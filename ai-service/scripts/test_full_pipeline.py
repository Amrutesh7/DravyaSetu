
"""Smoke test for the complete DravyaSetu AI pipeline."""

import json
import sys
import traceback
from pathlib import Path

from PIL import Image

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

# Update this to a real image in your dataset.
IMAGE_PATH = Path(
    r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike"
    r"\arya_shah_1dd91168dbee.jpg"
)

print("=" * 60)
print("DRAVYASETU FULL PIPELINE TEST")
print("=" * 60)

try:
    # Import application components.
    from app.inference.predictor import build_predictor
    from app.preprocessing.quality_checker import ImageQualityChecker
    from app.inference.confidence import ConfidenceCalculator
    from app.inference.unknown_detector import UnknownDetector
    from app.models.embeddings import EmbeddingExtractor
    from app.multi_image.evidence_fusion import EvidenceFusion
    from app.schemas.response_builder import ResponseBuilder
    from app.pipeline import DravyaSetuPipeline, PipelineConfig

    print("\n[1/7] Imports: PASS")

    if not IMAGE_PATH.is_file():
        raise FileNotFoundError(
            f"Image not found: {IMAGE_PATH}\n"
            "Change IMAGE_PATH to an existing dataset image."
        )

    # Load the existing trained checkpoint. Do not train a new model.
    predictor = build_predictor(
        num_classes=34,
        pretrained=False,
        device="cpu",
    )
    print("[2/7] Model checkpoint: PASS")

    quality_checker = ImageQualityChecker()
    confidence_calculator = ConfidenceCalculator()
    unknown_detector = UnknownDetector()
    embedding_extractor = EmbeddingExtractor(predictor)
    evidence_fusion = EvidenceFusion()
    response_builder = ResponseBuilder()

    pipeline = DravyaSetuPipeline(
        predictor=predictor,
        quality_checker=quality_checker,
        confidence_calculator=confidence_calculator,
        unknown_detector=unknown_detector,
        embedding_extractor=embedding_extractor,
        evidence_fusion=evidence_fusion,
        response_builder=response_builder,
        config=PipelineConfig(top_k=5),
    )
    print("[3/7] Pipeline initialization: PASS")

    with Image.open(IMAGE_PATH) as opened:
        image = opened.convert("RGB")

    quality = quality_checker.check(image)
    print("\n[4/7] IMAGE QUALITY")
    print("Acceptable:", quality.acceptable)
    print("Score:", round(quality.score, 4))
    print("Dimensions:", quality.width, "x", quality.height)
    print("Issues:", quality.issues)

    response = pipeline.analyze_image(
        image=image,
        image_id=IMAGE_PATH.stem,
        request_id="pipeline-smoke-test",
        model_version="dravya-v2",
    )
    print("\n[5/7] PIPELINE RESPONSE: PASS")

    # Support either Pydantic v2 models or dictionary responses.
    if hasattr(response, "model_dump"):
        response_dict = response.model_dump(mode="json")
    elif isinstance(response, dict):
        response_dict = response
    else:
        raise TypeError(
            f"Unexpected response type: {type(response)}"
        )

    output_path = (
        PROJECT_ROOT / "artifacts" / "pipeline_test_response.json"
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(response_dict, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print("\n[6/7] RESPONSE CONTENT")
    print(json.dumps(response_dict, indent=2, ensure_ascii=False))
    print("\nResponse saved to:", output_path)

    explanation = response_dict.get("explanation") or {}

    print("\n[7/7] GRAD-CAM URL CHECK")
    if isinstance(explanation, dict):
        print("Method:", explanation.get("method"))
        print("Heatmap URL:", explanation.get("heatmap_url"))
        print("Overlay URL:", explanation.get("overlay_url"))
        print(
            "Original URL:",
            explanation.get("original_image_url"),
        )

        if explanation.get("heatmap_url"):
            print("Heatmap URL: PRESENT")
        else:
            print(
                "Heatmap URL: MISSING "
                "(check response_builder.py and response.py)"
            )
    else:
        print("Explanation is not a dictionary:", explanation)

    print("\nGrad-CAM files:")
    gradcam_dir = PROJECT_ROOT / "artifacts" / "gradcam"
    if gradcam_dir.exists():
        for path in sorted(gradcam_dir.glob("*pipeline-smoke-test*")):
            print(path.name)
    else:
        print("Grad-CAM directory not found.")

    print("\nTEST SCRIPT FINISHED")

except Exception as exc:
    print("\nTEST FAILED")
    print(type(exc).__name__ + ":", exc)
    traceback.print_exc()
    sys.exit(1)