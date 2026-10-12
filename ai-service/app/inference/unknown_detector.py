
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
    """Reject predictions that fall below configured thresholds."""

    def __init__(
        self,
        confidence_threshold: float = 0.70,
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
            if not 0.0 <= embedding_similarity <= 1.0:
                raise ValueError(
                    "embedding_similarity must be between 0 and 1."
                )

        confidence_pass = (
            classifier_confidence >= self.confidence_threshold
        )

        if embedding_similarity is not None:
            similarity_pass = (
                embedding_similarity >= self.embedding_threshold
            )

            if confidence_pass and similarity_pass:
                return UnknownDetectionResult(
                    status="KNOWN",
                    classifier_confidence=classifier_confidence,
                    embedding_similarity=embedding_similarity,
                    accepted=True,
                    reason="Confidence and embedding thresholds passed.",
                )

            reason = (
                "Classifier confidence below threshold."
                if not confidence_pass
                else "Embedding similarity below threshold."
            )

            return UnknownDetectionResult(
                status="UNKNOWN",
                classifier_confidence=classifier_confidence,
                embedding_similarity=embedding_similarity,
                accepted=False,
                reason=reason,
            )

        if confidence_pass:
            return UnknownDetectionResult(
                status="KNOWN",
                classifier_confidence=classifier_confidence,
                embedding_similarity=None,
                accepted=True,
                reason=(
                    "Classifier confidence passed; "
                    "embedding similarity is unavailable."
                ),
            )

        return UnknownDetectionResult(
            status="UNKNOWN",
            classifier_confidence=classifier_confidence,
            embedding_similarity=None,
            accepted=False,
            reason="Classifier confidence below threshold.",
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
    confidence_threshold: float = 0.70,
    embedding_threshold: float = 0.50,
) -> UnknownDetector:
    return UnknownDetector(
        confidence_threshold=confidence_threshold,
        embedding_threshold=embedding_threshold,
    )


if __name__ == "__main__":
    detector = build_unknown_detector()

    known_result = detector.detect(
        classifier_confidence=0.91,
        embedding_similarity=0.82,
    )

    unknown_result = detector.detect(
        classifier_confidence=0.42,
        embedding_similarity=0.31,
    )

    print("Unknown detector smoke test completed.")
    print("Example 1:", known_result)
    print("Example 2:", unknown_result)