"""
Lists NO_MATCH sections that may still belong to one of the 34 plants.

Run from knowledge-service/:
  python scripts/check_unmatched.py

Two checks on each NO_MATCH row (read-only, changes nothing):
  1. heading contains a plant's Ayurvedic/regional name (from plant-classes.json)
  2. first 1500 chars contain the plant's scientific name, after fixing
     hyphenated line breaks (e.g. "Aza-\\ndirachta") that the first pass missed
If a row is really your plant, set its plant_id in the CSV, then run 'apply'.
"""
import csv
import json
import re
from collections import defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
CSV_PATH = BASE / "knowledge-base" / "mapped" / "section_mapping_review.csv"
CLASSES = BASE.parent / "shared" / "constants" / "plant-classes.json"

classes = json.loads(CLASSES.read_text(encoding="utf-8"))["classes"]


def word(s):
    return re.compile(r"\b" + r"\s+".join(map(re.escape, s.split())) + r"\b", re.I)


regional = {
    pid: [word(n) for n in c["ayurvedic_regional_names"]] for pid, c in classes.items()
}
latin = {}
for pid, c in classes.items():
    pats = []
    for part in c["scientific_name"].split("/"):
        words = part.replace("(", " ").split()[:2]
        if len(words) == 2:
            pats.append(word(" ".join(words)))
    latin[pid] = pats

found = defaultdict(list)
with CSV_PATH.open(newline="", encoding="utf-8-sig") as f:
    for row in csv.DictReader(f):
        if row["status"] != "NO_MATCH" or row["plant_id"].strip():
            continue
        text = Path(row["section_file"]).read_text(encoding="utf-8", errors="replace")
        text = re.sub(r"-\s*\n\s*", "", text)
        head = " ".join(text[:1500].split())
        for pid in classes:
            via = []
            if any(p.search(row["heading"]) for p in regional[pid]):
                via.append("regional name in heading")
            if any(p.search(head) for p in latin[pid]):
                via.append("scientific name in text")
            if via:
                found[pid].append((row["row"], row["heading"], ", ".join(via)))

print(f"Plants with possible missed sections: {len(found)}\n")
for pid in classes:
    for r, h, v in found.get(pid, []):
        print(f"{pid} {classes[pid]['plant_identifier']:<14} row {r:<4} {h[:40]:<40} [{v}]")
if not found:
    print("None found - NO_MATCH rows do not mention any of the 34 plants.")