"""Qdrant retriever for the DravyaSetu RAG layer.

Qdrant location:
  - env QDRANT_URL set (e.g. http://localhost:6333)  -> Qdrant server (Docker)
  - otherwise                                        -> local folder
    knowledge-base/qdrant_data (no server needed)

search() filtering:
  plant_id=None         -> all 34 plants, GENERAL (WHO general info) excluded
  plant_id="PLANT_023"  -> only that plant
  plant_id="GENERAL"    -> only the WHO general info

Quick test (from knowledge-service/):
  python -m app.rag.retriever "uses of pudina" --plant PLANT_023
"""
import argparse
import os
from functools import lru_cache
from pathlib import Path

from qdrant_client import QdrantClient
from qdrant_client.models import FieldCondition, Filter, MatchValue

from app.rag.embeddings import embed_query

BASE = Path(__file__).resolve().parents[2]
COLLECTION = "dravyasetu_plants"
LOCAL_PATH = BASE / "knowledge-base" / "qdrant_data"


@lru_cache(maxsize=1)
def get_client() -> QdrantClient:
    url = os.getenv("QDRANT_URL")
    if url:
        return QdrantClient(url=url)
    return QdrantClient(path=str(LOCAL_PATH))


def search(query: str, plant_id: str | None = None, top_k: int = 5) -> list[dict]:
    cond = FieldCondition(key="plant_id", match=MatchValue(value=plant_id or "GENERAL"))
    flt = Filter(must=[cond]) if plant_id else Filter(must_not=[cond])

    res = get_client().query_points(
        collection_name=COLLECTION,
        query=embed_query(query),
        query_filter=flt,
        limit=top_k,
        with_payload=True,
    )
    return [{"score": round(p.score, 4), **p.payload} for p in res.points]


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("query")
    ap.add_argument("--plant", default=None, help="PLANT_xxx or GENERAL")
    ap.add_argument("--k", type=int, default=3)
    args = ap.parse_args()

    for r in search(args.query, args.plant, args.k):
        print(f"\n[{r['score']}] {r['plant_id']} | {r['source_file']} | {r['section']}")
        print(r["text"][:300].replace("\n", " "))