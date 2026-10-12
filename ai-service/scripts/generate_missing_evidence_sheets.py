import csv
import re
from pathlib import Path
from collections import defaultdict

from PIL import Image, ImageDraw, ImageFont, ImageOps

BASE = Path(r"D:\DravyaSetu\ai-service\data\DravyaSetu_v2_1492")
RAW = BASE / "raw"
METADATA = BASE / "metadata"
MANIFEST = METADATA / "full_evidence_audit" / "full_observation_manifest.csv"
SHEET_DIR = METADATA / "evidence_audit_sheets"
AUDIT_CSV = METADATA / "full_evidence_audit" / "missing_sheet_generation_audit.csv"

THUMB_W, THUMB_H = 300, 250
LABEL_H = 48
COLUMNS = 3

def load_font(size=16):
    try:
        return ImageFont.truetype("arial.ttf", size)
    except OSError:
        return ImageFont.load_default()

def safe_name(value):
    return re.sub(r'[<>:"/\\|?*]', "_", str(value))

# Index images by class and filename. Do not use _collection_candidates.
local_images = {}
for class_dir in RAW.iterdir():
    if not class_dir.is_dir() or class_dir.name == "_collection_candidates":
        continue
    for path in class_dir.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
            local_images[(class_dir.name, path.name)] = path

groups = defaultdict(list)
with MANIFEST.open("r", encoding="utf-8-sig", newline="") as f:
    for row in csv.DictReader(f):
        key = (row["class_name"], row["observation_id"])
        groups[key].append(row)

font = load_font()
audit_rows = []
generated = skipped_existing = skipped_missing = failed = 0

SHEET_DIR.mkdir(parents=True, exist_ok=True)

for (class_name, observation_id), rows in sorted(groups.items()):
    output = SHEET_DIR / f"{safe_name(class_name)}_obs_{safe_name(observation_id)}.jpg"

    if output.exists():
        skipped_existing += 1
        audit_rows.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "status": "EXISTING_SHEET_PRESERVED",
            "image_count": len(rows),
            "sheet_path": str(output),
            "notes": "",
        })
        continue

    paths = []
    missing_names = []
    for row in rows:
        p = local_images.get((class_name, Path(row["filename"]).name))
        if p is None:
            missing_names.append(row["filename"])
        else:
            paths.append((row["filename"], p))

    if missing_names or not paths:
        skipped_missing += 1
        audit_rows.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "status": "SKIPPED_MISSING_IMAGES",
            "image_count": len(rows),
            "sheet_path": "",
            "notes": "Missing: " + "; ".join(missing_names[:10]),
        })
        continue

    try:
        tiles = []
        for filename, path in paths:
            with Image.open(path) as source:
                image = ImageOps.exif_transpose(source).convert("RGB")
                image.thumbnail((THUMB_W, THUMB_H))
                tile = Image.new("RGB", (THUMB_W, THUMB_H + LABEL_H), "white")
                x = (THUMB_W - image.width) // 2
                y = (THUMB_H - image.height) // 2
                tile.paste(image, (x, y))
                draw = ImageDraw.Draw(tile)
                draw.text((6, THUMB_H + 5), filename[:45], fill="black", font=font)
                tiles.append(tile)

        rows_count = (len(tiles) + COLUMNS - 1) // COLUMNS
        sheet = Image.new(
            "RGB",
            (COLUMNS * THUMB_W, rows_count * (THUMB_H + LABEL_H)),
            "#dddddd",
        )
        for index, tile in enumerate(tiles):
            sheet.paste(
                tile,
                ((index % COLUMNS) * THUMB_W, (index // COLUMNS) * (THUMB_H + LABEL_H)),
            )

        sheet.save(output, "JPEG", quality=88)
        generated += 1
        audit_rows.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "status": "GENERATED",
            "image_count": len(rows),
            "sheet_path": str(output),
            "notes": "",
        })
    except Exception as exc:
        failed += 1
        audit_rows.append({
            "class_name": class_name,
            "observation_id": observation_id,
            "status": "FAILED",
            "image_count": len(rows),
            "sheet_path": "",
            "notes": str(exc),
        })

with AUDIT_CSV.open("w", encoding="utf-8-sig", newline="") as f:
    writer = csv.DictWriter(
        f,
        fieldnames=["class_name", "observation_id", "status", "image_count", "sheet_path", "notes"],
    )
    writer.writeheader()
    writer.writerows(audit_rows)

print("\n===== SHEET GENERATION SUMMARY =====")
print(f"Manifest observations:       {len(groups)}")
print(f"Existing sheets preserved:   {skipped_existing}")
print(f"New sheets generated:        {generated}")
print(f"Skipped: images unavailable: {skipped_missing}")
print(f"Failed:                      {failed}")
print(f"Audit CSV: {AUDIT_CSV}")
