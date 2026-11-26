
"""Language configuration for DravyaSetu."""

SUPPORTED_LANGUAGES = {
    "en": "English",
    "kn": "Kannada",
    "hi": "Hindi",
    "ta": "Tamil",
    "te": "Telugu",
}

DEFAULT_LANGUAGE = "en"


def normalize_language_code(language: str | None) -> str:
    """Normalize and validate an API language code."""

    if language is None:
        return DEFAULT_LANGUAGE

    code = language.strip().lower()

    if code not in SUPPORTED_LANGUAGES:
        raise ValueError(f"Unsupported language: {code}")

    return code


def get_language_name(language: str) -> str:
    """Return the human-readable language name."""

    code = normalize_language_code(language)
    return SUPPORTED_LANGUAGES[code]
