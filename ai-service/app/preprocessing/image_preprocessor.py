from PIL import Image
import torch
from torchvision import transforms


# EfficientNetV2 expects ImageNet-style normalization
IMAGE_SIZE = 224

IMAGE_TRANSFORM = transforms.Compose([
    transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225]
    )
])


def preprocess_image(image: Image.Image) -> torch.Tensor:
    """
    Preprocess a plant image for EfficientNetV2.

    Input:
        PIL Image

    Output:
        Tensor with shape:
        [1, 3, 224, 224]
    """

    # Ensure RGB format
    image = image.convert("RGB")

    # Apply preprocessing
    tensor = IMAGE_TRANSFORM(image)

    # Add batch dimension
    tensor = tensor.unsqueeze(0)

    return tensor