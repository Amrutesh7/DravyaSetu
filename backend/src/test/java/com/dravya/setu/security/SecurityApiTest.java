package com.dravya.setu.security;

import com.dravya.setu.auth.AdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(SecurityApiTest.TestConfig.class)
class SecurityApiTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(adminController)
                .addFilter(securityFilterChain)
                .build();
    }

    @Test
    void adminEndpoint_shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/admin/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_shouldRejectNormalUser() throws Exception {
        mockMvc.perform(
                        get("/admin/test")
                                .with(user("testuser").roles("USER"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_shouldAllowAdmin() throws Exception {
        mockMvc.perform(
                        get("/admin/test")
                                .with(user("admin").roles("ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("Admin access granted"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        AdminController adminController() {
            return new AdminController();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http)
                throws Exception {

            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/auth/register",
                                    "/auth/login"
                            ).permitAll()
                            .anyRequest().authenticated()
                    );

            return http.build();
        }
    }
}