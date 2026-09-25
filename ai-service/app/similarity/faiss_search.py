import numpy as np
import faiss


class FAISSSimilaritySearch:
    """
    FAISS-based similarity search for plant embeddings.

    The index stores normalized embedding vectors and
    uses cosine similarity through inner product.
    """

    def __init__(self, dimension: int = 1280):
        self.dimension = dimension

        # Inner product on normalized vectors = cosine similarity
        self.index = faiss.IndexFlatIP(dimension)

        # Metadata corresponding to each vector
        self.metadata = []

    def add_embeddings(
        self,
        embeddings: np.ndarray,
        metadata: list
    ):
        """
        Add plant embeddings to the FAISS index.

        Args:
            embeddings:
                Shape [N, 1280]

            metadata:
                List containing metadata for each image.
        """

        embeddings = np.asarray(
            embeddings,
            dtype=np.float32
        )

        if embeddings.ndim != 2:
            raise ValueError(
                "Embeddings must have shape [N, dimension]"
            )

        if embeddings.shape[1] != self.dimension:
            raise ValueError(
                f"Expected embedding dimension "
                f"{self.dimension}, got {embeddings.shape[1]}"
            )

        if len(metadata) != len(embeddings):
            raise ValueError(
                "Number of metadata entries must match "
                "number of embeddings"
            )

        # Normalize for cosine similarity
        faiss.normalize_L2(embeddings)

        self.index.add(embeddings)

        self.metadata.extend(metadata)

    def search(
        self,
        query_embedding: np.ndarray,
        top_k: int = 5
    ) -> list:
        """
        Search for visually similar plant images.

        Returns:
            List of similarity results.
        """

        query_embedding = np.asarray(
            query_embedding,
            dtype=np.float32
        )

        if query_embedding.ndim == 1:
            query_embedding = query_embedding.reshape(1, -1)

        if query_embedding.shape[1] != self.dimension:
            raise ValueError(
                f"Expected embedding dimension "
                f"{self.dimension}, got {query_embedding.shape[1]}"
            )

        if self.index.ntotal == 0:
            return []

        faiss.normalize_L2(query_embedding)

        k = min(top_k, self.index.ntotal)

        similarities, indices = self.index.search(
            query_embedding,
            k
        )

        results = []

        for similarity, index in zip(
            similarities[0],
            indices[0]
        ):
            if index < 0:
                continue

            results.append({
                "similarity": float(similarity),
                "metadata": self.metadata[index]
            })

        return results

    def size(self) -> int:
        """Return number of vectors stored in FAISS."""

        return self.index.ntotal