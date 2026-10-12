"""
DravyaSetu AI Service
Multi-image evidence fusion.

Combines predictions from multiple images belonging to the
same plant/observation.

The system preserves:
- per-image prediction
- per-image confidence
- evidence type
- observation ID

This module does NOT perform classification.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


@dataclass
class ImageEvidence:
    """
    Prediction information for one image.
    """

    image_id: str
    predicted_class: int
    confidence: float
    evidence_type: str
    observation_id: Optional[str] = None


@dataclass
class FusedPrediction:
    """
    Final result after combining multiple images.
    """

    predicted_class: int
    confidence: float
    image_count: int
    supporting_images: list[str]


class EvidenceFusion:
    """
    Combines multiple image predictions.

    Evidence-type weights are deliberately conservative.
    They are configuration values and can later be tuned using
    validation data.

    The fusion strategy:
        1. Group evidence by predicted class.
        2. Weight each prediction by evidence reliability.
        3. Combine supporting evidence.
        4. Normalize the resulting scores.
    """

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

    def __init__(
        self,
        evidence_weights: Optional[
            dict[str, float]
        ] = None,
    ) -> None:

        self.evidence_weights = (
            evidence_weights.copy()
            if evidence_weights is not None
            else self.DEFAULT_WEIGHTS.copy()
        )

        for evidence_type, weight in (
            self.evidence_weights.items()
        ):
            if weight < 0:
                raise ValueError(
                    f"Weight for {evidence_type} "
                    "cannot be negative."
                )

    def get_weight(
        self,
        evidence_type: str,
    ) -> float:
        """
        Return the reliability weight for an evidence type.
        """

        return self.evidence_weights.get(
            evidence_type.upper(),
            self.evidence_weights["OTHER"],
        )

    def fuse(
        self,
        evidence: list[ImageEvidence],
    ) -> FusedPrediction:
        """
        Fuse predictions from multiple images.

        Args:
            evidence:
                List of image-level predictions.

        Returns:
            FusedPrediction.
        """

        if not evidence:
            raise ValueError(
                "At least one image evidence item is required."
            )

        class_scores: dict[int, float] = {}

        image_support: dict[int, list[str]] = {}

        total_weight = 0.0

        for item in evidence:

            if not 0.0 <= item.confidence <= 1.0:
                raise ValueError(
                    f"Invalid confidence for "
                    f"{item.image_id}."
                )

            weight = self.get_weight(
                item.evidence_type
            )

            weighted_score = (
                item.confidence * weight
            )

            class_scores[item.predicted_class] = (
                class_scores.get(
                    item.predicted_class,
                    0.0,
                )
                + weighted_score
            )

            image_support.setdefault(
                item.predicted_class,
                [],
            ).append(item.image_id)

            total_weight += weight

        if total_weight <= 0:
            raise ValueError(
                "Total evidence weight must be positive."
            )

        # Select the class with the strongest
        # accumulated evidence.
        predicted_class = max(
            class_scores,
            key=class_scores.get,
        )

        raw_score = class_scores[
            predicted_class
        ]

        # Normalize the winning score.
        fused_confidence = (
            raw_score / total_weight
        )

        fused_confidence = max(
            0.0,
            min(
                1.0,
                fused_confidence,
            ),
        )

        return FusedPrediction(
            predicted_class=predicted_class,
            confidence=fused_confidence,
            image_count=len(evidence),
            supporting_images=image_support[
                predicted_class
            ],
        )


def build_evidence_fusion() -> EvidenceFusion:
    """
    Factory function.
    """

    return EvidenceFusion()


if __name__ == "__main__":
    # CPU-only smoke test.
    #
    # These predictions are illustrative values.
    # They are NOT produced by a trained model.

    fusion = build_evidence_fusion()

    evidence = [
        ImageEvidence(
            image_id="image_001",
            predicted_class=7,
            confidence=0.82,
            evidence_type="LEAF",
            observation_id="obs_001",
        ),
        ImageEvidence(
            image_id="image_002",
            predicted_class=7,
            confidence=0.91,
            evidence_type="FLOWER",
            observation_id="obs_001",
        ),
        ImageEvidence(
            image_id="image_003",
            predicted_class=7,
            confidence=0.78,
            evidence_type="WHOLE_PLANT",
            observation_id="obs_001",
        ),
        ImageEvidence(
            image_id="image_004",
            predicted_class=12,
            confidence=0.60,
            evidence_type="LEAF",
            observation_id="obs_001",
        ),
    ]

    result = fusion.fuse(evidence)

    print("Evidence fusion test passed.")
    print(
        f"Predicted class: "
        f"{result.predicted_class}"
    )
    print(
        f"Fused confidence: "
        f"{result.confidence:.4f}"
    )
    print(
        f"Images used: "
        f"{result.image_count}"
    )
    print(
        f"Supporting images: "
        f"{result.supporting_images}"
    )