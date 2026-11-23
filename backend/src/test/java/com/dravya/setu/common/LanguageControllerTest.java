package com.dravya.setu.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageControllerTest {
    @Test
    void returnsUniqueLanguagesMatchingTheSharedConstant() {
        Map<String, Object> body = new LanguageController(new LanguageService()).languages();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> languages = (List<Map<String, String>>) body.get("languages");

        assertEquals(Set.of("en", "hi", "kn", "ta"),
                languages.stream().map(language -> language.get("code")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(languages.size(), languages.stream().map(language -> language.get("code")).distinct().count());
        assertTrue(languages.stream().allMatch(language -> !language.get("name").isBlank()));
    }
}
