
"""DravyaSetu single-image inference pipeline."""

from dataclasses import dataclass
import logging
from pathlib import Path
import tempfile
from uuid import uuid4

from PIL import Image

from app.explainability.gradcam import build_gradcam
from app.schemas.response_builder import (
    InternalAlternative,
    InternalImageResult,
    ResponseBuilder,
)

logger = logging.getLogger(__name__)


@dataclass
class PipelineConfig:
    top_k: int = 5


class DravyaSetuPipeline:
    def __init__(
        self,
        predictor,
        quality_checker,
        confidence_calculator,
        unknown_detector,
        embedding_extractor,
        evidence_fusion,
        response_builder,
        config: PipelineConfig,
    ):
        self.predictor = predictor
        self.quality_checker = quality_checker
        self.confidence_calculator = confidence_calculator
        self.unknown_detector = unknown_detector
        self.embedding_extractor = embedding_extractor
        self.evidence_fusion = evidence_fusion
        self.response_builder = response_builder
        self.config = config

        # ai-service/artifacts/gradcam
        self.gradcam_dir = (
            Path(__file__).resolve().parent.parent
            / "artifacts"
            / "gradcam"
        )
        self.gradcam_dir.mkdir(parents=True, exist_ok=True)

        # Reuse the same trained model; do not load or train another one.
        self.gradcam = build_gradcam(self.predictor)

    @staticmethod
    def _safe_filename(value: str) -> str:
        safe = "".join(
            character
            if character.isalnum() or character in ("-", "_")
            else "_"
            for character in str(value)
        )
        return safe or uuid4().hex

    def _generate_gradcam_urls(
        self,
        image: Image.Image,
        request_id: str,
        image_id: str,
    ) -> dict:
        """Generate Grad-CAM files and their API URLs."""
        prefix = (
            f"{self._safe_filename(request_id)}_"
            f"{self._safe_filename(image_id)}"
        )

        result = self.gradcam.generate(image)
        saved_paths = self.gradcam.save(
            result,
            output_dir=self.gradcam_dir,
            prefix=prefix,
        )

        # main.py must mount artifacts/gradcam at /artifacts/gradcam.
        return {
            "original_image_url": (
                f"/artifacts/gradcam/{Path(saved_paths['original']).name}"
            ),
            "heatmap_url": (
                f"/artifacts/gradcam/{Path(saved_paths['heatmap']).name}"
            ),
            "overlay_url": (
                f"/artifacts/gradcam/{Path(saved_paths['overlay']).name}"
            ),
        }

    def analyze_image(
        self,
        image: Image.Image,
        image_id: str,
        request_id: str,
        model_version: str = "dravya-v2",
    ):
        """Check image quality, predict its class, and build the API response."""
        quality = self.quality_checker.check(image)

        acceptable = bool(quality.acceptable)
        issues = list(getattr(quality, "issues", []) or [])
        messages = list(getattr(quality, "user_messages", []) or [])

        if not acceptable:
            result = InternalImageResult(
                image_id=image_id,
                quality_acceptable=False,
                quality_issues=issues,
                quality_messages=messages,
                status="UNKNOWN",
            )
            return self.response_builder.build_single_image_response(
                request_id=request_id,
                result=result,
                model_version=model_version,
            )

        temporary_path = None

        try:
            with tempfile.NamedTemporaryFile(
                suffix=".jpg",
                delete=False,
            ) as temporary:
                temporary_path = Path(temporary.name)

            image.convert("RGB").save(
                temporary_path,
                format="JPEG",
                quality=95,
            )

            prediction = self.predictor.predict(str(temporary_path))

        finally:
            if temporary_path is not None:
                temporary_path.unlink(missing_ok=True)

        top = prediction.top_prediction
        predicted_name = str(top.class_name)
        confidence = float(top.confidence)

        alternatives = []

        for item in prediction.alternatives[: self.config.top_k]:
            class_name = str(item.class_name)

            alternatives.append(
                InternalAlternative(
                    plant_id=class_name,
                    common_name=class_name.replace("_", " "),
                    scientific_name=class_name.replace("_", " "),
                    confidence=float(item.confidence),
                )
            )

        detection = self.unknown_detector.detect(
            classifier_confidence=confidence,
            embedding_similarity=None,
        )

        detection_status = getattr(detection, "status", None)

        if detection_status is not None:
            is_unknown = (
                str(detection_status).upper() == "UNKNOWN"
            )
        else:
            is_unknown = bool(
                getattr(detection, "is_unknown", False)
            )

        result = InternalImageResult(
            image_id=image_id,
            quality_acceptable=True,
            quality_issues=issues,
            quality_messages=messages,
            status="UNKNOWN" if is_unknown else "KNOWN",
            plant_id=None if is_unknown else predicted_name,
            confidence=0.0 if is_unknown else confidence,
            alternatives=alternatives,
        )

        # Grad-CAM is an explanation, not a condition for classification.
        try:
            explanation_urls = self._generate_gradcam_urls(
                image=image,
                request_id=request_id,
                image_id=image_id,
            )

            # These attributes must exist on InternalImageResult.
            result.original_image_url = explanation_urls[
                "original_image_url"
            ]
            result.heatmap_url = explanation_urls["heatmap_url"]
            result.overlay_url = explanation_urls["overlay_url"]

        except Exception:
            logger.exception(
                "Grad-CAM generation failed for request_id=%s, image_id=%s",
                request_id,
                image_id,
            )

            # Keep classification available if visualization fails.
            # Add a warning only if your InternalImageResult supports it.
            if hasattr(result, "processing_warnings"):
                result.processing_warnings.append(
                    "Grad-CAM visualization could not be generated."
                )

        return self.response_builder.build_single_image_response(
            request_id=request_id,
            result=result,
            model_version=model_version,
        )