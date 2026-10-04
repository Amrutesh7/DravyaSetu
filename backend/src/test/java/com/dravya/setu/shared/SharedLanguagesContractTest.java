package com.dravya.setu.shared;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SharedLanguagesContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportedLanguages_shouldBeValidAndContainBackendDefaultLanguage()
            throws IOException {

        Path path =
                Path.of("..", "shared/constants/supported-languages.json")
                        .normalize();

        assertTrue(
                Files.exists(path),
                "supported-languages.json must exist: " + path
        );

        JsonNode root =
                objectMapper.readTree(Files.readString(path));

        JsonNode languages = root.get("languages");

        assertNotNull(languages, "languages property must exist");
        assertTrue(languages.isArray(), "languages must be an array");
        assertFalse(languages.isEmpty(), "languages must not be empty");

        Set<String> languageCodes = new HashSet<>();

        languages.forEach(language -> {
            assertTrue(
                    language.isObject(),
                    "Each language entry must be an object"
            );

            JsonNode code = language.get("code");
            JsonNode name = language.get("name");

            assertNotNull(code, "Every language must have a code");
            assertNotNull(name, "Every language must have a name");

            assertFalse(
                    code.asText().isBlank(),
                    "Language code must not be blank"
            );

            assertFalse(
                    name.asText().isBlank(),
                    "Language name must not be blank"
            );

            assertTrue(
                    languageCodes.add(code.asText()),
                    "Duplicate language code: " + code.asText()
            );
        });

        // Spring Boot currently defaults chat requests to English.
        assertTrue(
                languageCodes.contains("en"),
                "Shared languages must contain backend default language 'en'"
        );
    }
}