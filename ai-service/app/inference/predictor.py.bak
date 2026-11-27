
"""DravyaSetu trained plant prediction pipeline."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Union

import torch
from PIL import Image

from app.models.classifier import DravyaSetuClassifier
from app.preprocessing.image_preprocessor import ImagePreprocessor
from app.inference.confidence import ConfidenceCalculator


ImageInput = Union[str, Path, Image.Image]

# Project paths
PROJECT_ROOT = Path(__file__).resolve().parents[2]
MODEL_DIR = PROJECT_ROOT / "artifacts" / "models"
WEIGHTS_PATH = MODEL_DIR / "efficientnet_v2_s.pth"
MAPPING_PATH = MODEL_DIR / "class_mapping.json"


@dataclass
class NamedPrediction:
    """Prediction for one plant class."""

    class_index: int
    class_name: str
    confidence: float


@dataclass
class PredictionResult:
    """Top prediction and alternative predictions."""

    top_prediction: NamedPrediction
    alternatives: list[NamedPrediction]


class PlantPredictor:
    """Predict plant classes using the trained DravyaSetu model."""

    def __init__(
        self,
        model: DravyaSetuClassifier,
        preprocessor: ImagePreprocessor,
        confidence_calculator: ConfidenceCalculator,
        class_names: list[str],
        device: str | torch.device = "cpu",
    ) -> None:
        if len(class_names) != model.num_classes:
            raise ValueError(
                "Class mapping count does not match model output count."
            )

        self.model = model
        self.preprocessor = preprocessor
        self.confidence_calculator = confidence_calculator
        self.class_names = class_names
        self.device = torch.device(device)

        self.model.to(self.device)
        self.model.eval()

    @torch.no_grad()
    def predict(
        self,
        image: ImageInput,
        top_k: int = 5,
    ) -> PredictionResult:
        """Predict the most likely plant classes for an image."""

        if top_k <= 0:
            raise ValueError("top_k must be greater than 0.")

        # Convert the input image into a normalized model tensor.
        tensor = self.preprocessor.preprocess(image).to(self.device)

        # Run the trained classifier.
        logits = self.model(tensor)

        # Calculate the top-k class predictions.
        predictions = self.confidence_calculator.top_k(
            logits,
            k=min(top_k, len(self.class_names)),
        )[0]

        named_predictions = [
            NamedPrediction(
                class_index=prediction.class_index,
                class_name=self.class_names[prediction.class_index],
                confidence=prediction.confidence,
            )
            for prediction in predictions
        ]

        return PredictionResult(
            top_prediction=named_predictions[0],
            alternatives=named_predictions,
        )


def build_predictor(
    num_classes: int = 34,
    pretrained: bool = False,
    device: str = "cpu",
) -> PlantPredictor:
    """Load the trained checkpoint and construct a plant predictor."""

    if not WEIGHTS_PATH.is_file():
        raise FileNotFoundError(
            f"Trained model checkpoint not found: {WEIGHTS_PATH}"
        )

    if not MAPPING_PATH.is_file():
        raise FileNotFoundError(
            f"Class mapping file not found: {MAPPING_PATH}"
        )

    # Load class mapping.
    with MAPPING_PATH.open("r", encoding="utf-8") as file:
        mapping = json.load(file)

    try:
        class_names = [mapping[str(index)] for index in range(num_classes)]
    except KeyError as exc:
        raise ValueError(
            f"Class mapping is missing index {exc}."
        ) from exc

    # Build the same model architecture used for training.
    model = DravyaSetuClassifier(
        num_classes=num_classes,
        pretrained=pretrained,
    )

    # Load the trained weights.
    state_dict = torch.load(
        WEIGHTS_PATH,
        map_location="cpu",
        weights_only=True,
    )

    model.model.load_state_dict(state_dict, strict=True)

    # Create preprocessing and confidence components.
    preprocessor = ImagePreprocessor(image_size=300)
    confidence_calculator = ConfidenceCalculator(temperature=1.0)

    return PlantPredictor(
        model=model,
        preprocessor=preprocessor,
        confidence_calculator=confidence_calculator,
        class_names=class_names,
        device=device,
    )


if __name__ == "__main__":
    predictor = build_predictor()

    print("DravyaSetu predictor loaded successfully.")
    print("Number of classes:", len(predictor.class_names))
    print("Device:", predictor.device)
    print("Model checkpoint:", WEIGHTS_PATH)