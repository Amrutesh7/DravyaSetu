
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.api.routes import router

app = FastAPI(
    title="DravyaSetu AI Service",
    version="1.0.0",
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


@app.get("/")
def root():
    return {
        "service": "DravyaSetu AI Service",
        "status": "running",
    }


@app.get("/health")
def health():
    return {"status": "healthy"}