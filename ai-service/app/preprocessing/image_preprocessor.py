
"""
DravyaSetu AI Service
Image preprocessing pipeline.

Responsible for converting input images into the format expected
by EfficientNetV2-S.

This module does NOT perform classification.
"""

from __future__ import annotations

from pathlib import Path
from typing import Union

import torch
from PIL import Image
from torchvision import transforms
from torchvision.models import EfficientNet_V2_S_Weights


ImageInput = Union[str, Path, Image.Image]


class ImagePreprocessor:
    """
    Preprocess images for EfficientNetV2-S.

    The ImageNet normalization values come from the torchvision
    EfficientNetV2-S pretrained weight configuration.
    """

    def __init__(
        self,
        image_size: int = 300,
    ) -> None:
        if image_size <= 0:
            raise ValueError("image_size must be greater than 0.")

        self.image_size = image_size

        weights = EfficientNet_V2_S_Weights.DEFAULT
        normalization = weights.transforms()

        # Validation/inference preprocessing.
        self.transform = transforms.Compose(
            [
                transforms.Resize((image_size, image_size)),
                transforms.ToTensor(),
                transforms.Normalize(
                    mean=normalization.mean,
                    std=normalization.std,
                ),
            ]
        )

    def load_image(
        self,
        image: ImageInput,
    ) -> Image.Image:
        """
        Load an image and convert it to RGB.

        Args:
            image:
                File path or PIL Image.

        Returns:
            RGB PIL Image.
        """

        if isinstance(image, Image.Image):
            return image.convert("RGB")

        image_path = Path(image)

        if not image_path.exists():
            raise FileNotFoundError(
                f"Image not found: {image_path}"
            )

        if not image_path.is_file():
            raise ValueError(
                f"Path is not a file: {image_path}"
            )

        try:
            with Image.open(image_path) as img:
                return img.convert("RGB")
        except Exception as exc:
            raise ValueError(
                f"Unable to decode image: {image_path}"
            ) from exc

    def preprocess(
        self,
        image: ImageInput,
    ) -> torch.Tensor:
        """
        Convert an image into a model-ready tensor.

        Returns:
            Tensor with shape [1, 3, 300, 300] by default.
            The shape changes if a different image_size is supplied.
        """

        pil_image = self.load_image(image)

        tensor = self.transform(pil_image)

        # Add batch dimension.
        tensor = tensor.unsqueeze(0)

        return tensor

    def preprocess_batch(
        self,
        images: list[ImageInput],
    ) -> torch.Tensor:
        """
        Preprocess multiple images into one batch.

        Returns:
            Tensor with shape:
            [batch_size, 3, image_size, image_size].
        """

        if not images:
            raise ValueError(
                "images cannot be empty."
            )

        tensors = []

        for image in images:
            pil_image = self.load_image(image)
            tensors.append(self.transform(pil_image))

        return torch.stack(tensors, dim=0)


def build_preprocessor(
    image_size: int = 300,
) -> ImagePreprocessor:
    """
    Factory function for creating the image preprocessor.
    """

    return ImagePreprocessor(
        image_size=image_size
    )


if __name__ == "__main__":
    # CPU-only smoke test.
    # No model training and no GPU required.

    preprocessor = build_preprocessor(
        image_size=300
    )

    # Create a temporary in-memory test image.
    test_image = Image.new(
        "RGB",
        (800, 600),
        color="white",
    )

    tensor = preprocessor.preprocess(
        test_image
    )

    batch = preprocessor.preprocess_batch(
        [test_image, test_image]
    )

    print("Image preprocessing test passed.")
    print(f"Single image shape: {tuple(tensor.shape)}")
    print(f"Batch shape: {tuple(batch.shape)}")
    print(f"Tensor dtype: {tensor.dtype}")