package com.dravya.setu.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {
    private int login(RateLimitFilter filter, String peer, String forwarded) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response.getStatus();
    }

    @Test
    void directCallerCannotRotateForwardedHeadersToBypassLimit() throws Exception {
        var filter = new RateLimitFilter(true);
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, login(filter, "198.51.100.1", "203.0.113." + i));
        }
        assertEquals(429, login(filter, "198.51.100.1", "203.0.113.99"));
    }

    @Test
    void trustedProxyDistinguishesClientsAndIgnoresSpoofedLeftmostHop() throws Exception {
        var filter = new RateLimitFilter(true, "172.30.77.2/32");
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, login(filter, "172.30.77.2", "203.0.113." + i + ", 198.51.100.1"));
        }
        assertEquals(429, login(filter, "172.30.77.2", "203.0.113.99, 198.51.100.1"));
        assertEquals(200, login(filter, "172.30.77.2", "198.51.100.2"));
    }

    @Test
    void malformedForwardedHeaderFallsBackToSocketPeer() throws Exception {
        var filter = new RateLimitFilter(true, "172.30.77.2/32");
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, login(filter, "172.30.77.2", "invalid-host-" + i));
        }
        assertEquals(429, login(filter, "172.30.77.2", "invalid-host-99"));
    }
}
