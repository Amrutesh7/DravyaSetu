"""Grad-CAM visualization for the DravyaSetu plant classifier."""

import logging
import sys
from pathlib import Path
from typing import Any, Optional
from uuid import uuid4

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image

from app.inference.predictor import PlantPredictor

logger = logging.getLogger(__name__)


class GradCAM:
    """Generate memory-conscious Grad-CAM heatmaps."""

    MAX_VISUALIZATION_SIDE = 1024

    def __init__(self, predictor: PlantPredictor):
        self.predictor = predictor
        self.model = predictor.model
        self.device = predictor.device

        self.target_layer = self.model.model.features[-1]

        self.activations = None
        self.gradients = None

        # Share the model lock with regular inference to protect hook state.
        self._lock = predictor.inference_lock

        self._forward_handle = self.target_layer.register_forward_hook(
            self._save_activations
        )
        self._backward_handle = (
            self.target_layer.register_full_backward_hook(
                self._save_gradients
            )
        )

    def _save_activations(self, module, inputs, output):
        self.activations = output.detach()

    def _save_gradients(self, module, grad_input, grad_output):
        if grad_output and grad_output[0] is not None:
            self.gradients = grad_output[0].detach()

    @staticmethod
    def _to_pil(image: Any) -> Image.Image:
        """Convert a PIL image or image path to RGB."""
        if isinstance(image, Image.Image):
            return image if image.mode == "RGB" else image.convert("RGB")

        with Image.open(image) as opened:
            return opened.convert("RGB")

    @classmethod
    def _resize_for_visualization(cls, image: Image.Image) -> Image.Image:
        """Limit visualization dimensions while preserving aspect ratio."""
        scale = min(
            1.0,
            cls.MAX_VISUALIZATION_SIDE / max(image.size),
        )
        if scale == 1.0:
            return image

        size = (
            max(1, round(image.width * scale)),
            max(1, round(image.height * scale)),
        )
        return image.resize(size, Image.Resampling.LANCZOS)

    def generate(
        self,
        image: Any,
        target_class_index: Optional[int] = None,
    ) -> dict:
        """Generate a heatmap with guaranteed tensor cleanup."""
        with self._lock:
            return self._generate_locked(image, target_class_index)

    def _generate_locked(
        self,
        image: Any,
        target_class_index: Optional[int] = None,
    ) -> dict:
        original = self._to_pil(image)
        visualization_original = self._resize_for_visualization(original)

        tensor = None
        logits = None
        probabilities = None
        score = None
        weights = None
        cam = None

        self.activations = None
        self.gradients = None

        try:
            tensor = self.predictor.preprocessor.preprocess(original)
            tensor = tensor.to(self.device)

            self.model.eval()
            self.model.zero_grad(set_to_none=True)

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

                weights = self.gradients.mean(
                    dim=(2, 3), keepdim=True
                )

                cam = (weights * self.activations).sum(
                    dim=1, keepdim=True
                )

                cam = F.relu(cam)

                # Resize only to visualization dimensions, not full upload size.
                cam = F.interpolate(
                    cam,
                    size=(
                        visualization_original.height,
                        visualization_original.width,
                    ),
                    mode="bilinear",
                    align_corners=False,
                )

                cam = cam[0, 0].detach().cpu().numpy()

            cam_min = float(cam.min())
            cam_max = float(cam.max())

            if cam_max > cam_min:
                cam = (cam - cam_min) / (cam_max - cam_min)
            else:
                cam = np.zeros_like(cam, dtype=np.float32)

            heatmap_image = self._colorize(cam)

            overlay_image = Image.blend(
                visualization_original,
                heatmap_image,
                alpha=0.45,
            )

            return {
                "original_image": visualization_original,
                "heatmap_image": heatmap_image,
                "overlay_image": overlay_image,
                "target_class_index": int(target_class_index),
                "confidence": confidence,
                "method": "GRAD_CAM",
            }

        finally:
            exception_in_flight = sys.exc_info()[0] is not None
            # Release references to intermediate tensors and their graphs.
            self.activations = None
            self.gradients = None

            cleanup_error = None
            try:
                self.model.zero_grad(set_to_none=True)
            except Exception as exc:
                logger.exception("Failed to clear Grad-CAM model gradients.")
                if not exception_in_flight:
                    cleanup_error = exc

            del tensor
            del logits
            del probabilities
            del score
            del weights
            del cam

            if cleanup_error is not None:
                raise cleanup_error

    @staticmethod
    def _colorize(cam: np.ndarray) -> Image.Image:
        """Convert a normalized heatmap into a color image."""
        cam = np.clip(cam, 0.0, 1.0)

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
            if character.isascii()
            and (character.isalnum() or character in ("-", "_"))
            else "_"
            for character in prefix
        )
        return (safe[:80] or "gradcam")

    def save(
        self,
        result: dict,
        output_dir: str | Path,
        prefix: str = "gradcam",
    ) -> dict:
        """Save original, heatmap, and overlay images."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        images = {
            "original": result["original_image"],
            "heatmap": result["heatmap_image"],
            "overlay": result["overlay_image"],
        }

        for _ in range(8):
            safe_prefix = (
                f"{self._safe_prefix(prefix)[:80]}_{uuid4().hex}"
            )
            paths = {
                key: output_dir / f"{safe_prefix}_{key}.jpg"
                for key in images
            }
            handles = {}
            created_paths = []

            try:
                for key, path in paths.items():
                    try:
                        handles[key] = path.open("xb")
                    except FileExistsError:
                        self._close_artifact_handles(handles)
                        self._remove_partial_artifacts(created_paths)
                        break
                    created_paths.append(path)
                else:
                    for key, image in images.items():
                        image.convert("RGB").save(
                            handles[key],
                            format="JPEG",
                            quality=90,
                        )

                    for handle in handles.values():
                        handle.close()

                    return {
                        key: str(path.resolve())
                        for key, path in paths.items()
                    }

            except Exception:
                self._close_artifact_handles(handles)
                self._remove_partial_artifacts(created_paths)
                raise

        raise FileExistsError(
            "Unable to allocate unique Grad-CAM artifact filenames."
        )

    @staticmethod
    def _close_artifact_handles(handles) -> None:
        for handle in handles.values():
            if handle.closed:
                continue
            try:
                handle.close()
            except OSError:
                logger.exception("Failed to close a Grad-CAM artifact file.")

    @staticmethod
    def _remove_partial_artifacts(paths) -> None:
        for path in paths:
            try:
                path.unlink()
            except FileNotFoundError:
                pass
            except OSError:
                logger.exception(
                    "Failed to remove partial Grad-CAM artifact %s.",
                    path,
                )

    def close(self):
        """Remove registered hooks and release retained state."""
        with self._lock:
            self.activations = None
            self.gradients = None

            for name in ("_forward_handle", "_backward_handle"):
                handle = getattr(self, name)
                if handle is None:
                    continue
                try:
                    handle.remove()
                except Exception:
                    logger.exception("Failed to remove a Grad-CAM hook.")
                finally:
                    setattr(self, name, None)


def build_gradcam(predictor: PlantPredictor) -> GradCAM:
    """Construct a Grad-CAM helper for an existing predictor."""
    return GradCAM(predictor)
