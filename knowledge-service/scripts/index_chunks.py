"""
Embeds all chunks and loads them into Qdrant (collection is rebuilt each run).

Run from knowledge-service/:
  python scripts/index_chunks.py

Each plant chunk is embedded with a short header (common name, regional names,
scientific name from plant-classes.json) so queries by regional name still
find it, even when the OCR'd text has garbled Sanskrit names. The stored
payload text is the original chunk, without the header.
"""
import json
import sys
import uuid
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE))

from qdrant_client.models import Distance, PointStruct, VectorParams  # noqa: E402

from app.rag.embeddings import VECTOR_SIZE, embed_texts  # noqa: E402
from app.rag.retriever import COLLECTION, get_client  # noqa: E402

CHUNKS = BASE / "knowledge-base" / "chunks"
CLASSES = BASE.parent / "shared" / "constants" / "plant-classes.json"
BATCH = 64


def header(c):
    return (
        f"Plant: {c['common_name']} ({c['plant_identifier']}). "
        f"Also known as: {', '.join(c['ayurvedic_regional_names'])}. "
        f"Scientific name: {c['scientific_name']}.\n"
    )


def main():
    classes = json.loads(CLASSES.read_text(encoding="utf-8"))["classes"]

    records = []
    for path in sorted(CHUNKS.glob("*.jsonl")):
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                records.append(json.loads(line))
    print(f"Loaded {len(records)} chunks from {CHUNKS}")

    client = get_client()
    if client.collection_exists(COLLECTION):
        client.delete_collection(COLLECTION)
    client.create_collection(
        COLLECTION,
        vectors_config=VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE),
    )

    done = 0
    for i in range(0, len(records), BATCH):
        batch = records[i : i + BATCH]
        texts = [
            (header(classes[r["plant_id"]]) if r["plant_id"] else "") + r["text"]
            for r in batch
        ]
        vectors = embed_texts(texts)
        points = []
        for r, vec in zip(batch, vectors):
            payload = dict(r)
            payload["plant_id"] = r["plant_id"] or "GENERAL"
            points.append(
                PointStruct(
                    id=str(uuid.uuid5(uuid.NAMESPACE_URL, r["chunk_id"])),
                    vector=vec,
                    payload=payload,
                )
            )
        client.upsert(COLLECTION, points=points)
        done += len(batch)
        print(f"  indexed {done}/{len(records)}")

    print(f"\nPoints in Qdrant: {client.count(COLLECTION).count}")


if __name__ == "__main__":
    main()