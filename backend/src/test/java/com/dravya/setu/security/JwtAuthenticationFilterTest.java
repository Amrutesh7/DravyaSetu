package com.dravya.setu.security;

import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtService,
                userRepository
        );

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldContinueWithoutAuthentication_whenHeaderIsMissing()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain)
                .doFilter(request, response);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void doFilter_shouldContinueWithoutAuthentication_whenTokenIsInvalid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer invalid-token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.isTokenValid("invalid-token"))
                .thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain)
                .doFilter(request, response);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService)
                .isTokenValid("invalid-token");

        verify(jwtService, never())
                .extractEmail(anyString());

        verifyNoInteractions(userRepository);
    }

    @Test
    void doFilter_shouldAuthenticateUser_whenTokenIsValid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer valid-token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        User user = new User();
        user.setEmail("test@example.com");
        user.setRole("USER");

        when(jwtService.isTokenValid("valid-token"))
                .thenReturn(true);

        when(jwtService.extractEmail("valid-token"))
                .thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        verify(filterChain)
                .doFilter(request, response);

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);
        assertSame(user, authentication.getPrincipal());
        assertEquals(
                "ROLE_USER",
                authentication
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );

        verify(jwtService)
                .isTokenValid("valid-token");

        verify(jwtService)
                .extractEmail("valid-token");

        verify(userRepository)
                .findByEmail("test@example.com");
    }

    @Test
    void doFilter_shouldSetAdminRole_whenUserIsAdmin()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer admin-token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        User user = new User();
        user.setEmail("admin@example.com");
        user.setRole("ADMIN");

        when(jwtService.isTokenValid("admin-token"))
                .thenReturn(true);

        when(jwtService.extractEmail("admin-token"))
                .thenReturn("admin@example.com");

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);

        assertEquals(
                "ROLE_ADMIN",
                authentication
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );

        assertSame(user, authentication.getPrincipal());

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void doFilter_shouldRemainUnauthenticated_whenUserDoesNotExist()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer valid-token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.isTokenValid("valid-token"))
                .thenReturn(true);

        when(jwtService.extractEmail("valid-token"))
                .thenReturn("unknown@example.com");

        when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        verify(filterChain)
                .doFilter(request, response);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }
}