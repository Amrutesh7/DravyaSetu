"""
DravyaSetu AI Service
EfficientNetV2-S classifier definition.

This file defines the model architecture only.
Training, inference, preprocessing, and unknown detection
are handled by separate modules.
"""

from __future__ import annotations

import torch
from torch import nn
from torchvision.models import (
    EfficientNet_V2_S_Weights,
    efficientnet_v2_s,
)


class DravyaSetuClassifier(nn.Module):
    """
    EfficientNetV2-S classifier for DravyaSetu.

    Input:
        RGB image tensor of shape:
        [batch_size, 3, height, width]

    Output:
        Logits of shape:
        [batch_size, num_classes]
    """

    def __init__(
        self,
        num_classes: int = 34,
        pretrained: bool = True,
        dropout: float = 0.2,
    ) -> None:
        super().__init__()

        if num_classes <= 0:
            raise ValueError("num_classes must be greater than 0.")

        if not 0.0 <= dropout < 1.0:
            raise ValueError("dropout must be in the range [0, 1).")

        # Load ImageNet weights only when explicitly requested.
        # For loading the trained DravyaSetu checkpoint, use
        # pretrained=False to avoid downloading ImageNet weights.
        weights = (
            EfficientNet_V2_S_Weights.DEFAULT
            if pretrained
            else None
        )

        # Create the EfficientNetV2-S backbone.
        self.model = efficientnet_v2_s(weights=weights)

        # EfficientNetV2-S classifier input size is 1280.
        in_features = self.model.classifier[1].in_features

        # Replace the ImageNet classification head with
        # the DravyaSetu classification head.
        self.model.classifier = nn.Sequential(
            nn.Dropout(p=dropout),
            nn.Linear(in_features, num_classes),
        )

        self.num_classes = num_classes
        self.dropout = dropout

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Run a forward pass.

        Args:
            x: Batch of RGB image tensors.

        Returns:
            Raw classification logits with shape
            [batch_size, num_classes].
        """
        return self.model(x)

    def get_features(self, x: torch.Tensor) -> torch.Tensor:
        """
        Extract 1280-dimensional feature embeddings before
        the classification head.

        Args:
            x: Batch of RGB image tensors.

        Returns:
            Feature tensor with shape [batch_size, 1280].
        """
        features = self.model.features(x)
        features = self.model.avgpool(features)
        features = torch.flatten(features, 1)

        return features


def build_classifier(
    num_classes: int = 34,
    pretrained: bool = True,
    dropout: float = 0.2,
) -> DravyaSetuClassifier:
    """
    Factory function for creating a DravyaSetu classifier.

    Set pretrained=False when loading an existing trained
    checkpoint, so ImageNet weights are not downloaded.
    """
    return DravyaSetuClassifier(
        num_classes=num_classes,
        pretrained=pretrained,
        dropout=dropout,
    )


if __name__ == "__main__":
    # Architecture smoke test only.
    # This does NOT train the model or modify checkpoint files.

    model = build_classifier(
        num_classes=34,
        pretrained=False,
    )

    dummy_input = torch.randn(2, 3, 300, 300)

    model.eval()

    with torch.no_grad():
        logits = model(dummy_input)
        features = model.get_features(dummy_input)

    print("Model: EfficientNetV2-S")
    print(f"Number of classes: {model.num_classes}")
    print(f"Logits shape: {tuple(logits.shape)}")
    print(f"Feature shape: {tuple(features.shape)}")
