package com.dravya.setu.security;

import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Optional<JwtService.JwtClaims> parsed = jwtService.parse(token);

            if (parsed.isPresent()) {
                User user = resolveUsableUser(parsed.get());

                if (user != null) {
                    authenticate(user);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } else {
                SecurityContextHolder.clearContext();
            }
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns the account behind a token, or {@code null} when the token must not be honoured.
     * A token is usable only while it is not revoked, its account still exists and is verified,
     * and it was issued after the account's most recent password change.
     */
    private User resolveUsableUser(JwtService.JwtClaims claims) {

        if (claims.jti() != null && revokedTokenRepository.existsById(claims.jti())) {
            return null;
        }

        User user = userRepository.findByEmail(claims.email()).orElse(null);

        if (user == null
                || !user.isEmailVerified()
                || user.getTokenVersion() != claims.tokenVersion()) {
            return null;
        }

        return user;
    }

    private void authenticate(User user) {        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole())
        );

        var authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
