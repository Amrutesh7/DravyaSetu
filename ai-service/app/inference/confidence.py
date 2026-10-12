"""
DravyaSetu AI Service
Confidence and probability utilities.

This module converts classifier logits into probabilities and
provides top-k predictions.

Important:
    Confidence here means the classifier's calibrated probability.
    It is NOT an unknown-detection score.
"""

from __future__ import annotations

from dataclasses import dataclass

import torch
from torch import Tensor


@dataclass
class Prediction:
    """
    Represents one classifier prediction.
    """

    class_index: int
    confidence: float


class ConfidenceCalculator:
    """
    Utilities for converting model logits into probabilities.
    """

    def __init__(
        self,
        temperature: float = 1.0,
    ) -> None:
        """
        Args:
            temperature:
                Temperature used for probability calibration.

                1.0 means no temperature adjustment.

                A value learned from validation data can later
                be supplied here for calibrated inference.
        """

        if temperature <= 0:
            raise ValueError(
                "temperature must be greater than 0."
            )

        self.temperature = float(temperature)

    def probabilities(
        self,
        logits: Tensor,
    ) -> Tensor:
        """
        Convert logits into probabilities using softmax.

        Args:
            logits:
                Tensor of shape [batch_size, num_classes]
                or [num_classes].

        Returns:
            Probability tensor with the same shape.
        """

        if not torch.is_floating_point(logits):
            logits = logits.float()

        return torch.softmax(
            logits / self.temperature,
            dim=-1,
        )

    def top_k(
        self,
        logits: Tensor,
        k: int = 5,
    ) -> list[list[Prediction]]:
        """
        Return top-k predictions for each image.

        Args:
            logits:
                Tensor of shape [batch_size, num_classes]
                or [num_classes].

            k:
                Number of predictions to return.

        Returns:
            List of predictions for each image.
        """

        if k <= 0:
            raise ValueError(
                "k must be greater than 0."
            )

        if logits.ndim == 1:
            logits = logits.unsqueeze(0)

        if logits.ndim != 2:
            raise ValueError(
                "logits must have shape "
                "[batch_size, num_classes]."
            )

        probabilities = self.probabilities(
            logits
        )

        k = min(
            k,
            probabilities.shape[-1],
        )

        values, indices = torch.topk(
            probabilities,
            k=k,
            dim=-1,
        )

        results: list[list[Prediction]] = []

        for row_values, row_indices in zip(
            values,
            indices,
        ):
            image_predictions: list[Prediction] = []

            for confidence, class_index in zip(
                row_values,
                row_indices,
            ):
                image_predictions.append(
                    Prediction(
                        class_index=int(
                            class_index.item()
                        ),
                        confidence=float(
                            confidence.item()
                        ),
                    )
                )

            results.append(
                image_predictions
            )

        return results

    def top_prediction(
        self,
        logits: Tensor,
    ) -> list[Prediction]:
        """
        Return the highest-probability prediction
        for each image.
        """

        predictions = self.top_k(
            logits,
            k=1,
        )

        return [
            image_predictions[0]
            for image_predictions in predictions
        ]


def build_confidence_calculator(
    temperature: float = 1.0,
) -> ConfidenceCalculator:
    """
    Factory function for creating the confidence calculator.
    """

    return ConfidenceCalculator(
        temperature=temperature
    )


if __name__ == "__main__":
    # CPU-only smoke test.
    # No model training and no GPU required.

    calculator = build_confidence_calculator()

    logits = torch.tensor(
        [
            [1.0, 3.0, 0.5, -1.0],
            [4.0, 1.0, 2.0, 0.0],
        ]
    )

    probabilities = calculator.probabilities(
        logits
    )

    top_predictions = calculator.top_k(
        logits,
        k=3,
    )

    best_predictions = calculator.top_prediction(
        logits
    )

    print("Confidence calculator test passed.")
    print(
        "Probability shape:",
        tuple(probabilities.shape),
    )

    print("\nProbabilities:")
    print(probabilities)

    print("\nTop-3 predictions:")

    for image_number, predictions in enumerate(
        top_predictions,
        start=1,
    ):
        print(f"Image {image_number}:")

        for prediction in predictions:
            print(
                f"  Class {prediction.class_index}: "
                f"{prediction.confidence:.4f}"
            )

    print("\nTop predictions:")

    for image_number, prediction in enumerate(
        best_predictions,
        start=1,
    ):
        print(
            f"Image {image_number}: "
            f"class={prediction.class_index}, "
            f"confidence={prediction.confidence:.4f}"
        )