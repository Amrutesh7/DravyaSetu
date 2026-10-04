package com.dravya.setu.auth;

import com.dravya.setu.security.JwtService;
import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import com.dravya.setu.common.InvalidCredentialsException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService
        );
    }

    @Test
    void register_shouldCreateUser_whenEmailDoesNotExist() {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("new@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashed-password");

        authService.register(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPasswordHash());
        assertEquals("USER", savedUser.getRole());

        verify(passwordEncoder)
                .encode("password123");
    }

    @Test
    void register_shouldRejectDuplicateEmail() {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("existing@example.com"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        verify(userRepository, never())
                .save(any(User.class));

        verify(passwordEncoder, never())
                .encode(any());
    }

    @Test
    void login_shouldReturnJwt_whenCredentialsAreCorrect() {

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "hashed-password"))
                .thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("test-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("test-jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());

        verify(passwordEncoder)
                .matches(
                        "password123",
                        "hashed-password"
                );

        verify(jwtService)
                .generateToken(user);
    }

    @Test
    void login_shouldRejectWrongPassword() {

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrong-password");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                "hashed-password"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(jwtService, never())
                .generateToken(any(User.class));
    }

    @Test
    void login_shouldRejectUnknownEmail() {

        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(passwordEncoder, never())
                .matches(any(), any());

        verify(jwtService, never())
                .generateToken(any(User.class));
    }
}