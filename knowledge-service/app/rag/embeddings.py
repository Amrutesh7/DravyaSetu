"""Lightweight ONNX embedding model wrapper for DravyaSetu (Render 512MB safe)."""
import logging
import os
import time
from functools import lru_cache

os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

logger = logging.getLogger("dravyasetu.embeddings")

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
VECTOR_SIZE = 384


@lru_cache(maxsize=1)
def get_model():
    from fastembed import TextEmbedding

    t0 = time.perf_counter()
    logger.info("Loading ONNX embedding model: %s", MODEL_NAME)
    model = TextEmbedding(model_name=MODEL_NAME, threads=1)
    logger.info("Embedding model loaded in %.2fs", time.perf_counter() - t0)
    return model


def embed_texts(texts: list[str], batch_size: int = 1, show_progress: bool = False) -> list[list[float]]:
    t0 = time.perf_counter()
    logger.info("Embedding generation started (count=%d)", len(texts))
    vectors = [vec.tolist() for vec in get_model().embed(texts, batch_size=batch_size)]
    logger.info("Embedding generation completed in %.2fs", time.perf_counter() - t0)
    return vectors


def embed_query(query: str) -> list[float]:
    return embed_texts([query], batch_size=1)[0]