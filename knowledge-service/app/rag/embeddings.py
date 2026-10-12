"""Embedding model wrapper for the DravyaSetu RAG layer."""
from functools import lru_cache

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
VECTOR_SIZE = 384


@lru_cache(maxsize=1)
def get_model():
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


def embed_texts(texts: list[str], batch_size: int = 64, show_progress: bool = False):
    """Return normalized 384-dim vectors (cosine-ready) as lists of floats."""
    return get_model().encode(
        texts,
        batch_size=batch_size,
        normalize_embeddings=True,
        show_progress_bar=show_progress,
    ).tolist()


def embed_query(query: str) -> list[float]:
    return embed_texts([query])[0]