import numpy as np
import torch

from PIL import Image
from pytorch_grad_cam import GradCAM
from pytorch_grad_cam.utils.model_targets import ClassifierOutputTarget
from pytorch_grad_cam.utils.image import show_cam_on_image


class PlantGradCAM:
    """
    Grad-CAM explainability for EfficientNetV2-S.

    Generates a heatmap showing the image regions
    that contributed to the predicted class.
    """

    def __init__(self, model):
        self.model = model
        self.model.eval()

        # EfficientNetV2 feature extraction layer
        self.target_layers = [
            self.model.features[-1]
        ]

    def generate(
        self,
        image: Image.Image,
        input_tensor: torch.Tensor,
        target_class: int,
        output_path: str
    ) -> str:
        """
        Generate and save a Grad-CAM heatmap.

        Args:
            image:
                Original PIL image.

            input_tensor:
                Preprocessed image tensor.

            target_class:
                Predicted class index.

            output_path:
                Where to save the heatmap.

        Returns:
            Path to generated heatmap.
        """

        # Convert original image to RGB
        image = image.convert("RGB")

        # Resize for visualization
        visualization_image = image.resize(
            (224, 224)
        )

        # Convert to float [0, 1]
        rgb_image = np.array(
            visualization_image
        ).astype(np.float32) / 255.0

        # Grad-CAM
        cam = GradCAM(
            model=self.model,
            target_layers=self.target_layers
        )

        targets = [
            ClassifierOutputTarget(target_class)
        ]

        grayscale_cam = cam(
            input_tensor=input_tensor,
            targets=targets
        )[0]

        # Overlay heatmap on original image
        visualization = show_cam_on_image(
            rgb_image,
            grayscale_cam,
            use_rgb=True
        )

        result_image = Image.fromarray(
            visualization
        )

        result_image.save(output_path)

        return output_path