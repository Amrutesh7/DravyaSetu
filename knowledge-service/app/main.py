import logging
import math
import time
from typing import Any, Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.rag.generator import (
    answer as generate_answer,
    summarize_conversation,
)
from app.language.config import normalize_language_code
from app.language.service import (
    translate_to_english,
    translate_from_english,
)


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

logger = logging.getLogger("dravyasetu.api")


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
def chat(request: ChatRequest):
    t0 = time.perf_counter()

    logger.info(
        "Request received: request_id=%s session_id=%s plant_id=%s",
        request.request_id,
        request.session_id,
        request.plant_id,
    )

    try:
        input_language = normalize_language_code(request.input_language)
        response_language = normalize_language_code(request.response_language)
    except ValueError as exc:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "UNSUPPORTED_LANGUAGE",
                "message": str(exc),
            },
        ) from exc

    if request.generate_audio:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "VOICE_NOT_IMPLEMENTED",
                "message": "Audio generation is not available yet.",
            },
        )

    try:
        english_query = translate_to_english(
            text=request.message,
            input_language=input_language,
        )

        conversation_context = request.conversation_context.model_dump()

        logger.info(
            "Conversation context: summary=%s recent_messages=%d",
            bool(conversation_context.get("summary")),
            len(conversation_context.get("recent_messages", [])),
        )

        result = generate_answer(
            query=english_query,
            plant_id=request.plant_id,
            conversation_context=conversation_context,
        )

        final_answer = translate_from_english(
            text=result["answer"],
            response_language=response_language,
        )

        response = ChatResponse(
            request_id=request.request_id,
            session_id=request.session_id,
            detected_language=input_language,
            transcript=None,
            answer=final_answer,
            language=response_language,
            sources=result.get("sources", []),
            voice_output=None,
            warnings=[],
        )

        logger.info(
            "Response returned: request_id=%s language=%s sources=%d duration=%.2fs",
            request.request_id,
            response.language,
            len(response.sources),
            time.perf_counter() - t0,
        )

        return response

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

    messages = [message.model_dump() for message in request.messages_to_summarize]

    message_ids = [message["message_id"] for message in messages]
    if len(message_ids) != len(set(message_ids)):
        raise HTTPException(
            status_code=422,
            detail={
                "code": "DUPLICATE_MESSAGE_IDS",
                "message": "Message IDs must be unique within a summarization batch.",
            },
        )

    try:
        updated_summary = summarize_conversation(
            existing_summary=request.existing_summary,
            messages_to_summarize=messages,
            summary_max_tokens=request.summary_max_tokens,
            language=language,
            metadata=request.metadata,
        )

        # This is an estimate, not an exact Gemini token count.
        estimated_token_count = max(1, math.ceil(len(updated_summary) / 4))
        last_processed_message_id = messages[-1]["message_id"]

        response = SummarizeResponse(
            request_id=request.request_id,
            session_id=request.session_id,
            summary=updated_summary,
            summarized_message_count=len(messages),
            summarized_through_message_id=last_processed_message_id,
            summary_token_count=estimated_token_count,
            warnings=["summary_token_count is an approximate character-based estimate."],
        )

        logger.info(
            "Summarization completed: request_id=%s session_id=%s messages=%d checkpoint=%s",
            request.request_id,
            request.session_id,
            len(messages),
            last_processed_message_id,
        )

        return response

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
