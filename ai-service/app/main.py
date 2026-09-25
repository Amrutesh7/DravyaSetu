from fastapi import FastAPI
from app.api.routes import router

app = FastAPI(
    title="DravyaSetu AI Service",
    description="AI and Computer Vision service for medicinal plant identification",
    version="1.0.0"
)

app.include_router(router, prefix="/ai")


@app.get("/")
def root():
    return {
        "service": "DravyaSetu AI Service",
        "status": "running",
        "version": "1.0.0"
    }