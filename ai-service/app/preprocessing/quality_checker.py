
"""Image quality validation for the DravyaSetu AI service."""

from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Union

import cv2
import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError


ImageInput = Union[str, Path, Image.Image]


@dataclass
class QualityResult:
    """Result returned by ImageQualityChecker."""

    acceptable: bool
    score: float
    width: int
    height: int
    aspect_ratio: float
    brightness: float
    blur_score: float
    issues: List[str] = field(default_factory=list)
    user_messages: List[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        """Backward-compatible alias for acceptable."""
        return self.acceptable


class ImageQualityChecker:
    """
    Validate image readability before plant classification.

    These checks measure image quality. They do not determine whether
    an image actually contains a medicinal plant.
    """

    DEFAULT_MESSAGES = {
        "IMAGE_NOT_FOUND": "The image could not be found.",
        "INVALID_IMAGE": "The uploaded file is not a valid image.",
        "LOW_RESOLUTION": (
            "The image resolution is too low. Upload a clearer image."
        ),
        "BLURRY": (
            "The image appears blurry. Hold the camera steady and retry."
        ),
        "TOO_DARK": (
            "The image is too dark. Capture it in better lighting."
        ),
        "OVEREXPOSED": (
            "The image is too bright. Avoid direct glare and retry."
        ),
        "INVALID_ASPECT_RATIO": (
            "The image shape is unsuitable. Upload a normal plant photo."
        ),
    }

    def __init__(
        self,
        min_width: int = 384,
        min_height: int = 250,
        min_blur_score: float = 35.0,
        min_brightness: float = 25.0,
        max_brightness: float = 235.0,
        min_quality_score: float = 0.45,
        min_aspect_ratio: float = 0.25,
        max_aspect_ratio: float = 4.0,
    ):
        if min_width <= 0 or min_height <= 0:
            raise ValueError(
                "Minimum image dimensions must be positive."
            )

        if min_blur_score < 0:
            raise ValueError(
                "min_blur_score cannot be negative."
            )

        if not 0 <= min_brightness < max_brightness <= 255:
            raise ValueError(
                "Brightness thresholds must satisfy "
                "0 <= min_brightness < max_brightness <= 255."
            )

        if not 0 <= min_quality_score <= 1:
            raise ValueError(
                "min_quality_score must be between 0 and 1."
            )

        if not 0 < min_aspect_ratio < max_aspect_ratio:
            raise ValueError(
                "Aspect-ratio thresholds must be positive and ordered."
            )

        self.min_width = min_width
        self.min_height = min_height
        self.min_blur_score = min_blur_score
        self.min_brightness = min_brightness
        self.max_brightness = max_brightness
        self.min_quality_score = min_quality_score
        self.min_aspect_ratio = min_aspect_ratio
        self.max_aspect_ratio = max_aspect_ratio

    @staticmethod
    def _load_image(image: ImageInput) -> Image.Image:
        """Load a PIL image or file path and return an RGB image."""
        if isinstance(image, Image.Image):
            return ImageOps.exif_transpose(image).convert("RGB")

        path = Path(image)

        if not path.exists() or not path.is_file():
            raise FileNotFoundError(str(path))

        with Image.open(path) as opened:
            opened.load()
            return ImageOps.exif_transpose(opened).convert("RGB")

    @staticmethod
    def _calculate_brightness(image: Image.Image) -> float:
        """Calculate mean grayscale brightness in the range 0–255."""
        grayscale = np.asarray(
            image.convert("L"),
            dtype=np.float32,
        )
        return float(np.mean(grayscale))

    @staticmethod
    def _calculate_blur_score(image: Image.Image) -> float:
        """
        Estimate sharpness using variance of the Laplacian.

        Higher values generally indicate more edges and detail.
        This measurement depends on image content and is a heuristic.
        """
        grayscale = np.asarray(
            image.convert("L"),
            dtype=np.uint8,
        )

        height, width = grayscale.shape

        if width < 3 or height < 3:
            return 0.0

        # Standardize large images to reduce dimension-related variation.
        scale = min(1.0, 512.0 / max(width, height))

        if scale < 1.0:
            new_width = max(3, int(round(width * scale)))
            new_height = max(3, int(round(height * scale)))

            grayscale = cv2.resize(
                grayscale,
                (new_width, new_height),
                interpolation=cv2.INTER_AREA,
            )

        laplacian = cv2.Laplacian(
            grayscale,
            cv2.CV_64F,
        )

        return float(laplacian.var())

    def _calculate_quality_score(
        self,
        width: int,
        height: int,
        brightness: float,
        blur_score: float,
    ) -> float:
        """Calculate a diagnostic image-quality score between 0 and 1."""
        sharpness_score = np.clip(
            blur_score / max(self.min_blur_score * 4.0, 1.0),
            0.0,
            1.0,
        )

        midpoint = (
            self.min_brightness + self.max_brightness
        ) / 2.0

        half_range = (
            self.max_brightness - self.min_brightness
        ) / 2.0

        brightness_score = 1.0 - (
            abs(brightness - midpoint) / max(half_range, 1.0)
        )

        brightness_score = float(
            np.clip(brightness_score, 0.0, 1.0)
        )

        resolution_score = min(
            width / self.min_width,
            height / self.min_height,
            1.0,
        )

        resolution_score = float(
            np.clip(resolution_score, 0.0, 1.0)
        )

        score = (
            0.45 * sharpness_score
            + 0.30 * brightness_score
            + 0.25 * resolution_score
        )

        return float(np.clip(score, 0.0, 1.0))

    def check(self, image: ImageInput) -> QualityResult:
        """
        Validate an image and return its quality metrics.

        An image is rejected only when one or more explicit checks fail.
        A low combined score alone does not reject the image.
        """
        try:
            pil_image = self._load_image(image)

        except FileNotFoundError:
            return QualityResult(
                acceptable=False,
                score=0.0,
                width=0,
                height=0,
                aspect_ratio=0.0,
                brightness=0.0,
                blur_score=0.0,
                issues=["IMAGE_NOT_FOUND"],
                user_messages=[
                    self.DEFAULT_MESSAGES["IMAGE_NOT_FOUND"]
                ],
            )

        except (
            UnidentifiedImageError,
            OSError,
            ValueError,
            TypeError,
        ):
            return QualityResult(
                acceptable=False,
                score=0.0,
                width=0,
                height=0,
                aspect_ratio=0.0,
                brightness=0.0,
                blur_score=0.0,
                issues=["INVALID_IMAGE"],
                user_messages=[
                    self.DEFAULT_MESSAGES["INVALID_IMAGE"]
                ],
            )

        width, height = pil_image.size

        if width <= 0 or height <= 0:
            return QualityResult(
                acceptable=False,
                score=0.0,
                width=width,
                height=height,
                aspect_ratio=0.0,
                brightness=0.0,
                blur_score=0.0,
                issues=["INVALID_IMAGE"],
                user_messages=[
                    self.DEFAULT_MESSAGES["INVALID_IMAGE"]
                ],
            )

        aspect_ratio = width / height
        brightness = self._calculate_brightness(pil_image)
        blur_score = self._calculate_blur_score(pil_image)

        score = self._calculate_quality_score(
            width=width,
            height=height,
            brightness=brightness,
            blur_score=blur_score,
        )

        issues = []

        # 1. Resolution check.
        if width < self.min_width or height < self.min_height:
            issues.append("LOW_RESOLUTION")

        # 2. Aspect-ratio check.
        if not (
            self.min_aspect_ratio
            <= aspect_ratio
            <= self.max_aspect_ratio
        ):
            issues.append("INVALID_ASPECT_RATIO")

        # 3. Brightness checks.
        if brightness < self.min_brightness:
            issues.append("TOO_DARK")
        elif brightness > self.max_brightness:
            issues.append("OVEREXPOSED")

        # 4. Blur check.
        if blur_score < self.min_blur_score:
            issues.append("BLURRY")

        # The composite score is diagnostic, not a rejection condition.
        issues = list(dict.fromkeys(issues))

        user_messages = [
            self.DEFAULT_MESSAGES[issue]
            for issue in issues
            if issue in self.DEFAULT_MESSAGES
        ]

        return QualityResult(
            acceptable=len(issues) == 0,
            score=score,
            width=width,
            height=height,
            aspect_ratio=aspect_ratio,
            brightness=brightness,
            blur_score=blur_score,
            issues=issues,
            user_messages=user_messages,
        )