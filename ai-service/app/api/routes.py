import uuid

from fastapi import APIRouter, File, UploadFile, HTTPException
from PIL import Image

from app.preprocessing.quality_checker import check_image_quality
from app.preprocessing.image_preprocessor import preprocess_image
from app.inference.predictor import PlantPredictor
from app.inference.confidence import classify_confidence
from app.inference.unknown_detector import get_identification_status
from app.schemas.response import AIResponse


router = APIRouter(
    prefix="/ai",
    tags=["AI"]
)


# Temporary predictor.
# Later this will load the trained DravyaSetu model.
predictor = PlantPredictor()


@router.post(
    "/analyze",
    response_model=AIResponse
)
async def analyze_image(
    image: UploadFile = File(...)
):
    request_id = str(uuid.uuid4())

    # --------------------------------------------------
    # 1. Validate uploaded file
    # --------------------------------------------------

    if not image.content_type:
        raise HTTPException(
            status_code=400,
            detail="Image content type is missing."
        )

    if not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="Uploaded file must be an image."
        )

    # --------------------------------------------------
    # 2. Read image
    # --------------------------------------------------

    try:
        image_bytes = await image.read()

        plant_image = Image.open(
            __import__("io").BytesIO(image_bytes)
        )

        plant_image.load()

    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid image file: {exc}"
        )

    # --------------------------------------------------
    # 3. Image quality check
    # --------------------------------------------------

    quality = check_image_quality(
        plant_image
    )

    # --------------------------------------------------
    # 4. Reject unusable image
    # --------------------------------------------------

    if not quality["acceptable"]:
        return AIResponse(
            request_id=request_id,
            model_version="efficientnetv2-s-dravyasetu-v1",
            image_quality=quality,
            identification={
                "status": "UNKNOWN",
                "plant_id": None,
                "confidence": 0.0
            },
            alternatives=[],
            similar_species=[],
            explanation={
                "method": "GRAD_CAM",
                "heatmap_url": None
            },
            multi_image={
                "used": False,
                "image_count": 1
            },
            warnings=quality["issues"]
        )

    # --------------------------------------------------
    # 5. Preprocess image
    # --------------------------------------------------

    input_tensor = preprocess_image(
        plant_image
    )

    # --------------------------------------------------
    # 6. Run plant classifier
    # --------------------------------------------------

    predictions = predictor.predict(
        input_tensor,
        top_k=3
    )

    if not predictions:
        raise HTTPException(
            status_code=500,
            detail="Plant prediction failed."
        )

    best_prediction = predictions[0]

    confidence = best_prediction[
        "confidence"
    ]

    # --------------------------------------------------
    # 7. Determine known / unknown
    # --------------------------------------------------

    status = get_identification_status(
        confidence
    )

    # --------------------------------------------------
    # 8. Build alternatives
    # --------------------------------------------------

    alternatives = []

    for prediction in predictions[1:]:
        alternatives.append({
            "plant_id": prediction["plant_id"],
            "common_name": prediction["common_name"],
            "scientific_name": prediction["scientific_name"],
            "confidence": prediction["confidence"]
        })

    # --------------------------------------------------
    # 9. Warnings
    # --------------------------------------------------

    warnings = []

    confidence_level = classify_confidence(
        confidence
    )

    if confidence_level == "LOW":
        warnings.append(
            "Prediction confidence is low."
        )

    # --------------------------------------------------
    # 10. Final response
    # --------------------------------------------------

    return AIResponse(
        request_id=request_id,
        model_version="efficientnetv2-s-dravyasetu-v1",
        image_quality=quality,
        identification={
            "status": status,
            "plant_id": (
                best_prediction["plant_id"]
                if status == "KNOWN"
                else None
            ),
            "confidence": confidence
        },
        alternatives=alternatives,
        similar_species=[],
        explanation={
            "method": "GRAD_CAM",
            "heatmap_url": None
        },
        multi_image={
            "used": False,
            "image_count": 1
        },
        warnings=warnings
    )