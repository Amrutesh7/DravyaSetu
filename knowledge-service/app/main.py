import base64
import json
import logging
import math
import os
import tempfile
import time
from pathlib import Path
from typing import Any, Literal

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from app.language.config import normalize_language_code
from app.language.service import (
    translate_from_english,
    translate_to_english,
)
from app.rag.generator import (
    answer as generate_answer,
    summarize_conversation,
)
from app.voice.speech_to_text import transcribe_audio
from app.voice.text_to_speech import generate_speech

# --------------------------------------------------
# LOGGING
# --------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

logger = logging.getLogger("dravyasetu.api")


# --------------------------------------------------
# FASTAPI APPLICATION
# --------------------------------------------------

app = FastAPI(
    title="DravyaSetu Knowledge Service",
    description="Multilingual RAG-based Ayurvedic medicinal plant knowledge API",
    version="1.3.0",
)


# --------------------------------------------------
# REQUEST MODELS
# --------------------------------------------------

class RecentMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class ConversationContext(BaseModel):
    summary: str | None = None
    recent_messages: list[RecentMessage] = Field(default_factory=list)


class ChatRequest(BaseModel):
    request_id: str
    session_id: str
    message: str = Field(min_length=1)
    input_type: Literal["text"] = "text"
    input_language: str | None = "en"
    response_language: str | None = "en"
    plant_id: str | None = None
    conversation_context: ConversationContext = Field(
        default_factory=ConversationContext
    )
    generate_audio: bool = False


class MessageToSummarize(BaseModel):
    message_id: str = Field(min_length=1)
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)
    created_at: str = Field(min_length=1)


class SummarizeRequest(BaseModel):
    request_id: str = Field(min_length=1)
    session_id: str = Field(min_length=1)
    existing_summary: str | None = None
    messages_to_summarize: list[MessageToSummarize] = Field(min_length=1)
    summary_max_tokens: int = Field(default=500, ge=100, le=2000)
    language: str = "en"
    metadata: dict[str, Any] = Field(default_factory=dict)


# --------------------------------------------------
# RESPONSE MODELS
# --------------------------------------------------

class Source(BaseModel):
    source_id: str
    title: str
    reference: str


class ChatResponse(BaseModel):
    request_id: str
    session_id: str
    detected_language: str
    transcript: str | None
    answer: str
    language: str
    sources: list[Source]
    voice_output: dict | None
    warnings: list[str]


class SummarizeResponse(BaseModel):
    request_id: str
    session_id: str
    summary: str
    summarized_message_count: int
    summarized_through_message_id: str
    summary_token_count: int
    warnings: list[str]


# --------------------------------------------------
# HEALTH ENDPOINTS
# --------------------------------------------------

@app.get("/")
def root():
    return {
        "service": "DravyaSetu Knowledge Service",
        "version": "1.3.0",
        "docs": "/docs",
    }


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "service": "DravyaSetu Knowledge Service",
    }


# --------------------------------------------------
# NORMAL CHAT API
# Both old and new routes use the same handler.
# --------------------------------------------------

