
"""
DravyaSetu AI Service
Conservative multi-image evidence fusion.
"""

from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Optional


@dataclass
class ImageEvidence:
    """Prediction information for one quality-accepted image."""

    image_id: str
    predicted_class: int
    confidence: float
    evidence_type: str
    observation_id: Optional[str] = None


@dataclass
class FusedPrediction:
    """Result of combining image-level evidence."""

    predicted_class: int
    confidence: float
    image_count: int
    supporting_images: list[str]
    agreement_ratio: float
    accepted: bool


class EvidenceFusion:
    """Weighted voting with conservative disagreement handling."""

    DEFAULT_WEIGHTS = {
        "WHOLE_PLANT": 1.00,
        "LEAF": 1.00,
        "FLOWER": 0.95,
        "FRUIT": 0.95,
        "STEM_BARK": 0.85,
        "ROOT": 0.80,
        "SEED": 0.75,
        "OTHER": 0.70,
    }

    MIN_AGREEMENT_RATIO = 0.80

    def __init__(
        self,
        evidence_weights: Optional[dict[str, float]] = None,
        min_agreement_ratio: float = MIN_AGREEMENT_RATIO,
    ) -> None:
        self.evidence_weights = (
            evidence_weights.copy()
            if evidence_weights is not None
            else self.DEFAULT_WEIGHTS.copy()
        )

        if not 0.0 < min_agreement_ratio <= 1.0:
            raise ValueError(
                "min_agreement_ratio must be greater than 0 and at most 1."
            )

        self.min_agreement_ratio = min_agreement_ratio

        for evidence_type, weight in self.evidence_weights.items():
            if not math.isfinite(weight) or weight < 0:
                raise ValueError(
                    f"Invalid weight for {evidence_type}."
                )

        if "OTHER" not in self.evidence_weights:
            raise ValueError(
                "Evidence weights must contain an OTHER fallback."
            )

    def get_weight(self, evidence_type: str) -> float:
        """Return the configured weight for an evidence type."""

        return self.evidence_weights.get(
            evidence_type.upper(),
            self.evidence_weights["OTHER"],
        )

    def fuse(
        self,
        evidence: list[ImageEvidence],
    ) -> FusedPrediction:
        """Combine predictions without hiding strong disagreement."""

        if not evidence:
            raise ValueError(
                "At least one image evidence item is required."
            )

        observation_ids = {
            item.observation_id
            for item in evidence
            if item.observation_id is not None
        }

        if len(observation_ids) > 1:
            raise ValueError(
                "Evidence belongs to different observations."
            )

        class_scores: dict[int, float] = {}
        class_weights: dict[int, float] = {}
        image_support: dict[int, list[str]] = {}
        supporting_confidences: dict[int, list[float]] = {}

        total_weight = 0.0

        for item in evidence:
            if not isinstance(item.predicted_class, int):
                raise ValueError(
                    f"Invalid class index for {item.image_id}."
                )

            if (
                not math.isfinite(item.confidence)
                or not 0.0 <= item.confidence <= 1.0
            ):
                raise ValueError(
                    f"Invalid confidence for {item.image_id}."
                )

            weight = self.get_weight(item.evidence_type)

            if weight <= 0:
                raise ValueError(
                    f"Evidence weight for {item.evidence_type} "
                    "must be positive."
                )

            class_scores[item.predicted_class] = (
                class_scores.get(item.predicted_class, 0.0)
                + item.confidence * weight
            )

            class_weights[item.predicted_class] = (
                class_weights.get(item.predicted_class, 0.0)
                + weight
            )

            image_support.setdefault(
                item.predicted_class, []
            ).append(item.image_id)

            supporting_confidences.setdefault(
                item.predicted_class, []
            ).append(item.confidence)

            total_weight += weight

        if total_weight <= 0:
            raise ValueError(
                "Total evidence weight must be positive."
            )

        predicted_class = max(
            class_scores,
            key=class_scores.get,
        )

        agreement_ratio = (
            class_weights[predicted_class] / total_weight
        )

        # This is the lowest individual model confidence among
        # supporting images, not a calibrated fused probability.
        conservative_confidence = min(
            supporting_confidences[predicted_class]
        )

        accepted = (
            agreement_ratio >= self.min_agreement_ratio
        )

        return FusedPrediction(
            predicted_class=predicted_class,
            confidence=conservative_confidence,
            image_count=len(evidence),
            supporting_images=image_support[predicted_class],
            agreement_ratio=agreement_ratio,
            accepted=accepted,
        )


def build_evidence_fusion() -> EvidenceFusion:
    """Create the default evidence fusion component."""

    return EvidenceFusion()


if __name__ == "__main__":
    fusion = build_evidence_fusion()

    evidence = [
        ImageEvidence("image_001", 7, 0.98, "LEAF", "obs_001"),
        ImageEvidence("image_002", 7, 0.97, "FLOWER", "obs_001"),
        ImageEvidence("image_003", 7, 0.96, "WHOLE_PLANT", "obs_001"),
        ImageEvidence("image_004", 7, 0.98, "FRUIT", "obs_001"),
        ImageEvidence("image_005", 12, 0.97, "LEAF", "obs_001"),
    ]

    result = fusion.fuse(evidence)

    print("Evidence fusion smoke test")
    print("Predicted class:", result.predicted_class)
    print("Conservative confidence:", result.confidence)
    print("Agreement ratio:", result.agreement_ratio)
    print("Accepted:", result.accepted)
    print("Supporting images:", result.supporting_images)
