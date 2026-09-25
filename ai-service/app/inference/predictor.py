import torch
import torch.nn.functional as F

from app.models.classifier import create_model
from app.models.plant_registry import load_plant_registry


class PlantPredictor:
    """
    Runs EfficientNetV2-S inference and converts
    model output into DravyaSetu plant predictions.
    """

    def __init__(self, model=None):
        self.model = model if model is not None else create_model()

        self.model.eval()

        # Load the authoritative 34-class registry
        self.registry = load_plant_registry()

        # Keep class order exactly consistent with plant-classes.json
        self.class_names = list(self.registry.keys())

    def predict(
        self,
        image_tensor: torch.Tensor,
        top_k: int = 3
    ) -> list:
        """
        Predict the top-k plants.

        Args:
            image_tensor:
                Tensor of shape [1, 3, 224, 224]

            top_k:
                Number of predictions to return.

        Returns:
            List of prediction dictionaries.
        """

        if image_tensor.ndim != 4:
            raise ValueError(
                "Expected image tensor with shape "
                "[batch, channels, height, width]"
            )

        if image_tensor.shape[1:] != (3, 224, 224):
            raise ValueError(
                "Expected image tensor shape "
                "[batch, 3, 224, 224]"
            )

        # Run model inference
        with torch.no_grad():
            logits = self.model(image_tensor)

        # Convert logits → probabilities
        probabilities = F.softmax(logits, dim=1)

        # Get top predictions
        k = min(top_k, len(self.class_names))

        scores, indices = torch.topk(
            probabilities,
            k=k,
            dim=1
        )

        predictions = []

        for score, index in zip(
            scores[0],
            indices[0]
        ):
            class_name = self.class_names[index.item()]
            plant = self.registry[class_name]

            predictions.append({
                "plant_id": plant["plant_id"],
                "plant_identifier": class_name,
                "common_name": plant["common_name"],
                "scientific_name": plant["scientific_name"],
                "confidence": float(score.item())
            })

        return predictions