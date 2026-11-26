
"""Multilingual translation service for DravyaSetu.

Uses the existing Gemini client and model fallback chain
from the RAG generator.

Flow:
    Regional language -> English -> RAG
    RAG answer -> Regional language
"""

import logging

from app.language.config import (
    get_language_name,
    normalize_language_code,
)
from app.rag.generator import generate_text

logger = logging.getLogger("dravyasetu.language")


TRANSLATION_SYSTEM_PROMPT = """
You are a professional multilingual translator for DravyaSetu,
an Ayurvedic medicinal plant knowledge application.

Your task is to translate text accurately without changing its meaning.

RULES:
1. Translate faithfully. Do not summarize, explain, or add information.
2. Preserve all botanical and scientific plant names exactly.
3. Preserve Sanskrit medicinal terms where appropriate.
4. Preserve plant IDs, source IDs, numbers, measurements and units.
5. Never invent medical claims or medicinal uses.
6. Use natural, grammatically correct language.
7. Use the requested target language and its native script.
8. Return only the translated text.
9. Do not add introductions or explanations.
"""


def translate_text(
    text: str,
    source_language: str,
    target_language: str,
) -> str:
    """Translate text using the existing Gemini integration."""

    source_code = normalize_language_code(source_language)
    target_code = normalize_language_code(target_language)

    if not text or not text.strip():
        return text

    # Avoid unnecessary Gemini calls.
    if source_code == target_code:
        return text

    source_name = get_language_name(source_code)
    target_name = get_language_name(target_code)

    user_prompt = f"""
Translate the following text.

Source language: {source_name}
Target language: {target_name}

TEXT TO TRANSLATE:
<text>
{text}
</text>

Return only the translation.
"""

    try:
        logger.info(
            "Translation started: %s -> %s",
            source_code,
            target_code,
        )

        translated = generate_text(
            system_prompt=TRANSLATION_SYSTEM_PROMPT,
            user_prompt=user_prompt,
        )

        if not translated or not translated.strip():
            raise RuntimeError("Empty translation response")

        result = translated.strip()

        logger.info(
            "Translation completed: %s -> %s",
            source_code,
            target_code,
        )

        return result

    except Exception as exc:
        logger.exception(
            "Translation failed: %s -> %s",
            source_code,
            target_code,
        )
        raise RuntimeError("TRANSLATION_UNAVAILABLE") from exc


def translate_to_english(
    text: str,
    input_language: str,
) -> str:
    """Translate the incoming user question into English."""

    return translate_text(
        text=text,
        source_language=input_language,
        target_language="en",
    )


def translate_from_english(
    text: str,
    response_language: str,
) -> str:
    """Translate the generated English answer into requested language."""

    return translate_text(
        text=text,
        source_language="en",
        target_language=response_language,
    )
