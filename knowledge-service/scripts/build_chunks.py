"""
Builds RAG chunks for all plants.

Run from knowledge-service/:
  python scripts/build_chunks.py

Reads (never modifies):
  knowledge-base/cleaned/<single-plant files>   -> via the confirmed FILE_MAP below
  knowledge-base/by_plant/PLANT_xxx_*.txt       -> sections from the combined PDFs
Writes:
  knowledge-base/chunks/PLANT_xxx_<Identifier>.jsonl   (one JSON chunk per line)
  knowledge-base/chunks/GENERAL.jsonl                  (WHO general info, no plant)
"""
import json
import re
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE))
from app.rag.chunker import chunk_text  # noqa: E402
from text_fixes import STATS, clean_common, fix_sanskrit, leftover_chars  # noqa: E402

CLEANED = BASE / "knowledge-base" / "cleaned"
BY_PLANT = BASE / "knowledge-base" / "by_plant"
CHUNKS = BASE / "knowledge-base" / "chunks"
CLASSES = BASE.parent / "shared" / "constants" / "plant-classes.json"

CHUNK_SIZE = 800
OVERLAP = 100
MIN_CHARS = 40  # drop tiny fragments

# Confirmed mapping: single-plant cleaned file -> plant id ("GENERAL" = no plant)
FILE_MAP = {
    "Aloe_Vera_WHO_Extract.txt": "PLANT_001",
    "Amla.txt": "PLANT_002",
    "Amruthballi.txt": "PLANT_003",
    "Ashoka.txt": "PLANT_005",
    "Ashwagandha_01.txt": "PLANT_006",
    "Betel.txt": "PLANT_007",
    "Betel_NUt-1.txt": "PLANT_008",
    "Betel_NUt-2.txt": "PLANT_008",
    "Brahmi.txt": "PLANT_009",
    "Castor.txt": "PLANT_010",
    "cURRY_lEAF.txt": "PLANT_011",
    "Hibiscus.txt": "PLANT_016",
    "Insulin.txt": "PLANT_018",
    "Lemon.txt": "PLANT_020",
    "Lemon-Grass.txt": "PLANT_021",
    "Nagadalli.txt": "PLANT_024",
    "neem.txt": "PLANT_025",
    "nitya_pushpa.txt": "PLANT_026",
    "Nooni.txt": "PLANT_027",
    "Papaya.txt": "PLANT_028",
    "Pomogranate.txt": "PLANT_030",
    "Rose2.txt": "PLANT_032",
    "Tulasi.txt": "PLANT_033",
    "WHO General info.txt": "GENERAL",
}
COMBINED_SOURCES = {  # handled through by_plant/, not FILE_MAP
    "Ayurvedic Pharmacopoeia of India All Volume-Remaining Plants.txt",
    "Betel, Castor, Ekka, Jasmine, Mango, Pepper, Raktachandini and Wood_sorel..txt",
}

SECTION_HEADER = re.compile(r"^----- SOURCE: (.*?) \| SECTION: (.*?) -----$", re.M)


def read(path):
    return Path(path).read_text(encoding="utf-8", errors="replace")


def main():
    classes = json.loads(CLASSES.read_text(encoding="utf-8"))["classes"]
    ident = {pid: c["plant_identifier"] for pid, c in classes.items()}
    ident["GENERAL"] = "General"

    # pid -> list of (source_file, section_label, text)
    docs = {pid: [] for pid in ident}

    # 1) single-plant cleaned files
    for fname, pid in FILE_MAP.items():
        path = CLEANED / fname
        if not path.exists():
            print(f"WARNING missing cleaned file: {fname}")
            continue
        if pid not in ident:
            print(f"WARNING unknown plant id {pid} for {fname}")
            continue
        docs[pid].append((fname, "full text", clean_common(read(path))))

    # 2) sections from the combined PDFs (Sanskrit font characters are fixed here)
    combined_texts = []
    for path in sorted(BY_PLANT.glob("PLANT_*.txt")):
        pid = path.name[:9]
        if pid not in ident:
            print(f"WARNING unknown plant id in {path.name}")
            continue
        text = read(path)
        heads = list(SECTION_HEADER.finditer(text))
        for i, m in enumerate(heads):
            end = heads[i + 1].start() if i + 1 < len(heads) else len(text)
            body = text[m.end():end]
            if m.group(1) in COMBINED_SOURCES:
                body = fix_sanskrit(body)
                combined_texts.append(body)
            docs[pid].append((m.group(1), m.group(2), clean_common(body)))

    # warn about cleaned files that nothing uses
    known = set(FILE_MAP) | COMBINED_SOURCES
    for p in CLEANED.glob("*.txt"):
        if p.name not in known:
            print(f"WARNING cleaned file not mapped to any plant: {p.name}")

    # 3) chunk and write
    CHUNKS.mkdir(parents=True, exist_ok=True)
    print(f"\n{'ID':<10} {'Identifier':<15} {'sources':>7} {'chunks':>7}")
    total = 0
    for pid, items in docs.items():
        if not items:
            print(f"{pid:<10} {ident[pid]:<15} {0:>7} {0:>7}   <-- NO SOURCE")
            continue
        name = "GENERAL" if pid == "GENERAL" else f"{pid}_{ident[pid]}"
        out_path = CHUNKS / f"{name}.jsonl"
        n = 0
        with out_path.open("w", encoding="utf-8") as f:
            for src, section, text in items:
                for idx, chunk in enumerate(chunk_text(text, CHUNK_SIZE, OVERLAP)):
                    if len(chunk) < MIN_CHARS:
                        continue
                    rec = {
                        "chunk_id": f"{pid}-{n:04d}",
                        "plant_id": None if pid == "GENERAL" else pid,
                        "plant_identifier": None if pid == "GENERAL" else ident[pid],
                        "source_file": src,
                        "section": section,
                        "section_chunk_index": idx,
                        "text": chunk,
                    }
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                    n += 1
        total += n
        print(f"{pid:<10} {ident[pid]:<15} {len(items):>7} {n:>7}")

    print(f"\nTotal chunks: {total}")
    print(f"Output folder: {CHUNKS}")
    print(f"Corrupt lines dropped (Devanagari / replacement chars): {STATS['corrupt_lines_dropped']}")
    left = leftover_chars(combined_texts)
    if left:
        print("\nNon-ASCII characters still left in the combined sources (check these):")
        for ch, code, name, n in left:
            print(f"  {ch}  {code}  {name}  x{n}")
    else:
        print("\nNo unmapped special characters left in the combined sources.")


if __name__ == "__main__":
    main()