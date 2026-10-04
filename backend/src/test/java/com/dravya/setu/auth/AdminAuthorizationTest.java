package com.dravya.setu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(AdminAuthorizationTest.TestConfig.class)
class AdminAuthorizationTest {

    @Autowired
    private AdminController adminController;

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoint_shouldRejectNormalUser() {

        assertThrows(
                AccessDeniedException.class,
                () -> adminController.adminTest()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoint_shouldAllowAdmin() {

        String response = adminController.adminTest();

        assertEquals(
                "Admin access granted",
                response
        );
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        AdminController adminController() {
            return new AdminController();
        }
    }
}