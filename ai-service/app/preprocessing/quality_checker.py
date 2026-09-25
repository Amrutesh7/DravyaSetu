from PIL import Image
import cv2
import numpy as np


# Minimum image dimensions
MIN_WIDTH = 224
MIN_HEIGHT = 224

# Image quality thresholds
BLUR_THRESHOLD = 50.0
DARK_THRESHOLD = 40.0
BRIGHT_THRESHOLD = 230.0


def check_image_quality(image: Image.Image) -> dict:
    """
    Check whether an input plant image is suitable
    for AI identification.

    Returns:
        {
            "acceptable": bool,
            "issues": list[str]
        }
    """

    issues = []

    # -------------------------------------------------
    # 1. Check image dimensions
    # -------------------------------------------------

    width, height = image.size

    if width < MIN_WIDTH or height < MIN_HEIGHT:
        issues.append(
            f"Image is too small: {width}x{height}. "
            f"Minimum required size is {MIN_WIDTH}x{MIN_HEIGHT}."
        )

    # -------------------------------------------------
    # Convert image to RGB
    # -------------------------------------------------

    image = image.convert("RGB")

    # Convert PIL image → NumPy array
    image_array = np.array(image)

    # Convert RGB → grayscale
    gray = cv2.cvtColor(
        image_array,
        cv2.COLOR_RGB2GRAY
    )

    # -------------------------------------------------
    # 2. Blur detection
    # -------------------------------------------------

    blur_score = cv2.Laplacian(
        gray,
        cv2.CV_64F
    ).var()

    if blur_score < BLUR_THRESHOLD:
        issues.append(
            f"Image is too blurry "
            f"(blur score: {blur_score:.2f})."
        )

    # -------------------------------------------------
    # 3. Darkness detection
    # -------------------------------------------------

    brightness = gray.mean()

    if brightness < DARK_THRESHOLD:
        issues.append(
            f"Image is too dark "
            f"(brightness: {brightness:.2f})."
        )

    # -------------------------------------------------
    # 4. Excessive brightness detection
    # -------------------------------------------------

    if brightness > BRIGHT_THRESHOLD:
        issues.append(
            f"Image is too bright "
            f"(brightness: {brightness:.2f})."
        )

    # -------------------------------------------------
    # Final result
    # -------------------------------------------------

    acceptable = len(issues) == 0

    return {
        "acceptable": acceptable,
        "issues": issues
    }