@app.post("/knowledge/chat", response_model=ChatResponse)
@app.post("/api/v1/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):

    t0 = time.perf_counter()

    logger.info(
        "Request received: request_id=%s session_id=%s plant_id=%s",
        request.request_id,
        request.session_id,
        request.plant_id,
    )

    # ----------------------------------------------
    # LANGUAGE VALIDATION
    # ----------------------------------------------

    try:
        input_language = normalize_language_code(
            request.input_language
        )

        response_language = normalize_language_code(
            request.response_language
        )

    except ValueError as exc:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "UNSUPPORTED_LANGUAGE",
                "message": str(exc),
            },
        ) from exc

    # ----------------------------------------------
    # CHAT PROCESSING
    # ----------------------------------------------

    try:

        # Step 1: Translate user question into English.
        english_query = translate_to_english(
            text=request.message,
            input_language=input_language,
        )

        # Step 2: Prepare conversation context.
        conversation_context = (
            request.conversation_context.model_dump()
        )

        logger.info(
            "Conversation context: summary=%s recent_messages=%d",
            bool(conversation_context.get("summary")),
            len(conversation_context.get("recent_messages", [])),
        )

        # Step 3: Generate answer using existing RAG.
        result = generate_answer(
            query=english_query,
            plant_id=request.plant_id,
            conversation_context=conversation_context,
        )

        # Step 4: Translate answer into requested language.
        final_answer = translate_from_english(
            text=result["answer"],
            response_language=response_language,
        )

        # ------------------------------------------
        # OPTIONAL TEXT-TO-SPEECH
        # ------------------------------------------

        voice_output = None
        warnings = []

        if request.generate_audio:

            try:
                audio_bytes = await generate_speech(
                    text=final_answer,
                    language=response_language,
                )

                voice_output = {
                    "audio_base64": base64.b64encode(
                        audio_bytes
                    ).decode("utf-8"),
                    "format": "mp3",
                    "language": response_language,
                }

            except RuntimeError:
                logger.exception(
                    "Voice generation failed: request_id=%s",
                    request.request_id,
                )

                warnings.append("VOICE_GENERATION_FAILED")

        # ------------------------------------------
        # BUILD CHAT RESPONSE
        # ------------------------------------------

        response = ChatResponse(
            request_id=request.request_id,
            session_id=request.session_id,
            detected_language=input_language,
            transcript=None,
            answer=final_answer,
            language=response_language,
            sources=result.get("sources", []),
            voice_output=voice_output,
            warnings=warnings,
        )

        logger.info(
            "Response returned: request_id=%s language=%s sources=%d duration=%.2fs",
            request.request_id,
            response.language,
            len(response.sources),
            time.perf_counter() - t0,
        )

        return response

    # ----------------------------------------------
    # ERROR HANDLING
    # ----------------------------------------------

    except RuntimeError as exc:

        logger.exception(
            "RuntimeError in chat (request_id=%s)",
            request.request_id,
        )

        if str(exc) == "TRANSLATION_UNAVAILABLE":
            raise HTTPException(
                status_code=503,
                detail={
                    "code": "TRANSLATION_UNAVAILABLE",
                    "message": "The translation service is temporarily unavailable.",
                },
            ) from exc

        if str(exc) == "LLM_UNAVAILABLE":
            raise HTTPException(
                status_code=503,
                detail={
                    "code": "LLM_UNAVAILABLE",
                    "message": "The answer service is temporarily unavailable.",
                },
            ) from exc

        raise HTTPException(
            status_code=500,
            detail={
                "code": "KNOWLEDGE_SERVICE_ERROR",
                "message": "An internal knowledge service error occurred.",
            },
        ) from exc

    except Exception as exc:

        logger.exception(
            "Unhandled exception in chat (request_id=%s)",
            request.request_id,
        )

        raise HTTPException(
            status_code=500,
            detail={
                "code": "KNOWLEDGE_SERVICE_ERROR",
                "message": "An internal knowledge service error occurred.",
            },
        ) from exc


# --------------------------------------------------
# AUDIO CHAT API
# --------------------------------------------------

SUPPORTED_AUDIO_EXTENSIONS = {
    ".wav",
    ".mp3",
    ".m4a",
    ".mp4",
    ".mpeg",
    ".mpga",
    ".ogg",
    ".webm",
    ".flac",
}

MAX_AUDIO_SIZE = 25 * 1024 * 1024  # 25 MB

# Groq Whisper may return either a language name or a language code.
LANGUAGE_NAME_TO_CODE = {
    "english": "en",
    "kannada": "kn",
    "hindi": "hi",
    "telugu": "te",
    "tamil": "ta",
    "en": "en",
    "kn": "kn",
    "hi": "hi",
    "te": "te",
    "ta": "ta",
}


