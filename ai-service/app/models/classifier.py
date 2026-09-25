import torch
import torch.nn as nn

from torchvision.models import (
    efficientnet_v2_s,
    EfficientNet_V2_S_Weights
)


NUM_CLASSES = 34


def create_model(num_classes: int = NUM_CLASSES):
    """
    Create EfficientNetV2-S for DravyaSetu.

    The final classification layer is replaced
    with a 34-class plant classifier.
    """

    # Load ImageNet-pretrained EfficientNetV2-S
    weights = EfficientNet_V2_S_Weights.DEFAULT

    model = efficientnet_v2_s(weights=weights)

    # Get number of input features to final layer
    input_features = model.classifier[1].in_features

    # Replace ImageNet classifier with DravyaSetu classifier
    model.classifier[1] = nn.Linear(
        input_features,
        num_classes
    )

    return model