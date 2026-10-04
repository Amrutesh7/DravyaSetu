package com.dravya.setu.shared;

import com.dravya.setu.common.Roles;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SharedRolesContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sharedRoles_shouldMatchBackendRoles() throws IOException {
        Path path = Path.of("..", "shared/constants/roles.json").normalize();

        assertTrue(Files.exists(path), "roles.json must exist: " + path);

        JsonNode root = objectMapper.readTree(Files.readString(path));
        JsonNode roles = root.get("roles");

        assertNotNull(roles, "roles property must exist");
        assertTrue(roles.isArray(), "roles property must be an array");

        assertEquals(
                2,
                roles.size(),
                "DravyaSetu currently supports exactly two roles"
        );

        Set<String> sharedRoles = new HashSet<>();
        roles.forEach(role -> sharedRoles.add(role.asText()));

        Set<String> backendRoles = Set.of(
                Roles.USER,
                Roles.ADMIN
        );

        assertEquals(
                backendRoles,
                sharedRoles,
                "Shared roles must match backend Roles constants"
        );
    }
}