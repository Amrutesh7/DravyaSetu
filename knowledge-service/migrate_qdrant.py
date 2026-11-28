
import os
from qdrant_client import QdrantClient, models

COLLECTION = "dravyasetu_plants"
LOCAL_PATH = "knowledge-base/qdrant_data"
BATCH_SIZE = 100

url = os.getenv("QDRANT_URL")
api_key = os.getenv("QDRANT_API_KEY")

if not url or not api_key:
    raise RuntimeError("QDRANT_URL and QDRANT_API_KEY must be set.")

local = QdrantClient(path=LOCAL_PATH)
cloud = QdrantClient(url=url, api_key=api_key)

try:
    info = local.get_collection(COLLECTION)

    existing = [c.name for c in cloud.get_collections().collections]
    if COLLECTION in existing:
        raise RuntimeError(
            f"Cloud collection {COLLECTION} already exists. "
            "Stopping to avoid overwriting data."
        )

    vectors = info.config.params.vectors
    sparse = getattr(info.config.params, "sparse_vectors", None)

    cloud.create_collection(
        collection_name=COLLECTION,
        vectors_config=vectors,
        sparse_vectors_config=sparse,
    )

    print("Local collection points:", info.points_count)

    offset = None
    migrated = 0

    while True:
        points, offset = local.scroll(
            collection_name=COLLECTION,
            limit=BATCH_SIZE,
            offset=offset,
            with_payload=True,
            with_vectors=True,
        )

        if not points:
            break

        cloud.upsert(
            collection_name=COLLECTION,
            points=[
                models.PointStruct(
                    id=p.id,
                    vector=p.vector,
                    payload=p.payload,
                )
                for p in points
            ],
            wait=True,
        )

        migrated += len(points)
        print(f"Migrated: {migrated}")

        if offset is None:
            break

    cloud_count = cloud.count(
        collection_name=COLLECTION,
        exact=True,
    ).count

    print("Migration finished.")
    print("Local points:", info.points_count)
    print("Cloud points:", cloud_count)

    if cloud_count != info.points_count:
        raise RuntimeError("Point counts do not match. Verify migration.")

finally:
    local.close()
    cloud.close()