@app.post("/api/v1/chat/audio", response_model=ChatResponse)
async def chat_with_audio(
    audio: UploadFile = File(...),
    request_id: str = Form(...),
    session_id: str = Form(...),
    input_language: str | None = Form(default=None),
    response_language: str = Form(default="en"),
    plant_id: str | None = Form(default=None),
    conversation_context: str = Form(default="{}"),
    generate_audio: bool = Form(default=True),
):
    """Transcribe uploaded speech and process it through the existing chat flow."""

    temp_path = None

    try:
        # 1. Validate the uploaded file extension.
        extension = Path(audio.filename or "").suffix.lower()

        if extension not in SUPPORTED_AUDIO_EXTENSIONS:
            raise HTTPException(
                status_code=415,
                detail={
                    "code": "UNSUPPORTED_AUDIO_FORMAT",
                    "message": "Unsupported audio format.",
                },
            )

        # 2. Read at most 25 MB + 1 byte to enforce the size limit.
        audio_data = await audio.read(MAX_AUDIO_SIZE + 1)

        if not audio_data:
            raise HTTPException(
                status_code=400,
                detail={
                    "code": "EMPTY_AUDIO",
                    "message": "The uploaded audio file is empty.",
                },
            )

        if len(audio_data) > MAX_AUDIO_SIZE:
            raise HTTPException(
                status_code=413,
                detail={
                    "code": "AUDIO_TOO_LARGE",
                    "message": "Audio file must not exceed 25 MB.",
                },
            )

        # 3. Validate the selected input language, if supplied.
        try:
            selected_language = (
                normalize_language_code(input_language)
                if input_language and input_language.strip()
                else None
            )
        except ValueError as exc:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "UNSUPPORTED_LANGUAGE",
                    "message": str(exc),
                },
            ) from exc

        # 4. Parse and validate conversation context JSON.
        try:
            context_data = json.loads(conversation_context)
            context = ConversationContext.model_validate(context_data)
        except (json.JSONDecodeError, ValueError, TypeError) as exc:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "INVALID_CONVERSATION_CONTEXT",
                    "message": "Conversation context must be a valid JSON object.",
                },
            ) from exc

        # 5. Save audio to a temporary file for the existing Whisper service.
        with tempfile.NamedTemporaryFile(
            suffix=extension,
            delete=False,
        ) as temp_file:
            temp_file.write(audio_data)
            temp_path = temp_file.name

        # 6. Run synchronous Groq transcription outside the event loop.
        try:
            transcription = await run_in_threadpool(
                transcribe_audio,
                temp_path,
                selected_language,
            )
        except RuntimeError as exc:
            if str(exc) == "ASR_UNAVAILABLE":
                raise HTTPException(
                    status_code=503,
                    detail={
                        "code": "ASR_UNAVAILABLE",
                        "message": "Speech recognition is temporarily unavailable.",
                    },
                ) from exc
            raise

        transcript = (transcription.get("text") or "").strip()

        if not transcript:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "EMPTY_TRANSCRIPTION",
                    "message": "No speech could be detected in the audio.",
                },
            )

        # 7. Resolve the language detected by Whisper.
        detected_raw = (
            selected_language
            or transcription.get("language")
            or ""
        )
        detected_raw = detected_raw.strip().lower()
        detected_language = LANGUAGE_NAME_TO_CODE.get(detected_raw)

        if not detected_language:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "UNSUPPORTED_DETECTED_LANGUAGE",
                    "message": (
                        "The detected language is not supported. "
                        "Please select the spoken language and try again."
                    ),
                },
            )

        logger.info(
            "Audio transcribed: request_id=%s session_id=%s language=%s",
            request_id,
            session_id,
            detected_language,
        )

        # 8. Reuse the existing text-chat pipeline without changing its schema.
        chat_request = ChatRequest(
            request_id=request_id,
            session_id=session_id,
            message=transcript,
            input_language=detected_language,
            response_language=response_language,
            plant_id=plant_id,
            conversation_context=context,
            generate_audio=generate_audio,
        )

        response = await chat(chat_request)

        # 9. Return the same ChatResponse, now including the speech transcript.
        return response.model_copy(
            update={
                "transcript": transcript,
                "detected_language": detected_language,
            }
        )

    finally:
        # Always remove the temporary audio file, including on errors.
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                logger.warning(
                    "Could not delete temporary audio file: %s",
                    temp_path,
                    exc_info=True,
                )

        await audio.close()


