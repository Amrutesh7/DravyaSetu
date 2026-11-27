
"""DravyaSetu unknown-plant detection."""

from dataclasses import dataclass
from typing import Optional


@dataclass
class UnknownDetectionResult:
    status: str
    classifier_confidence: float
    embedding_similarity: Optional[float]
    accepted: bool
    reason: str


class UnknownDetector:
    """Decide whether a prediction meets configured acceptance thresholds."""

    def __init__(
        self,
        confidence_threshold: float = 0.95,
        embedding_threshold: float = 0.50,
    ) -> None:
        if not 0.0 <= confidence_threshold <= 1.0:
            raise ValueError(
                "confidence_threshold must be between 0 and 1."
            )

        if not 0.0 <= embedding_threshold <= 1.0:
            raise ValueError(
                "embedding_threshold must be between 0 and 1."
            )

        self.confidence_threshold = confidence_threshold
        self.embedding_threshold = embedding_threshold

    def detect(
        self,
        classifier_confidence: float,
        embedding_similarity: Optional[float] = None,
    ) -> UnknownDetectionResult:
        if not 0.0 <= classifier_confidence <= 1.0:
            raise ValueError(
                "classifier_confidence must be between 0 and 1."
            )

        if embedding_similarity is not None:
            if not -1.0 <= embedding_similarity <= 1.0:
                raise ValueError(
                    "embedding_similarity must be between -1 and 1."
                )

        confidence_pass = (
            classifier_confidence >= self.confidence_threshold
        )

        # If a real embedding similarity is supplied, require both checks.
        if embedding_similarity is not None:
            similarity_pass = (
                embedding_similarity >= self.embedding_threshold
            )

            accepted = confidence_pass and similarity_pass

            if accepted:
                reason = "Confidence and embedding thresholds passed."
            elif not confidence_pass:
                reason = "Classifier confidence below threshold."
            else:
                reason = "Embedding similarity below threshold."

            return UnknownDetectionResult(
                status="KNOWN" if accepted else "UNKNOWN",
                classifier_confidence=classifier_confidence,
                embedding_similarity=embedding_similarity,
                accepted=accepted,
                reason=reason,
            )

        # Without an embedding score, use the confidence threshold only.
        accepted = confidence_pass

        return UnknownDetectionResult(
            status="KNOWN" if accepted else "UNKNOWN",
            classifier_confidence=classifier_confidence,
            embedding_similarity=None,
            accepted=accepted,
            reason=(
                "Classifier confidence passed; embedding similarity "
                "is unavailable."
                if accepted
                else "Classifier confidence below threshold; "
                     "embedding similarity is unavailable."
            ),
        )

    def update_thresholds(
        self,
        confidence_threshold: float,
        embedding_threshold: float,
    ) -> None:
        if not 0.0 <= confidence_threshold <= 1.0:
            raise ValueError(
                "confidence_threshold must be between 0 and 1."
            )

        if not 0.0 <= embedding_threshold <= 1.0:
            raise ValueError(
                "embedding_threshold must be between 0 and 1."
            )

        self.confidence_threshold = confidence_threshold
        self.embedding_threshold = embedding_threshold


def build_unknown_detector(
    confidence_threshold: float = 0.95,
    embedding_threshold: float = 0.50,
) -> UnknownDetector:
    return UnknownDetector(
        confidence_threshold=confidence_threshold,
        embedding_threshold=embedding_threshold,
    )


if __name__ == "__main__":
    detector = build_unknown_detector()

    for confidence in (0.9992, 0.9185, 0.3832):
        result = detector.detect(
            classifier_confidence=confidence,
        )
        print(f"Confidence: {confidence:.4f} -> {result}")