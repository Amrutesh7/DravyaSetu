package com.dravya.setu.security;

import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "header.payload.signature";

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private RevokedTokenRepository revokedTokenRepository;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(
                jwtService, userRepository, revokedTokenRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAVerifiedAccountWithAMatchingTokenVersion() throws Exception {

        User user = verifiedUser(0);
        UUID jti = UUID.randomUUID();

        givenToken(user, jti);
        when(revokedTokenRepository.existsById(jti)).thenReturn(false);

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertEquals(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
    }

    @Test
    void refusesAnUnverifiedAccount() throws Exception {

        User user = verifiedUser(0);
        user.setEmailVerified(false);

        givenToken(user, UUID.randomUUID());

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void refusesATokenIssuedBeforeAPasswordChange() throws Exception {

        User user = verifiedUser(2);

        givenToken(user, UUID.randomUUID(), 1);

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void refusesARevokedToken() throws Exception {

        User user = verifiedUser(0);
        UUID jti = UUID.randomUUID();

        givenToken(user, jti);
        when(revokedTokenRepository.existsById(jti)).thenReturn(true);

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void refusesADeletedAccount() throws Exception {

        UUID jti = UUID.randomUUID();
        when(jwtService.parse(TOKEN)).thenReturn(Optional.of(claims(jti, 0)));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ignoresRequestsWithoutABearerToken() throws Exception {

        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, userRepository, revokedTokenRepository);
        assertNotNull(chain.getRequest(), "the filter chain must still run");
    }

    @Test
    void ignoresAnInvalidTokenAndContinuesTheChain() throws Exception {

        when(jwtService.parse(TOKEN)).thenReturn(Optional.empty());

        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWith(TOKEN), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    // ---------------------------------------------------------------- helpers

    private void givenToken(User user, UUID jti) {
        givenToken(user, jti, user.getTokenVersion());
    }

    private void givenToken(User user, UUID jti, int tokenVersion) {
        when(jwtService.parse(TOKEN)).thenReturn(Optional.of(claims(jti, tokenVersion)));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private static JwtService.JwtClaims claims(UUID jti, int tokenVersion) {
        return new JwtService.JwtClaims(
                "user@example.com", 1L, "USER", tokenVersion,
                jti, Instant.now().plusSeconds(600));
    }

    private static User verifiedUser(int tokenVersion) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        user.setRole("USER");
        user.setEmailVerified(true);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private static MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
