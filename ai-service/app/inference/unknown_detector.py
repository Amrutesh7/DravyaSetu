DEFAULT_UNKNOWN_THRESHOLD = 0.50


def detect_unknown(
    confidence: float,
    threshold: float = DEFAULT_UNKNOWN_THRESHOLD
) -> bool:
    """
    Determine whether a prediction should be treated
    as UNKNOWN.

    Args:
        confidence: Model confidence between 0 and 1.
        threshold: Minimum confidence required to accept
                   a known plant.

    Returns:
        True  -> UNKNOWN
        False -> KNOWN
    """

    if not 0.0 <= confidence <= 1.0:
        raise ValueError(
            "Confidence must be between 0.0 and 1.0"
        )

    if not 0.0 <= threshold <= 1.0:
        raise ValueError(
            "Threshold must be between 0.0 and 1.0"
        )

    return confidence < threshold


def get_identification_status(
    confidence: float,
    threshold: float = DEFAULT_UNKNOWN_THRESHOLD
) -> str:
    """
    Return the status expected by the shared AI contract.

    Returns:
        'KNOWN' or 'UNKNOWN'
    """

    if detect_unknown(confidence, threshold):
        return "UNKNOWN"

    return "KNOWN"