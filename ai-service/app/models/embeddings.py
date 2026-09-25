import torch
import torch.nn as nn

from torchvision.models import (
    efficientnet_v2_s,
    EfficientNet_V2_S_Weights
)


class PlantEmbeddingModel:
    """
    Extract feature embeddings from EfficientNetV2-S.

    These embeddings will later be used by FAISS
    for plant similarity search.
    """

    def __init__(self, model=None):
        if model is None:
            weights = EfficientNet_V2_S_Weights.DEFAULT
            model = efficientnet_v2_s(weights=weights)

        self.model = model

        # Remove the final classification layer.
        # EfficientNetV2-S produces a 1280-dimensional
        # feature representation before classification.
        self.model.classifier = nn.Identity()

        self.model.eval()

    def extract(self, image_tensor: torch.Tensor) -> torch.Tensor:
        """
        Extract an embedding from a preprocessed image.

        Input:
            [batch, 3, 224, 224]

        Output:
            [batch, 1280]
        """

        if image_tensor.ndim != 4:
            raise ValueError(
                "Expected tensor with shape "
                "[batch, channels, height, width]"
            )

        if image_tensor.shape[1:] != (3, 224, 224):
            raise ValueError(
                "Expected tensor shape "
                "[batch, 3, 224, 224]"
            )

        with torch.no_grad():
            embedding = self.model(image_tensor)

        return embedding