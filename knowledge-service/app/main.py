
from typing import Literal
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from app.rag.generator import answer as generate_answer

app = FastAPI(
    title="DravyaSetu Knowledge Service",
    description="RAG-based Ayurvedic medicinal plant knowledge API",
    version="1.0.0"
)


class ChatRequest(BaseModel):
    request_id: str
    session_id: str
    message: str = Field(min_length=1)
    input_type: Literal["text"] = "text"
    input_language: str | None = "en"
    response_language: str | None = "en"
    plant_id: str | None = None
    generate_audio: bool = False


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


@app.get("/health")
def health():
    return {"status": "healthy", "service": "DravyaSetu Knowledge Service"}


@app.post("/knowledge/chat", response_model=ChatResponse)
def chat(request: ChatRequest):

    if request.input_language not in (None, "en"):
        raise HTTPException(
            status_code=422,
            detail={
                "code": "UNSUPPORTED_LANGUAGE",
                "message": "Version 1.0 currently supports English text only."
            }
        )

    if request.response_language not in (None, "en"):
        raise HTTPException(
            status_code=422,
            detail={
                "code": "UNSUPPORTED_LANGUAGE",
                "message": "Version 1.0 currently supports English responses only."
            }
        )

    if request.generate_audio:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "VOICE_NOT_IMPLEMENTED",
                "message": "Audio generation is not available in Version 1.0."
            }
        )

    try:
        result = generate_answer(
            query=request.message,
            plant_id=request.plant_id
        )

        return ChatResponse(
            request_id=request.request_id,
            session_id=request.session_id,
            detected_language="en",
            transcript=None,
            answer=result["answer"],
            language="en",
            sources=result.get("sources", []),
            voice_output=None,
            warnings=[]
        )

    except RuntimeError as exc:
        if str(exc) == "LLM_UNAVAILABLE":
            raise HTTPException(
                status_code=503,
                detail={
                    "code": "LLM_UNAVAILABLE",
                    "message": "The answer service is temporarily unavailable."
                }
            )
        raise HTTPException(
            status_code=500,
            detail={
                "code": "KNOWLEDGE_SERVICE_ERROR",
                "message": "An internal knowledge service error occurred."
            }
        )


@app.get("/")
def root():
    return {
        "service": "DravyaSetu Knowledge Service",
        "version": "1.0.0",
        "docs": "/docs"
    }
