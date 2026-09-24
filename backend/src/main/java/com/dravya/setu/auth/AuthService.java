package com.dravya.setu.auth;

import com.dravya.setu.security.JwtService;
import com.dravya.setu.users.User;
import com.dravya.setu.common.InvalidCredentialsException;
import com.dravya.setu.auth.AuthResponse;
import com.dravya.setu.users.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User();

        user.setEmail(request.getEmail());
        user.setPasswordHash(hashedPassword);
        user.setRole("USER");

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, "Bearer");
    }
}