from pathlib import Path
import pandas as pd
from PIL import Image, ImageDraw, ImageFont
import hashlib
import math

# ---------------------------------------------------------
# PATHS
# ---------------------------------------------------------

BASE = Path(r"D:\DravyaSetu")

INPUT_CSV = (
    BASE
    / "datasets"
    / "v2_expansion"
    / "metadata"
    / "ganike_nagadali_distance0_conflicts.csv"
)

OUTPUT_DIR = (
    BASE
    / "datasets"
    / "v2_expansion"
    / "metadata"
)

OUTPUT_CSV = OUTPUT_DIR / "ganike_nagadali_distance0_36file_audit.csv"
CONTACT_SHEET = OUTPUT_DIR / "ganike_nagadali_distance0_36file_contact_sheet.jpg"


# ---------------------------------------------------------
# LOAD CONFLICT PAIRS
# ---------------------------------------------------------

df = pd.read_csv(INPUT_CSV)

files = []

for _, row in df.iterrows():
    files.append(("Ganike", row["path_a"]))
    files.append(("Nagadali", row["path_b"]))

# Remove duplicates while preserving order
unique_files = []
seen = set()

for label, path in files:
    key = str(Path(path).resolve())

    if key not in seen:
        seen.add(key)
        unique_files.append((label, Path(path)))


print("Unique files found:", len(unique_files))


# ---------------------------------------------------------
# SHA-256 FUNCTION
# ---------------------------------------------------------

def sha256_file(path):
    h = hashlib.sha256()

    with open(path, "rb") as f:
        while True:
            chunk = f.read(1024 * 1024)

            if not chunk:
                break

            h.update(chunk)

    return h.hexdigest()


# ---------------------------------------------------------
# IMAGE AUDIT
# ---------------------------------------------------------

records = []

for original_label, path in unique_files:

    if not path.exists():
        print("MISSING:", path)

        records.append({
            "original_class": original_label,
            "filename": path.name,
            "path": str(path),
            "exists": False,
            "sha256": "",
            "width": "",
            "height": "",
            "mode": "",
        })

        continue

    try:
        with Image.open(path) as img:

            width, height = img.size
            mode = img.mode

        sha = sha256_file(path)

        records.append({
            "original_class": original_label,
            "filename": path.name,
            "path": str(path),
            "exists": True,
            "sha256": sha,
            "width": width,
            "height": height,
            "mode": mode,
        })

    except Exception as e:

        print("ERROR:", path, e)

        records.append({
            "original_class": original_label,
            "filename": path.name,
            "path": str(path),
            "exists": True,
            "sha256": "",
            "width": "",
            "height": "",
            "mode": "",
        })


audit = pd.DataFrame(records)


# ---------------------------------------------------------
# FIND CROSS-CLASS EXACT SHA MATCHES
# ---------------------------------------------------------

print()
print("=" * 70)
print("CROSS-CLASS EXACT SHA-256 MATCHES")
print("=" * 70)

sha_groups = audit[audit["sha256"] != ""].groupby("sha256")

exact_cross_class = []

for sha, group in sha_groups:

    classes = set(group["original_class"])

    if "Ganike" in classes and "Nagadali" in classes:

        exact_cross_class.append(group)

        print()
        print("SHA-256:", sha)

        for _, r in group.iterrows():
            print(" ", r["original_class"], "->", r["filename"])


if not exact_cross_class:
    print("NONE FOUND")


# ---------------------------------------------------------
# SAVE AUDIT CSV
# ---------------------------------------------------------

audit.to_csv(OUTPUT_CSV, index=False)


# ---------------------------------------------------------
# CONTACT SHEET
# ---------------------------------------------------------

THUMB_W = 240
THUMB_H = 220

COLS = 4
ROWS = math.ceil(len(unique_files) / COLS)

SHEET_W = COLS * THUMB_W
SHEET_H = ROWS * THUMB_H

sheet = Image.new("RGB", (SHEET_W, SHEET_H), "white")

draw = ImageDraw.Draw(sheet)

try:
    font = ImageFont.truetype("arial.ttf", 18)
    small_font = ImageFont.truetype("arial.ttf", 14)
except:
    font = ImageFont.load_default()
    small_font = ImageFont.load_default()


for i, (original_label, path) in enumerate(unique_files):

    x = (i % COLS) * THUMB_W
    y = (i // COLS) * THUMB_H

    try:

        with Image.open(path) as img:

            img = img.convert("RGB")
            img.thumbnail((220, 170))

            img_x = x + (THUMB_W - img.width) // 2
            img_y = y + 8

            sheet.paste(img, (img_x, img_y))

        draw.text(
            (x + 8, y + 182),
            original_label,
            fill="black",
            font=font
        )

        draw.text(
            (x + 8, y + 202),
            path.stem[:24],
            fill="black",
            font=small_font
        )

    except Exception as e:

        draw.text(
            (x + 8, y + 20),
            "ERROR",
            fill="red",
            font=font
        )

        draw.text(
            (x + 8, y + 50),
            path.name[:25],
            fill="black",
            font=small_font
        )


sheet.save(CONTACT_SHEET, quality=95)


# ---------------------------------------------------------
# FINAL SUMMARY
# ---------------------------------------------------------

print()
print("=" * 70)
print("AUDIT COMPLETE")
print("=" * 70)

print("Unique files:", len(unique_files))
print("Ganike:", sum(1 for x in unique_files if x[0] == "Ganike"))
print("Nagadali:", sum(1 for x in unique_files if x[0] == "Nagadali"))

print()
print("Audit CSV:")
print(OUTPUT_CSV)

print()
print("Contact sheet:")
print(CONTACT_SHEET)