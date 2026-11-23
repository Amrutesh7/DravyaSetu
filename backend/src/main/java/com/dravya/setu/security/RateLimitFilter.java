package com.dravya.setu.security;

import com.dravya.setu.common.ErrorResponse;
import com.dravya.setu.common.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.List;

/** Single-node development rate limiter. Deploy Redis/API-gateway limiting for multi-instance production. */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {
    public static final String CLIENT_IP_ATTRIBUTE = "trustedClientIp";
    private final boolean enabled;
    private final List<IpAddressMatcher> trustedProxies;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(boolean enabled) {
        this(enabled, "");
    }

    @Autowired
    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.enabled = enabled;
        this.trustedProxies = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String clientIp = clientKey(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);
        Limit limit = limitFor(request.getRequestURI(), request.getMethod());
        if (!enabled || limit == null) { chain.doFilter(request, response); return; }
        String key = request.getRequestURI() + ":" + clientIp;
        Window window = windows.compute(key, (ignored, current) -> current == null || current.expired(limit.window())
                ? new Window(Instant.now(), 1) : current.increment());
        if (window.count() > limit.requests()) {
            response.setStatus(429); response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(limit.window().toSeconds()));
            Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("RATE_LIMITED",
                    "Too many requests. Please try again later.", requestId == null ? null : requestId.toString()));
            return;
        }
        chain.doFilter(request, response);
    }

    private Limit limitFor(String uri, String method) {
        if (!"POST".equalsIgnoreCase(method)) return null;
        if (uri.endsWith("/auth/register")) return new Limit(5, Duration.ofMinutes(15));
        if (uri.endsWith("/auth/login") || uri.endsWith("/auth/forgot-password")) return new Limit(5, Duration.ofMinutes(15));
        if (uri.endsWith("/auth/resend-verification")) return new Limit(5, Duration.ofMinutes(15));
        // Verification endpoints allow the documented five attempts plus retries and typos.
        if (uri.endsWith("/auth/verify-email") || uri.endsWith("/auth/verify-reset-otp")) return new Limit(20, Duration.ofMinutes(15));
        if (uri.endsWith("/auth/reset-password")) return new Limit(10, Duration.ofMinutes(15));
        if (uri.endsWith("/auth/logout")) return new Limit(30, Duration.ofMinutes(15));
        if (uri.endsWith("/identifications") || uri.endsWith("/chat/voice")) return new Limit(10, Duration.ofMinutes(1));
        if (uri.endsWith("/chat")) return new Limit(30, Duration.ofMinutes(1));
        return null;
    }

    private String clientKey(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        // Forwarded headers are evidence only when the socket peer is a trusted proxy.
        if (!isTrustedProxy(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 2048) return peer;
        String[] chain = forwarded.split(",", -1);
        // Walk from our proxy towards the client; stop before any untrusted hop's claims.
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate = chain[index].trim();
            if (!isAddress(candidate)) return peer;
            peer = candidate;
            if (!isTrustedProxy(peer)) return peer;
        }
        return peer;
    }

    private boolean isTrustedProxy(String address) {
        return isAddress(address) && trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }

    private boolean isAddress(String address) {
        // Restrict to numeric IP literals: no DNS lookups for untrusted header values.
        if (address == null || !address.matches("[0-9a-fA-F:.]+")) return false;
        try {
            return new IpAddressMatcher(address).matches(address);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record Limit(int requests, Duration window) { }
    private record Window(Instant startedAt, int count) {
        boolean expired(Duration duration) { return startedAt.plus(duration).isBefore(Instant.now()); }
        Window increment() { return new Window(startedAt, count + 1); }
    }
}
