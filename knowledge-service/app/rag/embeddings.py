
"""Lightweight embedding model wrapper for DravyaSetu."""
import os
from functools import lru_cache

# Set before loading Sentence Transformers / PyTorch.
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
VECTOR_SIZE = 384


@lru_cache(maxsize=1)
def get_model():
    import torch
    from sentence_transformers import SentenceTransformer

    torch.set_num_threads(1)

    return SentenceTransformer(
        MODEL_NAME,
        device="cpu"
    )


def embed_texts(texts: list[str], batch_size: int = 1, show_progress: bool = False):
    return get_model().encode(
        texts,
        batch_size=batch_size,
        normalize_embeddings=True,
        show_progress_bar=show_progress,
    ).tolist()


def embed_query(query: str) -> list[float]:
    return embed_texts([query], batch_size=1)[0]
