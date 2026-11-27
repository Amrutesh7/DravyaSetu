
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exception_handlers import http_exception_handler
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api.routes import MAX_REQUEST_BODY_BYTES, pipeline, router
from app.api.upload_limits import (
    REQUEST_BODY_TOO_LARGE_DETAIL,
    AnalyzeRequestBodyLimitMiddleware,
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app):
    try:
        yield
    finally:
        try:
            pipeline.gradcam.close()
        except Exception:
            logger.exception("Failed to remove Grad-CAM hooks at shutdown.")


app = FastAPI(
    title="DravyaSetu AI Service",
    version="1.0.0",
    lifespan=lifespan,
)
app.add_middleware(
    AnalyzeRequestBodyLimitMiddleware,
    max_bytes=MAX_REQUEST_BODY_BYTES,
)

PROJECT_ROOT = Path(__file__).resolve().parents[1]
GRADCAM_DIR = PROJECT_ROOT / "artifacts" / "gradcam"
GRADCAM_DIR.mkdir(parents=True, exist_ok=True)

app.mount(
    "/artifacts/gradcam",
    StaticFiles(directory=str(GRADCAM_DIR)),
    name="gradcam",
)

app.include_router(router)


@app.exception_handler(StarletteHTTPException)
async def handle_http_exception(
    request: Request,
    exc: StarletteHTTPException,
):
    if (
        exc.status_code == 400
        and exc.detail == REQUEST_BODY_TOO_LARGE_DETAIL
    ):
        return JSONResponse(
            status_code=413,
            content={"detail": REQUEST_BODY_TOO_LARGE_DETAIL},
        )
    return await http_exception_handler(request, exc)


@app.get("/")
def root():
    return {
        "service": "DravyaSetu AI Service",
        "status": "running",
    }


@app.get("/health")
def health():
    return {"status": "healthy"}