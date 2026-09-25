def classify_confidence(confidence: float) -> str:
    """
    Convert a confidence score into a simple category.

    Returns:
        HIGH, MEDIUM, or LOW
    """

    if not 0.0 <= confidence <= 1.0:
        raise ValueError(
            "Confidence must be between 0.0 and 1.0"
        )

    if confidence >= 0.80:
        return "HIGH"

    if confidence >= 0.50:
        return "MEDIUM"

    return "LOW"


def is_confident(confidence: float, threshold: float = 0.50) -> bool:
    """
    Determine whether a prediction meets the minimum
    confidence threshold.
    """

    if not 0.0 <= confidence <= 1.0:
        raise ValueError(
            "Confidence must be between 0.0 and 1.0"
        )

    return confidence >= threshold