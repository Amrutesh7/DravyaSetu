"""
Step 2 of combined-PDF handling.

Run from the knowledge-service folder:
  python scripts/map_sections_to_plants.py propose   # suggest plant_id per section
  python scripts/map_sections_to_plants.py apply     # merge reviewed sections per plant

propose: reads section_mapping_review.json, looks for each plant's scientific
         name in the first part of every section, writes
         mapped/section_mapping_review.csv (edit this in Excel).
         Will not overwrite an existing CSV unless you add --force.
apply:   reads the CSV and writes knowledge-base/by_plant/PLANT_xxx_Identifier.txt.
         Rows with an empty plant_id are skipped.
Original cleaned/mapped section files are never modified.
"""
import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
MAPPED = BASE / "knowledge-base" / "mapped"
JSON_PATH = MAPPED / "section_mapping_review.json"
CSV_PATH = MAPPED / "section_mapping_review.csv"
OUT_DIR = BASE / "knowledge-base" / "by_plant"
SCAN_CHARS = 800  # the "X consists of ... <Latin name>" line is near the top

# Official mapping: plant_id -> (identifier, [scientific names to look for])
# Extra names (synonyms) are only used to SUGGEST a match; you review before apply.
PLANTS = {
    "PLANT_001": ("Aloevera", ["Aloe vera", "Aloe barbadensis"]),
    "PLANT_002": ("Amla", ["Phyllanthus emblica", "Emblica officinalis"]),
    "PLANT_003": ("Amruta_Balli", ["Tinospora cordifolia"]),
    "PLANT_004": ("Arali", ["Ficus religiosa"]),
    "PLANT_005": ("Ashoka", ["Saraca asoca", "Saraca indica"]),
    "PLANT_006": ("Ashwagandha", ["Withania somnifera"]),
    "PLANT_007": ("Betel", ["Piper betle"]),
    "PLANT_008": ("Betel_Nut", ["Areca catechu"]),
    "PLANT_009": ("Brahmi", ["Bacopa monnieri", "Bacopa monniera"]),
    "PLANT_010": ("Castor", ["Ricinus communis"]),
    "PLANT_011": ("Curry_Leaf", ["Murraya koenigii"]),
    "PLANT_012": ("Doddapatre", ["Plectranthus amboinicus", "Coleus amboinicus"]),
    "PLANT_013": ("Ekka", ["Calotropis gigantea", "Calotropis procera"]),
    "PLANT_014": ("Ganike", ["Solanum nigrum"]),
    "PLANT_015": ("Henna", ["Lawsonia inermis"]),
    "PLANT_016": ("Hibiscus", ["Hibiscus rosa-sinensis"]),
    "PLANT_017": ("Honge", ["Millettia pinnata", "Pongamia pinnata"]),
    "PLANT_018": ("Insulin", ["Chamaecostus cuspidatus", "Costus igneus"]),
    "PLANT_019": ("Jasmine", ["Jasminum officinale", "Jasminum sambac"]),
    "PLANT_020": ("Lemon", ["Citrus limon"]),
    "PLANT_021": ("Lemon_grass", ["Cymbopogon citratus"]),
    "PLANT_022": ("Mango", ["Mangifera indica"]),
    "PLANT_023": ("Mint", ["Mentha spicata", "Mentha piperita"]),
    "PLANT_024": ("Nagadali", ["Ruta graveolens"]),
    "PLANT_025": ("Neem", ["Azadirachta indica"]),
    "PLANT_026": ("Nithyapushpa", ["Catharanthus roseus", "Vinca rosea"]),
    "PLANT_027": ("Nooni", ["Morinda citrifolia"]),
    "PLANT_028": ("Pappaya", ["Carica papaya"]),
    "PLANT_029": ("Pepper", ["Piper nigrum"]),
    "PLANT_030": ("Pomegranate", ["Punica granatum"]),
    "PLANT_031": ("Raktachandini", ["Pterocarpus santalinus"]),
    "PLANT_032": ("Rose", ["Rosa indica", "Rosa damascena"]),
    "PLANT_033": ("Tulasi", ["Ocimum sanctum", "Ocimum tenuiflorum"]),
    "PLANT_034": ("Wood_sorel", ["Oxalis corniculata"]),
}

