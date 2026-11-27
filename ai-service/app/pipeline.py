
"""DravyaSetu single-image and multi-image inference pipeline."""

import base64
from dataclasses import dataclass
import logging
from pathlib import Path
from uuid import uuid4

from PIL import Image

from app.explainability.gradcam import build_gradcam
from app.multi_image.evidence_fusion import ImageEvidence
from app.models.plant_registry import get_plant_by_name
from app.schemas.response_builder import (
    InternalAlternative,
    InternalImageResult,
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

        self.gradcam_dir = (
            Path(__file__).resolve().parent.parent
            / "artifacts"
            / "gradcam"
        )
        self.gradcam_dir.mkdir(parents=True, exist_ok=True)

        self.gradcam = build_gradcam(self.predictor)

    @staticmethod
    def _safe_filename(value: str) -> str:
        safe = "".join(
            character
            if character.isascii()
            and (character.isalnum() or character in ("-", "_"))
            else "_"
            for character in str(value)
        )[:48]
        return safe or uuid4().hex

    def _generate_gradcam_payload(
        self,
        image: Image.Image,
        request_id: str,
        image_id: str,
    ) -> dict:
        """Generate a JPEG heatmap and encode it for JSON transport."""

        prefix = (
            f"{self._safe_filename(request_id)}_"
            f"{self._safe_filename(image_id)}"
        )

        gradcam_result = self.gradcam.generate(image)

        # Preserve the local artifact for debugging and inspection.
        saved_paths = self.gradcam.save(
            gradcam_result,
            output_dir=self.gradcam_dir,
            prefix=prefix,
        )

        heatmap_path = Path(saved_paths["heatmap"])
        heatmap_bytes = heatmap_path.read_bytes()

        if not heatmap_bytes:
            raise ValueError("Generated Grad-CAM JPEG is empty.")

        image_data = base64.b64encode(
            heatmap_bytes
        ).decode("ascii")

        return {
            "image_data": image_data,
            "heatmap_data": image_data,
            "heatmap_url": f"/artifacts/gradcam/{Path(saved_paths['heatmap']).name}",
            "overlay_url": f"/artifacts/gradcam/{Path(saved_paths['overlay']).name}",
            "original_image_url": f"/artifacts/gradcam/{Path(saved_paths['original']).name}",
            "media_type": "image/jpeg",
        }

    def _analyze_one(
        self,
        image: Image.Image,
        image_id: str,
        evidence_type: str = "OTHER",
        observation_id: str = None,
    ) -> InternalImageResult:
        """Run quality checks and model inference for one image."""

        quality = self.quality_checker.check(image)

        acceptable = bool(quality.acceptable)
        issues = list(getattr(quality, "issues", []) or [])
        messages = list(getattr(quality, "user_messages", []) or [])

        if not acceptable:
            return InternalImageResult(
                image_id=image_id,
                quality_acceptable=False,
                quality_issues=issues,
                quality_messages=messages,
                status="UNKNOWN",
                plant_id=None,
                class_index=None,
                predicted_class_name=None,
                confidence=0.0,
                alternatives=[],
                evidence_type=evidence_type,
                observation_id=observation_id,
            )

        prediction = self.predictor.predict(image)

        top = prediction.top_prediction
        predicted_name = str(top.class_name)
        confidence = float(top.confidence)

        raw_class_index = getattr(top, "class_index", None)
        class_index = (
            int(raw_class_index)
            if raw_class_index is not None
            else None
        )

        alternatives = []

        for item in prediction.alternatives[: self.config.top_k]:
            class_name = str(item.class_name)

            try:
                registry_entry = get_plant_by_name(class_name)
            except KeyError:
                logger.warning(
                    "Alternative class '%s' missing from plant registry.",
                    class_name,
                )
                continue

            alternatives.append(
                InternalAlternative(
                    plant_id=class_name,
                    common_name=registry_entry["common_name"],
                    scientific_name=registry_entry["scientific_name"],
                    confidence=float(item.confidence),
                )
            )

        detection = self.unknown_detector.detect(
            classifier_confidence=confidence,
            embedding_similarity=None,
        )

        is_unknown = str(detection.status).upper() != "KNOWN"

        return InternalImageResult(
            image_id=image_id,
            quality_acceptable=True,
            quality_issues=issues,
            quality_messages=messages,
            status="UNKNOWN" if is_unknown else "KNOWN",
            plant_id=None if is_unknown else predicted_name,
            class_index=class_index,
            predicted_class_name=predicted_name,
            confidence=confidence,
            alternatives=alternatives,
            evidence_type=evidence_type,
            observation_id=observation_id,
        )

    def _add_gradcam(
        self,
        result: InternalImageResult,
        image: Image.Image,
        request_id: str,
    ) -> None:
        """Attach Base64 JPEG data without affecting classification."""

        try:
            payload = self._generate_gradcam_payload(
                image=image,
                request_id=request_id,
                image_id=result.image_id,
            )

            result.heatmap_image_data = payload["image_data"]
            result.heatmap_data = payload["image_data"]
            result.heatmap_url = payload.get("heatmap_url")
            result.overlay_url = payload.get("overlay_url")
            result.original_image_url = payload.get("original_image_url")
            result.heatmap_media_type = payload["media_type"]

        except Exception:
            logger.exception(
                "Grad-CAM generation failed for request_id=%s, image_id=%s",
                request_id,
                result.image_id,
            )

    @staticmethod
    def _apply_registry_metadata(
        result: InternalImageResult,
    ) -> None:
        """Map multi-image class names to official plant registry IDs."""

        class_name = result.predicted_class_name

        if not class_name:
            result.status = "UNKNOWN"
            result.plant_id = None
            return

        try:
            registry_entry = get_plant_by_name(class_name)
        except KeyError:
            logger.warning(
                "Plant class '%s' is missing from the official registry.",
                class_name,
            )
            result.status = "UNKNOWN"
            result.plant_id = None
            return

        result.plant_id = registry_entry["plant_id"]

        for alternative in result.alternatives:
            try:
                alternative_entry = get_plant_by_name(
                    alternative.plant_id
                )
            except KeyError:
                logger.warning(
                    "Alternative class '%s' missing from plant registry.",
                    alternative.plant_id,
                )
                continue

            alternative.plant_id = alternative_entry["plant_id"]
            alternative.common_name = alternative_entry["common_name"]
            alternative.scientific_name = (
                alternative_entry["scientific_name"]
            )

    def analyze_image(
        self,
        image: Image.Image,
        image_id: str,
        request_id: str,
        model_version: str = "dravya-v2",
        evidence_type: str = "OTHER",
    ):
        """Preserve existing single-image API behavior."""

        result = self._analyze_one(
            image=image,
            image_id=image_id,
            evidence_type=evidence_type,
            observation_id=request_id,
        )

        if result.quality_acceptable:
            self._apply_registry_metadata(result)
            self._add_gradcam(
                result=result,
                image=image,
                request_id=request_id,
            )

        return self.response_builder.build_single_image_response(
            request_id=request_id,
            result=result,
            model_version=model_version,
        )

    def analyze_images(
        self,
        images: list[Image.Image],
        image_ids: list[str],
        evidence_types: list[str],
        request_id: str,
        model_version: str = "dravya-v2",
    ):
        """Fuse multiple images belonging to one plant observation."""

        if not images:
            raise ValueError("At least one image is required.")

        if len(images) > 8:
            raise ValueError("A maximum of 8 images is supported.")

        if len(images) != len(image_ids):
            raise ValueError(
                "The number of image IDs must match the number of images."
            )

        if len(images) != len(evidence_types):
            raise ValueError(
                "The number of evidence types must match the number of images."
            )

        observation_id = request_id
        image_results = []

        # Independently process every submitted image.
        for image, image_id, evidence_type in zip(
            images,
            image_ids,
            evidence_types,
        ):
            result = self._analyze_one(
                image=image,
                image_id=image_id,
                evidence_type=evidence_type,
                observation_id=observation_id,
            )

            if result.quality_acceptable:
                self._add_gradcam(
                    result=result,
                    image=image,
                    request_id=request_id,
                )

            image_results.append(result)

        # Exclude poor-quality images from evidence fusion.
        usable_results = [
            result
            for result in image_results
            if result.quality_acceptable
        ]

        evidence_items = []

        for result in usable_results:
            if result.class_index is None:
                logger.warning(
                    "Image %s has no numeric class index; "
                    "it cannot participate in evidence fusion.",
                    result.image_id,
                )
                continue

            evidence_items.append(
                ImageEvidence(
                    image_id=result.image_id,
                    predicted_class=result.class_index,
                    confidence=result.confidence,
                    evidence_type=result.evidence_type,
                    observation_id=observation_id,
                )
            )

        # Fuse numeric class predictions.
        # In multi-image mode, individual images may each be uncertain (< threshold)
        # but together they can form a confident consensus. The fusion result's
        # own agreement_ratio and confidence determine acceptance — not whether
        # every individual image cleared the single-image unknown threshold.
        fusion_result = None

        if evidence_items:
            fusion_result = self.evidence_fusion.fuse(
                evidence_items
            )

            # Only mark unaccepted if fewer evidence items than usable images
            # (i.e. some image had no class index — truly unclassifiable).
            every_usable_image_has_evidence = (
                len(evidence_items) == len(usable_results)
            )

            if not every_usable_image_has_evidence:
                fusion_result.accepted = False

        # Map predictions to official plant registry IDs.
        for result in usable_results:
            self._apply_registry_metadata(result)

        # After registry lookup, if the winning class is not in the registry,
        # the matching result's status will be "UNKNOWN" — in that case reject.
        if fusion_result is not None and fusion_result.accepted:
            matching_result = next(
                (
                    r for r in usable_results
                    if r.class_index == fusion_result.predicted_class
                ),
                None,
            )
            if matching_result is None or matching_result.plant_id is None:
                fusion_result.accepted = False

        # Build response with all image results.
        return self.response_builder.build_multi_image_response(
            request_id=request_id,
            image_results=image_results,
            fusion_result=fusion_result,
            model_version=model_version,
        )
