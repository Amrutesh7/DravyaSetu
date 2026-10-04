package com.dravya.setu.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        AuthController authController =
                new AuthController(authService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .build();
    }

    @Test
    void register_shouldReturn201() throws Exception {

        doNothing()
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(
                        post("/auth/register")
                                .contentType("application/json")
                                .content("""
                                {
                                  "email": "new@example.com",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isCreated());
    }

    @Test
    void login_shouldReturn200AndToken() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(
                        new AuthResponse(
                                "test-jwt-token",
                                "Bearer"
                        )
                );

        mockMvc.perform(
                        post("/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token")
                        .value("test-jwt-token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"));
    }
}