
"""Speech-to-text service for DravyaSetu.

Uses Groq Whisper to convert user speech into text.
Supports English, Kannada, Hindi, Telugu and Tamil.
"""

import logging
import os

from groq import Groq

from app.language.config import normalize_language_code

logger = logging.getLogger("dravyasetu.voice")


def transcribe_audio(
    audio_path: str,
    language: str | None = None,
) -> dict:
    """Convert audio into text using Groq Whisper."""

    api_key = os.getenv("GROQ_API_KEY")

    if not api_key:
        raise RuntimeError("GROQ_API_KEY is not configured")

    try:
        client = Groq(api_key=api_key)

        # Use selected language or allow automatic detection.
        language_code = (
            normalize_language_code(language)
            if language is not None
            else None
        )

        with open(audio_path, "rb") as audio_file:
            audio_data = audio_file.read()

        options = {
            "file": (os.path.basename(audio_path), audio_data),
            "model": "whisper-large-v3",
            "response_format": "verbose_json",
        }

        if language_code:
            options["language"] = language_code

        transcription = client.audio.transcriptions.create(**options)

        result = {
            "text": transcription.text,
            "language": getattr(transcription, "language", None),
        }

        logger.info(
            "Transcription completed: language=%s",
            result["language"],
        )

        return result

    except Exception as exc:
        logger.exception("Audio transcription failed")
        raise RuntimeError("ASR_UNAVAILABLE") from exc