PATTERNS = {
    pid: [
        re.compile(r"\b" + r"\s+".join(map(re.escape, n.split())) + r"\b", re.I)
        for n in names
    ]
    for pid, (_, names) in PLANTS.items()
}

CSV_FIELDS = [
    "row", "source_file", "section_file", "heading",
    "plant_id", "status", "candidates", "preview",
]


def read_text(path):
    return Path(path).read_text(encoding="utf-8", errors="replace")


def propose(force=False):
    if CSV_PATH.exists() and not force:
        print(f"{CSV_PATH.name} already exists (may contain your edits).")
        print("Re-run with --force to overwrite it.")
        return

    rows = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    out = []
    counts = defaultdict(int)
    status_counts = defaultdict(int)

    for i, r in enumerate(rows, start=1):
        text = read_text(r["section_file"])
        head = " ".join(text[:SCAN_CHARS].split())
        hits = sorted(
            pid for pid, pats in PATTERNS.items() if any(p.search(head) for p in pats)
        )

        if len(hits) == 1:
            pid, status = hits[0], "AUTO_MATCHED"
            counts[pid] += 1
        elif len(hits) > 1:
            pid, status = "", "REVIEW_MULTIPLE"
        else:
            pid, status = "", "NO_MATCH"

        status_counts[status] += 1
        r["plant_id"] = pid or None
        r["mapping_status"] = status

        out.append({
            "row": i,
            "source_file": r["source_file"],
            "section_file": r["section_file"],
            "heading": r["heading"],
            "plant_id": pid,
            "status": status,
            "candidates": ";".join(hits),
            "preview": head[:160],
        })

    JSON_PATH.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    with CSV_PATH.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        w.writeheader()
        w.writerows(out)

    print(f"Sections: {len(out)}  |  " + "  ".join(f"{k}: {v}" for k, v in status_counts.items()))
    print("\nAuto-matched sections per plant:")
    for pid, (ident, _) in PLANTS.items():
        print(f"  {pid} {ident:<14} {counts.get(pid, 0)}")
    print(f"\nReview file: {CSV_PATH}")
    print("Fill/fix the plant_id column for rows you want, leave blank to skip.")


def apply():
    if not CSV_PATH.exists():
        print("Run 'propose' first.")
        return

    groups = defaultdict(list)
    skipped_invalid = 0
    with CSV_PATH.open(newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            pid = (row.get("plant_id") or "").strip()
            if not pid:
                continue
            if pid not in PLANTS:
                skipped_invalid += 1
                print(f"Invalid plant_id '{pid}' at row {row['row']} - skipped")
                continue
            groups[pid].append(row)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for pid, rows in sorted(groups.items()):
        ident = PLANTS[pid][0]
        parts = [f"PLANT_ID: {pid}\nPLANT_IDENTIFIER: {ident}\n"]
        for row in rows:
            parts.append(
                f"----- SOURCE: {row['source_file']} | SECTION: {row['heading']} -----\n"
                + read_text(row["section_file"]).strip()
            )
        path = OUT_DIR / f"{pid}_{ident}.txt"
        path.write_text("\n\n".join(parts) + "\n", encoding="utf-8")
        print(f"{path.name}: {len(rows)} sections")

    missing = [pid for pid in PLANTS if pid not in groups]
    print(f"\nPlants written: {len(groups)}  |  invalid rows: {skipped_invalid}")
    print("Plants with no sections from combined sources (expected if covered by their own file):")
    print("  " + ", ".join(f"{p} {PLANTS[p][0]}" for p in missing))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "propose":
        propose(force="--force" in sys.argv)
    elif cmd == "apply":
        apply()
    else:
        print(__doc__)