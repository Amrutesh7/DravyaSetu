"""
DravyaSetu AI Service
FAISS embedding similarity search.

This module provides nearest-neighbor search over plant
embeddings.

Production flow:

    Image
      ↓
    EfficientNetV2-S
      ↓
    1280-D embedding
      ↓
    FAISS
      ↓
    Similar plant examples

The current smoke test uses dummy embeddings only.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np


@dataclass
class SimilarityResult:
    """
    One FAISS similarity-search result.
    """

    index: int
    similarity: float


class FaissSearch:
    """
    FAISS cosine-similarity search.

    Embeddings are L2-normalized before being inserted into
    the index. Therefore, inner product is equivalent to
    cosine similarity.
    """

    def __init__(
        self,
        embedding_dimension: int = 1280,
    ) -> None:

        if embedding_dimension <= 0:
            raise ValueError(
                "embedding_dimension must be greater than 0."
            )

        import faiss

        self.embedding_dimension = (
            embedding_dimension
        )

        self.index = faiss.IndexFlatIP(
            embedding_dimension
        )

    def _prepare_embeddings(
        self,
        embeddings: np.ndarray,
    ) -> np.ndarray:
        """
        Validate and prepare embeddings for FAISS.
        """

        embeddings = np.asarray(
            embeddings,
            dtype=np.float32,
        )

        if embeddings.ndim == 1:
            embeddings = embeddings.reshape(
                1,
                -1,
            )

        if embeddings.ndim != 2:
            raise ValueError(
                "Embeddings must have shape "
                "[num_embeddings, dimension]."
            )

        if embeddings.shape[1] != (
            self.embedding_dimension
        ):
            raise ValueError(
                f"Expected embedding dimension "
                f"{self.embedding_dimension}, "
                f"got {embeddings.shape[1]}."
            )

        # L2 normalization makes inner product equivalent
        # to cosine similarity.
        import faiss

        faiss.normalize_L2(
            embeddings
        )

        return embeddings

    def add(
        self,
        embeddings: np.ndarray,
    ) -> None:
        """
        Add embeddings to the FAISS index.
        """

        prepared = self._prepare_embeddings(
            embeddings
        )

        self.index.add(prepared)

    def search(
        self,
        query_embedding: np.ndarray,
        k: int = 5,
    ) -> list[list[SimilarityResult]]:
        """
        Search for the k most similar embeddings.

        Args:
            query_embedding:
                One or more query embeddings.

            k:
                Number of nearest neighbors.

        Returns:
            Similarity results for each query.
        """

        if k <= 0:
            raise ValueError(
                "k must be greater than 0."
            )

        if self.index.ntotal == 0:
            raise RuntimeError(
                "FAISS index is empty."
            )

        prepared_query = self._prepare_embeddings(
            query_embedding
        )

        k = min(
            k,
            self.index.ntotal,
        )

        similarities, indices = (
            self.index.search(
                prepared_query,
                k,
            )
        )

        results: list[list[SimilarityResult]] = []

        for row_similarities, row_indices in zip(
            similarities,
            indices,
        ):
            query_results: list[
                SimilarityResult
            ] = []

            for similarity, index in zip(
                row_similarities,
                row_indices,
            ):
                query_results.append(
                    SimilarityResult(
                        index=int(index),
                        similarity=float(
                            similarity
                        ),
                    )
                )

            results.append(
                query_results
            )

        return results

    def save(
        self,
        path: str | Path,
    ) -> None:
        """
        Save the FAISS index to disk.
        """

        import faiss

        path = Path(path)

        path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        faiss.write_index(
            self.index,
            str(path),
        )

    @classmethod
    def load(
        cls,
        path: str | Path,
        embedding_dimension: int = 1280,
    ) -> "FaissSearch":
        """
        Load an existing FAISS index.
        """

        import faiss

        path = Path(path)

        if not path.exists():
            raise FileNotFoundError(
                f"FAISS index not found: {path}"
            )

        searcher = cls(
            embedding_dimension=
                embedding_dimension
        )

        searcher.index = faiss.read_index(
            str(path)
        )

        return searcher

    @property
    def size(self) -> int:
        """
        Number of embeddings currently stored.
        """

        return int(
            self.index.ntotal
        )


def build_faiss_search(
    embedding_dimension: int = 1280,
) -> FaissSearch:
    """
    Factory function.
    """

    return FaissSearch(
        embedding_dimension=
            embedding_dimension
    )


if __name__ == "__main__":
    # CPU-only smoke test.
    #
    # These are random vectors and have no biological meaning.

    rng = np.random.default_rng(
        seed=42
    )

    searcher = build_faiss_search(
        embedding_dimension=1280
    )

    database_embeddings = rng.normal(
        size=(10, 1280)
    ).astype(
        np.float32
    )

    query_embedding = rng.normal(
        size=(1, 1280)
    ).astype(
        np.float32
    )

    searcher.add(
        database_embeddings
    )

    results = searcher.search(
        query_embedding,
        k=3,
    )

    print("FAISS search test passed.")
    print(
        f"Index size: {searcher.size}"
    )

    print("\nTop-3 results:")

    for result in results[0]:
        print(
            f"Index {result.index}: "
            f"similarity={result.similarity:.4f}"
        )