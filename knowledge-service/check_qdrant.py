
from app.rag.retriever import get_client, COLLECTION

client = get_client()

print("Collection:", COLLECTION)

info = client.get_collection(COLLECTION)

print("Points count:", info.points_count)
print("Vectors count:", info.indexed_vectors_count)
print("Status:", info.status)
print("Vector configuration:", info.config.params.vectors)

client.close()
