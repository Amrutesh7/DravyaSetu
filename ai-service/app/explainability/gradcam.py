from pathlib import Path

import numpy as np
import torch
from PIL import Image


class GradCAM:
    """
    Grad-CAM explanation generator for the existing DravyaSetu
    PlantPredictor.

    Important:
    - Does NOT load another model.
    - Uses the exact model already loaded by PlantPredictor.
    - Uses the predictor's existing preprocessing transform.
    - Explains the selected/predicted class.
    """

    def __init__(self, predictor):

        self.predictor = predictor
        self.model = predictor.model
        self.device = predictor.device
        self.transform = predictor.transform

        # -----------------------------------------------------
        # EfficientNetV2-S target layer
        # -----------------------------------------------------

        self.target_layer = self._find_target_layer()

        self.activations = None
        self.gradients = None

        self.forward_handle = None
        self.backward_handle = None

        self._register_hooks()

    # =========================================================
    # TARGET LAYER
    # =========================================================

    def _find_target_layer(self):

        """
        Find the final convolutional feature block.

        Supports both:
            PlantPredictor -> EfficientNetV2
        and:
            PlantPredictor -> PlantClassifier -> EfficientNetV2
        """

        model = self.model

        # Case 1:
        # predictor.model is the actual EfficientNetV2 model
        if hasattr(model, "features"):

            return model.features[-1]

        # Case 2:
        # predictor.model is our PlantClassifier wrapper
        if hasattr(model, "model"):

            inner_model = model.model

            if hasattr(inner_model, "features"):

                return inner_model.features[-1]

        raise RuntimeError(
            "Unable to locate EfficientNetV2 feature layer "
            "for Grad-CAM."
        )

    # =========================================================
    # HOOKS
    # =========================================================

    def _register_hooks(self):

        def forward_hook(
            module,
            inputs,
            output
        ):

            self.activations = output

        def backward_hook(
            module,
            grad_input,
            grad_output
        ):

            self.gradients = grad_output[0]

        self.forward_handle = (
            self.target_layer.register_forward_hook(
                forward_hook
            )
        )

        self.backward_handle = (
            self.target_layer.register_full_backward_hook(
                backward_hook
            )
        )

    # =========================================================
    # GENERATE GRAD-CAM
    # =========================================================

    def generate(
        self,
        image_path,
        target_class_index=None,
    ):

        image_path = Path(image_path)

        if not image_path.exists():

            raise FileNotFoundError(
                f"Image not found: {image_path}"
            )

        # -----------------------------------------------------
        # Load original image
        # -----------------------------------------------------

        original_image = Image.open(
            image_path
        ).convert("RGB")

        # -----------------------------------------------------
        # Reset previous Grad-CAM state
        # -----------------------------------------------------

        self.activations = None
        self.gradients = None

        # -----------------------------------------------------
        # Preprocess
        # -----------------------------------------------------

        tensor = self.transform(
            original_image
        )

        tensor = tensor.unsqueeze(
            0
        ).to(
            self.device
        )

        # -----------------------------------------------------
        # IMPORTANT
        # Do NOT use torch.no_grad()
        # Grad-CAM requires gradients.
        # -----------------------------------------------------

        self.model.zero_grad(
            set_to_none=True
        )

        outputs = self.model(
            tensor
        )

        # -----------------------------------------------------
        # Probabilities
        # -----------------------------------------------------

        probabilities = torch.softmax(
            outputs,
            dim=1
        )

        # -----------------------------------------------------
        # Select target class
        # -----------------------------------------------------

        if target_class_index is None:

            target_class_index = int(
                probabilities.argmax(
                    dim=1
                ).item()
            )

        # Validate class index

        if (
            target_class_index < 0
            or
            target_class_index >= outputs.shape[1]
        ):

            raise ValueError(
                f"Invalid target class index: "
                f"{target_class_index}"
            )

        # -----------------------------------------------------
        # Target score
        # -----------------------------------------------------

        target_score = outputs[
            0,
            target_class_index
        ]

        # -----------------------------------------------------
        # Backpropagation
        # -----------------------------------------------------

        target_score.backward()

        # -----------------------------------------------------
        # Validate hooks
        # -----------------------------------------------------

        if self.activations is None:

            raise RuntimeError(
                "Grad-CAM activations were not captured."
            )

        if self.gradients is None:

            raise RuntimeError(
                "Grad-CAM gradients were not captured."
            )

        # -----------------------------------------------------
        # Remove batch dimension
        # -----------------------------------------------------

        activations = self.activations[
            0
        ]

        gradients = self.gradients[
            0
        ]

        # -----------------------------------------------------
        # Global average pooling of gradients
        # -----------------------------------------------------

        weights = gradients.mean(
            dim=(1, 2)
        )

        # -----------------------------------------------------
        # Weighted feature maps
        # -----------------------------------------------------

        cam = torch.sum(
            weights[:, None, None]
            *
            activations,
            dim=0
        )

        # -----------------------------------------------------
        # ReLU
        #
        # Keep only positive influence.
        # -----------------------------------------------------

        cam = torch.relu(
            cam
        )

        # -----------------------------------------------------
        # Normalize
        # -----------------------------------------------------

        cam_min = cam.min()
        cam_max = cam.max()

        if float(
            cam_max - cam_min
        ) > 1e-8:

            cam = (
                cam - cam_min
            ) / (
                cam_max - cam_min
            )

        else:

            cam = torch.zeros_like(
                cam
            )

        # -----------------------------------------------------
        # Convert to NumPy
        # -----------------------------------------------------

        heatmap = (
            cam
            .detach()
            .cpu()
            .numpy()
        )

        # -----------------------------------------------------
        # Resize to original image
        # -----------------------------------------------------

        original_width, original_height = (
            original_image.size
        )

        heatmap_image = Image.fromarray(
            np.uint8(
                heatmap * 255
            )
        )

        heatmap_image = heatmap_image.resize(
            (
                original_width,
                original_height
            ),
            Image.Resampling.BILINEAR
        )

        heatmap = (
            np.asarray(
                heatmap_image
            ).astype(
                np.float32
            ) / 255.0
        )

        # -----------------------------------------------------
        # RGB heatmap
        # -----------------------------------------------------

        heatmap_rgb = (
            self._create_heatmap_rgb(
                heatmap
            )
        )

        # -----------------------------------------------------
        # Original image → [0,1]
        # -----------------------------------------------------

        original_array = (
            np.asarray(
                original_image
            ).astype(
                np.float32
            ) / 255.0
        )

        # -----------------------------------------------------
        # Overlay
        # -----------------------------------------------------

        alpha = 0.45

        overlay = (
            (1.0 - alpha)
            * original_array
            +
            alpha
            * heatmap_rgb
        )

        overlay = np.clip(
            overlay,
            0.0,
            1.0
        )

        overlay_image = Image.fromarray(
            np.uint8(
                overlay * 255
            )
        )

        # -----------------------------------------------------
        # Target information
        # -----------------------------------------------------

        confidence = float(
            probabilities[
                0,
                target_class_index
            ].item()
        )

        class_name = self._get_class_name(
            target_class_index
        )

        return {
            "original_image": original_image,

            "heatmap_image": heatmap_image,

            "overlay_image": overlay_image,

            "target_class_index": int(
                target_class_index
            ),

            "target_class_name": class_name,

            "confidence": confidence,
        }

    # =========================================================
    # CLASS NAME
    # =========================================================

    def _get_class_name(
        self,
        class_index
    ):

        # Preferred structure
        if hasattr(
            self.predictor,
            "idx_to_class"
        ):

            return self.predictor.idx_to_class[
                class_index
            ]

        # Alternative structure
        if hasattr(
            self.predictor,
            "class_names"
        ):

            return self.predictor.class_names[
                class_index
            ]

        # Fallback
        return str(
            class_index
        )

    # =========================================================
    # HEATMAP COLORIZATION
    # =========================================================

    @staticmethod
    def _create_heatmap_rgb(
        heatmap
    ):

        """
        Convert grayscale CAM values into
        a blue → cyan → yellow → red heatmap.

        No plotting library required.
        """

        heatmap = np.clip(
            heatmap,
            0.0,
            1.0
        )

        red = np.clip(
            2.0 * heatmap,
            0.0,
            1.0
        )

        green = np.clip(
            2.0
            * (
                1.0
                -
                np.abs(
                    heatmap - 0.5
                )
                * 2.0
            ),
            0.0,
            1.0
        )

        blue = np.clip(
            1.0
            -
            2.0 * heatmap,
            0.0,
            1.0
        )

        return np.stack(
            [
                red,
                green,
                blue
            ],
            axis=-1
        )

    # =========================================================
    # SAVE RESULTS
    # =========================================================

    def save(
        self,
        result,
        output_dir,
        prefix="gradcam"
    ):

        output_dir = Path(
            output_dir
        )

        output_dir.mkdir(
            parents=True,
            exist_ok=True
        )

        original_path = (
            output_dir
            /
            f"{prefix}_original.jpg"
        )

        heatmap_path = (
            output_dir
            /
            f"{prefix}_heatmap.jpg"
        )

        overlay_path = (
            output_dir
            /
            f"{prefix}_overlay.jpg"
        )

        result[
            "original_image"
        ].save(
            original_path,
            quality=95
        )

        result[
            "heatmap_image"
        ].save(
            heatmap_path,
            quality=95
        )

        result[
            "overlay_image"
        ].save(
            overlay_path,
            quality=95
        )

        return {

            "original_path": str(
                original_path
            ),

            "heatmap_path": str(
                heatmap_path
            ),

            "overlay_path": str(
                overlay_path
            ),

            "method": "GRAD_CAM",

            "target_class_index": result[
                "target_class_index"
            ],

            "target_class_name": result[
                "target_class_name"
            ],

            "confidence": result[
                "confidence"
            ]
        }

    # =========================================================
    # CLEANUP
    # =========================================================

    def close(self):

        """
        Remove hooks when Grad-CAM is no longer needed.
        """

        if self.forward_handle is not None:

            self.forward_handle.remove()

            self.forward_handle = None

        if self.backward_handle is not None:

            self.backward_handle.remove()

            self.backward_handle = None