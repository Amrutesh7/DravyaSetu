"""Create/refresh knowledge-base/sources_registry.json from the chunk files.
Run from knowledge-service/:   python -m scripts.make_sources_registry
Existing entries are never overwritten, so your edits are safe."""
import json
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
CHUNKS = BASE / "knowledge-base" / "chunks"
OUT = BASE / "knowledge-base" / "sources_registry.json"

# Prefilled ONLY from what the documents themselves state. Verify before release.
KNOWN = {
    "WHO General info": {
        "title": "WHO Monographs on Selected Medicinal Plants, Volume 1",
        "reference": "World Health Organization, Geneva, 1999. ISBN 92 4 154517 8",
    },
    "Aloe_Vera_WHO_Extract": {
        "title": "WHO Monographs on Selected Medicinal Plants, Volume 1 (Aloe monographs)",
        "reference": "World Health Organization, Geneva, 1999. ISBN 92 4 154517 8",
    },
    "Ayurvedic Pharmacopoeia of India All Volume-Remaining Plants": {
        "title": "The Ayurvedic Pharmacopoeia of India",
        "reference": "Government of India (Ministry of AYUSH) - part/volume and year TO BE CONFIRMED",
    },
}

reg = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
stems = set()
for f in CHUNKS.glob("*.jsonl"):
    for line in f.read_text(encoding="utf-8").splitlines():
        if line.strip():
            sf = json.loads(line)["source_file"]
            stems.add(sf[:-4] if sf.lower().endswith(".txt") else sf)

n = max([int(v["source_id"].split("-")[1]) for v in reg.values()] + [0])
for stem in sorted(stems):
    if stem in reg:
        continue
    n += 1
    k = KNOWN.get(stem, {})
    reg[stem] = {"source_id": f"SRC-{n:03d}", "title": k.get("title", ""),
                 "reference": k.get("reference", ""), "verified": False}

OUT.write_text(json.dumps(reg, indent=2, ensure_ascii=False), encoding="utf-8")
todo = [s for s, v in reg.items() if not v["title"] or not v["verified"]]
print(f"{len(reg)} sources written to {OUT}\nNeed title/reference/verified=true: {len(todo)}")
for s in todo:
    print("  -", s)