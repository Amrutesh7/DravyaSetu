
from __future__ import annotations

import io
import uuid

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image

from app.inference.confidence import ConfidenceCalculator
from app.inference.predictor import PlantPredictor, build_predictor
from app.inference.unknown_detector import UnknownDetector
from app.models.embeddings import EmbeddingExtractor
from app.multi_image.evidence_fusion import EvidenceFusion
from app.pipeline import DravyaSetuPipeline, PipelineConfig
from app.preprocessing.quality_checker import ImageQualityChecker
from app.schemas.response import AIResponse
from app.schemas.response_builder import ResponseBuilder


router = APIRouter(prefix="/ai", tags=["AI"])


def build_ai_pipeline() -> DravyaSetuPipeline:
    """Build the AI pipeline using the trained DravyaSetu model."""

    # Load the trained checkpoint and its 34-class mapping.
    predictor = build_predictor(
        num_classes=34,
        pretrained=False,
        device="cpu",
    )

    # Reuse the predictor's confidence calculator.
    confidence_calculator = predictor.confidence_calculator

    # Initialize the remaining pipeline components.
    quality_checker = ImageQualityChecker()
    unknown_detector = UnknownDetector()
    embedding_extractor = EmbeddingExtractor(predictor.model)
    evidence_fusion = EvidenceFusion()
    response_builder = ResponseBuilder()

    config = PipelineConfig(top_k=5)

    return DravyaSetuPipeline(
        predictor=predictor,
        quality_checker=quality_checker,
        confidence_calculator=confidence_calculator,
        unknown_detector=unknown_detector,
        embedding_extractor=embedding_extractor,
        evidence_fusion=evidence_fusion,
        response_builder=response_builder,
        config=config,
    )


# Initialize the pipeline when this module is imported.
pipeline = build_ai_pipeline()


async def read_uploaded_image(upload: UploadFile) -> Image.Image:
    """Read and validate an uploaded image."""

    if not upload.content_type:
        raise HTTPException(
            status_code=400,
            detail="Image content type is missing.",
        )

    if not upload.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="Uploaded file must be an image.",
        )

    try:
        image_bytes = await upload.read()

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded image is empty.",
            )

        image = Image.open(io.BytesIO(image_bytes))
        image.load()

        return image.convert("RGB")

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=400,
            detail="Invalid image file. Please upload a valid image.",
        )


@router.post("/analyze", response_model=AIResponse)
async def analyze_image(image: UploadFile = File(...)):
    """Analyze an uploaded plant image."""

    request_id = str(uuid.uuid4())

    plant_image = await read_uploaded_image(image)

    try:
        response = pipeline.analyze_image(
            image=plant_image,
            image_id=image.filename or "image_0",
            request_id=request_id,
            model_version="efficientnetv2-s-dravyasetu-v2",
        )

        validated_response = AIResponse.model_validate(response)

        return validated_response

    except HTTPException:
        raise

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"AI pipeline execution failed: {exc}",
        ) from exc