import csv
import os
from PIL import Image

BASE = r"D:\DravyaSetu\datasets\v2_expansion"

MANIFEST = os.path.join(
    BASE, "metadata", "inat_original_manifest.csv"
)

OUTPUT = os.path.join(
    BASE, "metadata", "inat_original_integrity_audit.csv"
)

MIN_SIZE = 384

total = 0
exists = 0
missing = 0
valid_resolution = 0
low_resolution = 0
decode_failed = 0
thumbnail_75 = 0
manifest_mismatch = 0

rows = []

with open(MANIFEST, "r", encoding="utf-8", newline="") as f:
    reader = csv.DictReader(f)

    for row in reader:
        total += 1

        path = row["local_path"]

        result = {
            "class_name": row["class_name"],
            "photo_id": row["photo_id"],
            "observation_id": row["observation_id"],
            "filename": row["filename"],
            "local_path": path,
            "manifest_width": row["width"],
            "manifest_height": row["height"],
            "status": row["status"],
            "actual_width": "",
            "actual_height": "",
            "result": "",
            "error": "",
        }

        if not os.path.exists(path):
            missing += 1
            result["result"] = "MISSING"
            rows.append(result)
            continue

        exists += 1

        try:
            with Image.open(path) as img:
                actual_w, actual_h = img.size

                result["actual_width"] = actual_w
                result["actual_height"] = actual_h

                if actual_w == 75 and actual_h == 75:
                    thumbnail_75 += 1
                    result["result"] = "THUMBNAIL_75X75"

                elif actual_w < MIN_SIZE or actual_h < MIN_SIZE:
                    low_resolution += 1
                    result["result"] = "LOW_RESOLUTION"

                else:
                    valid_resolution += 1
                    result["result"] = "VALID_RESOLUTION"

                try:
                    mw = int(row["width"])
                    mh = int(row["height"])

                    if mw != actual_w or mh != actual_h:
                        manifest_mismatch += 1
                        result["error"] = "MANIFEST_DIMENSION_MISMATCH"

                except (ValueError, TypeError):
                    manifest_mismatch += 1
                    result["error"] = "INVALID_MANIFEST_DIMENSIONS"

        except Exception as e:
            decode_failed += 1
            result["result"] = "DECODE_FAILED"
            result["error"] = str(e)

        rows.append(result)

with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
    fieldnames = list(rows[0].keys()) if rows else []
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(rows)

print("=" * 55)
print("iNaturalist Original Integrity Audit")
print("=" * 55)
print(f"Manifest records       : {total}")
print(f"Files exist            : {exists}")
print(f"Missing files          : {missing}")
print(f"Valid resolution       : {valid_resolution}")
print(f"Low resolution         : {low_resolution}")
print(f"75x75 thumbnails       : {thumbnail_75}")
print(f"Decode failures        : {decode_failed}")
print(f"Manifest mismatches    : {manifest_mismatch}")
print("=" * 55)
print(f"Audit saved to         : {OUTPUT}")
print("=" * 55)
