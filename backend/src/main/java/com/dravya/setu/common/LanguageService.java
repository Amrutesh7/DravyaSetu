package com.dravya.setu.common;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LanguageService {
    private static final List<Map<String, String>> SUPPORTED_LANGUAGES = List.of(
            Map.of("code", "en", "name", "English"),
            Map.of("code", "hi", "name", "Hindi"),
            Map.of("code", "kn", "name", "Kannada"),
            Map.of("code", "ta", "name", "Tamil"));
    private static final Set<String> SUPPORTED_CODES = Set.of("en", "hi", "kn", "ta");

    public List<Map<String, String>> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public void requireSupported(String language) {
        if (!SUPPORTED_CODES.contains(language)) {
            throw ClientErrorException.badRequest("Unsupported language: " + language);
        }
    }
}
