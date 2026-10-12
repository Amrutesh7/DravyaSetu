"""
DravyaSetu AI Service
EfficientNetV2-S embedding extraction.

This module extracts feature embeddings from the classifier.
The embeddings will later be used for:

- FAISS similarity search
- class prototypes
- unknown detection
- multi-image evidence fusion
"""

from __future__ import annotations

import torch
from torch import Tensor
import torch.nn.functional as F

from app.models.classifier import DravyaSetuClassifier


class EmbeddingExtractor:
    """
    Extract feature embeddings from EfficientNetV2-S.
    """

    def __init__(
        self,
        model: DravyaSetuClassifier,
        normalize: bool = True,
    ) -> None:
        self.model = model
        self.normalize = normalize

    @torch.no_grad()
    def extract(
        self,
        images: Tensor,
    ) -> Tensor:
        """
        Extract embeddings from a batch of images.

        Args:
            images:
                Tensor with shape:
                [batch_size, 3, 384, 384]

        Returns:
            Tensor with shape:
            [batch_size, 1280]
        """

        if images.ndim != 4:
            raise ValueError(
                "images must have shape "
                "[batch_size, channels, height, width]."
            )

        features = self.model.get_features(images)

        if self.normalize:
            features = F.normalize(
                features,
                p=2,
                dim=1,
            )

        return features

    @torch.no_grad()
    def extract_single(
        self,
        image: Tensor,
    ) -> Tensor:
        """
        Extract an embedding from one image.

        Args:
            image:
                Tensor with shape:
                [3, 384, 384]

        Returns:
            Tensor with shape:
            [1280]
        """

        if image.ndim != 3:
            raise ValueError(
                "image must have shape "
                "[channels, height, width]."
            )

        batch = image.unsqueeze(0)

        embedding = self.extract(batch)

        return embedding.squeeze(0)


def build_embedding_extractor(
    model: DravyaSetuClassifier,
    normalize: bool = True,
) -> EmbeddingExtractor:
    """
    Factory function for creating the embedding extractor.
    """

    return EmbeddingExtractor(
        model=model,
        normalize=normalize,
    )


if __name__ == "__main__":
    # CPU-only smoke test.
    #
    # This uses a randomly initialized model only to verify
    # the embedding pipeline.
    #
    # It does NOT produce meaningful plant embeddings.

    model = DravyaSetuClassifier(
        num_classes=34,
        pretrained=False,
    )

    extractor = build_embedding_extractor(
        model=model,
        normalize=True,
    )

    dummy_batch = torch.randn(
        2,
        3,
        384,
        384,
    )

    embeddings = extractor.extract(
        dummy_batch
    )

    single_embedding = extractor.extract_single(
        dummy_batch[0]
    )

    print("Embedding extractor test passed.")
    print(
        f"Batch embedding shape: "
        f"{tuple(embeddings.shape)}"
    )
    print(
        f"Single embedding shape: "
        f"{tuple(single_embedding.shape)}"
    )

    # Verify L2 normalization.
    norms = torch.linalg.vector_norm(
        embeddings,
        ord=2,
        dim=1,
    )

    print(
        "Embedding norms:",
        norms.tolist(),
    )