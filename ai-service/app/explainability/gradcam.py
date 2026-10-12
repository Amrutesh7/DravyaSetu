
"""Grad-CAM visualization for the DravyaSetu plant classifier."""

from pathlib import Path
from typing import Any, Optional

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image

from app.inference.predictor import PlantPredictor


class GradCAM:
    """Generate Grad-CAM heatmaps for a PlantPredictor model."""

    def __init__(self, predictor: PlantPredictor):
        self.predictor = predictor
        self.model = predictor.model
        self.device = predictor.device

        # EfficientNetV2-S final convolutional feature block.
        self.target_layer = self.model.model.features[-1]

        self.activations = None
        self.gradients = None

        self._forward_handle = self.target_layer.register_forward_hook(
            self._save_activations
        )
        self._backward_handle = (
            self.target_layer.register_full_backward_hook(
                self._save_gradients
            )
        )

    def _save_activations(self, module, inputs, output):
        self.activations = output

    def _save_gradients(self, module, grad_input, grad_output):
        if grad_output and grad_output[0] is not None:
            self.gradients = grad_output[0]

    @staticmethod
    def _to_pil(image: Any) -> Image.Image:
        """Convert a PIL image or image path to RGB."""
        if isinstance(image, Image.Image):
            return image.convert("RGB")

        with Image.open(image) as opened:
            return opened.convert("RGB")

    def generate(
        self,
        image: Any,
        target_class_index: Optional[int] = None,
    ) -> dict:
        """
        Generate a heatmap and overlay.

        If target_class_index is not supplied, the model's top predicted
        class is used.
        """
        original = self._to_pil(image)

        tensor = self.predictor.preprocessor.preprocess(original)
        tensor = tensor.to(self.device)

        self.model.eval()
        self.model.zero_grad(set_to_none=True)

        self.activations = None
        self.gradients = None

        # Gradients must be enabled for Grad-CAM.
        with torch.enable_grad():
            logits = self.model(tensor)

            if logits.ndim != 2 or logits.shape[0] != 1:
                raise ValueError(
                    "Grad-CAM expects exactly one image per call."
                )

            if target_class_index is None:
                target_class_index = int(
                    torch.argmax(logits, dim=1).item()
                )

            if not 0 <= target_class_index < logits.shape[1]:
                raise ValueError(
                    f"Invalid target class index: {target_class_index}"
                )

            probabilities = torch.softmax(logits, dim=1)
            confidence = float(
                probabilities[0, target_class_index].detach().item()
            )

            score = logits[0, target_class_index]
            score.backward()

            if self.activations is None:
                raise RuntimeError(
                    "Grad-CAM did not capture layer activations."
                )

            if self.gradients is None:
                raise RuntimeError(
                    "Grad-CAM did not capture layer gradients."
                )

            # Average gradients spatially to obtain channel weights.
            weights = self.gradients.mean(
                dim=(2, 3), keepdim=True
            )

            # Weighted combination of feature maps.
            cam = (weights * self.activations).sum(
                dim=1, keepdim=True
            )

            # Retain positive contributions.
            cam = F.relu(cam)

            # Resize heatmap to the original image dimensions.
            cam = F.interpolate(
                cam,
                size=(original.height, original.width),
                mode="bilinear",
                align_corners=False,
            )

            cam = cam[0, 0].detach().cpu().numpy()

        # Normalize heatmap to [0, 1].
        cam_min = float(cam.min())
        cam_max = float(cam.max())

        if cam_max > cam_min:
            cam = (cam - cam_min) / (cam_max - cam_min)
        else:
            cam = np.zeros_like(cam, dtype=np.float32)

        heatmap_image = self._colorize(cam)

        # Blend the heatmap with the original image.
        overlay_image = Image.blend(
            original.convert("RGB"),
            heatmap_image.convert("RGB"),
            alpha=0.45,
        )

        return {
            "original_image": original,
            "heatmap_image": heatmap_image,
            "overlay_image": overlay_image,
            "target_class_index": int(target_class_index),
            "confidence": confidence,
            "method": "GRAD_CAM",
        }

    @staticmethod
    def _colorize(cam: np.ndarray) -> Image.Image:
        """Convert a normalized heatmap into a color image."""
        cam = np.clip(cam, 0.0, 1.0)

        # Lightweight blue-to-red heatmap without another dependency.
        red = np.clip(1.5 - np.abs(4.0 * cam - 3.0), 0.0, 1.0)
        green = np.clip(1.5 - np.abs(4.0 * cam - 2.0), 0.0, 1.0)
        blue = np.clip(1.5 - np.abs(4.0 * cam - 1.0), 0.0, 1.0)

        colored = np.stack([red, green, blue], axis=-1)
        colored = (colored * 255).astype(np.uint8)

        return Image.fromarray(colored, mode="RGB")

    @staticmethod
    def _safe_prefix(prefix: str) -> str:
        """Remove path separators and unsafe filename characters."""
        safe = "".join(
            character
            if character.isalnum() or character in ("-", "_")
            else "_"
            for character in prefix
        )
        return safe or "gradcam"

    def save(
        self,
        result: dict,
        output_dir: str | Path,
        prefix: str = "gradcam",
    ) -> dict:
        """Save original, heatmap, and overlay images."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        safe_prefix = self._safe_prefix(prefix)

        # Define every path before saving it.
        paths = {
            "original": output_dir / f"{safe_prefix}_original.jpg",
            "heatmap": output_dir / f"{safe_prefix}_heatmap.jpg",
            "overlay": output_dir / f"{safe_prefix}_overlay.jpg",
        }

        result["original_image"].convert("RGB").save(
            paths["original"], format="JPEG", quality=95
        )
        result["heatmap_image"].convert("RGB").save(
            paths["heatmap"], format="JPEG", quality=95
        )
        result["overlay_image"].convert("RGB").save(
            paths["overlay"], format="JPEG", quality=95
        )

        return {
            key: str(path.resolve())
            for key, path in paths.items()
        }

    def close(self):
        """Remove registered hooks when Grad-CAM is no longer needed."""
        if self._forward_handle is not None:
            self._forward_handle.remove()
            self._forward_handle = None

        if self._backward_handle is not None:
            self._backward_handle.remove()
            self._backward_handle = None


def build_gradcam(predictor: PlantPredictor) -> GradCAM:
    """Construct a Grad-CAM helper for an existing predictor."""
    return GradCAM(predictor)