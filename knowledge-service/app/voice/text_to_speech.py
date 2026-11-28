
"""Text-to-speech service for DravyaSetu.

Uses Edge TTS to generate speech in the requested language.
Returns generated audio as MP3 bytes.
"""

import logging

import edge_tts

from app.language.config import normalize_language_code

logger = logging.getLogger("dravyasetu.voice")

# Default voices for supported languages.
LANGUAGE_VOICES = {
    "en": "en-IN-NeerjaNeural",
    "kn": "kn-IN-SapnaNeural",
    "hi": "hi-IN-SwaraNeural",
    "te": "te-IN-ShrutiNeural",
    "ta": "ta-IN-PallaviNeural",
}


async def generate_speech(
    text: str,
    language: str,
) -> bytes:
    """Generate speech and return MP3 audio bytes."""

    if not text or not text.strip():
        raise ValueError("Text cannot be empty")

    language_code = normalize_language_code(language)
    voice = LANGUAGE_VOICES[language_code]

    try:
        logger.info(
            "Speech generation started: language=%s voice=%s",
            language_code,
            voice,
        )

        communicate = edge_tts.Communicate(
            text=text,
            voice=voice,
        )

        audio = bytearray()

        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio.extend(chunk["data"])

        if not audio:
            raise RuntimeError("Empty audio response")

        logger.info(
            "Speech generation completed: language=%s size=%d bytes",
            language_code,
            len(audio),
        )

        return bytes(audio)

    except Exception as exc:
        logger.exception(
            "Speech generation failed: language=%s",
            language_code,
        )
        raise RuntimeError("TTS_UNAVAILABLE") from exc