# --------------------------------------------------
# CONVERSATION MEMORY SUMMARIZATION API
# --------------------------------------------------

@app.post(
    "/api/v1/chat/memory/summarize",
    response_model=SummarizeResponse,
)
def summarize_chat(request: SummarizeRequest):

    logger.info(
        "Summarization request received: request_id=%s session_id=%s messages=%d",
        request.request_id,
        request.session_id,
        len(request.messages_to_summarize),
    )

    # ----------------------------------------------
    # LANGUAGE VALIDATION
    # ----------------------------------------------

    try:
        language = normalize_language_code(request.language)

    except ValueError as exc:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "UNSUPPORTED_LANGUAGE",
                "message": str(exc),
            },
        ) from exc

    # ----------------------------------------------
    # MESSAGE VALIDATION
    # ----------------------------------------------

    messages = [
        message.model_dump()
        for message in request.messages_to_summarize
    ]

    message_ids = [
        message["message_id"]
        for message in messages
    ]

    if len(message_ids) != len(set(message_ids)):
        raise HTTPException(
            status_code=422,
            detail={
                "code": "DUPLICATE_MESSAGE_IDS",
                "message": "Message IDs must be unique within a summarization batch.",
            },
        )

    # ----------------------------------------------
    # GENERATE SUMMARY
    # ----------------------------------------------

    try:

        updated_summary = summarize_conversation(
            existing_summary=request.existing_summary,
            messages_to_summarize=messages,
            summary_max_tokens=request.summary_max_tokens,
            language=language,
            metadata=request.metadata,
        )

        # This is an estimate, not an exact Gemini token count.
        estimated_token_count = max(
            1,
            math.ceil(len(updated_summary) / 4),
        )

        last_processed_message_id = messages[-1]["message_id"]

        response = SummarizeResponse(
            request_id=request.request_id,
            session_id=request.session_id,
            summary=updated_summary,
            summarized_message_count=len(messages),
            summarized_through_message_id=last_processed_message_id,
            summary_token_count=estimated_token_count,
            warnings=[
                "summary_token_count is an approximate character-based estimate."
            ],
        )

        logger.info(
            "Summarization completed: request_id=%s session_id=%s messages=%d checkpoint=%s",
            request.request_id,
            request.session_id,
            len(messages),
            last_processed_message_id,
        )

        return response

    # ----------------------------------------------
    # SUMMARIZATION ERROR HANDLING
    # ----------------------------------------------

    except RuntimeError as exc:

        logger.exception(
            "Summarization failed: request_id=%s",
            request.request_id,
        )

        if str(exc) == "LLM_UNAVAILABLE":
            raise HTTPException(
                status_code=503,
                detail={
                    "code": "LLM_UNAVAILABLE",
                    "message": "The summarization service is temporarily unavailable.",
                },
            ) from exc

        if str(exc) == "Set GEMINI_API_KEY in your environment.":
            raise HTTPException(
                status_code=503,
                detail={
                    "code": "LLM_CONFIGURATION_ERROR",
                    "message": "The Gemini API key is not configured.",
                },
            ) from exc

        raise HTTPException(
            status_code=500,
            detail={
                "code": "SUMMARIZATION_ERROR",
                "message": "Conversation summarization failed.",
            },
        ) from exc

    except Exception as exc:

        logger.exception(
            "Unexpected summarization error: request_id=%s",
            request.request_id,
        )

        raise HTTPException(
            status_code=500,
            detail={
                "code": "SUMMARIZATION_ERROR",
                "message": "Conversation summarization failed.",
            },
        ) from exc
