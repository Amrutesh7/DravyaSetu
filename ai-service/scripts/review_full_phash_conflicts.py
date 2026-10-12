from pathlib import Path
import hashlib
import pandas as pd
from PIL import Image, ImageDraw

MASTER = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata\full_cross_class_phash_audit.csv"
)

OUT = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata"
)

df = pd.read_csv(MASTER)

# All currently detected conflicts
files = {}

for _, row in df.iterrows():

    for class_name, path_str in [
        (row["class_a"], row["path_a"]),
        (row["class_b"], row["path_b"])
    ]:

        path = Path(path_str)

        if path.exists():
            files[str(path)] = {
                "class": class_name,
                "path": path
            }

print()
print("=" * 70)
print("FULL pHASH CONFLICT REVIEW")
print("=" * 70)

print("Conflict pairs:", len(df))
print("Unique active files:", len(files))

# ------------------------------------------------------------
# SHA-256
# ------------------------------------------------------------

sha_rows = []

for item in files.values():

    path = item["path"]

    sha = hashlib.sha256(path.read_bytes()).hexdigest()

    sha_rows.append({
        "class": item["class"],
        "filename": path.name,
        "path": str(path),
        "sha256": sha
    })

sha_df = pd.DataFrame(sha_rows)

sha_output = OUT / "full_cross_class_phash_conflicts_sha256.csv"
sha_df.to_csv(sha_output, index=False)

print()
print("SHA-256 AUDIT")
print("-" * 70)
print(
    sha_df[
        ["class", "filename", "sha256"]
    ].to_string(index=False)
)

print()
print("Cross-class exact SHA-256 matches:")

found = False

for sha, group in sha_df.groupby("sha256"):

    if len(set(group["class"])) > 1:

        found = True
        print(group.to_string(index=False))
        print()

if not found:
    print("NONE")

# ------------------------------------------------------------
# CONTACT SHEET
# ------------------------------------------------------------

thumb_w = 500
thumb_h = 360
cols = 2

file_list = list(files.values())

rows_count = (len(file_list) + cols - 1) // cols

sheet = Image.new(
    "RGB",
    (cols * thumb_w, rows_count * thumb_h),
    "white"
)

draw = ImageDraw.Draw(sheet)

for i, item in enumerate(file_list):

    path = item["path"]
    class_name = item["class"]

    try:

        img = Image.open(path).convert("RGB")

        img.thumbnail(
            (thumb_w - 20, thumb_h - 90)
        )

        x = (i % cols) * thumb_w
        y = (i // cols) * thumb_h

        px = x + (thumb_w - img.width) // 2
        py = y + 10

        sheet.paste(img, (px, py))

        draw.multiline_text(
            (x + 10, y + thumb_h - 70),
            f"{class_name}\n{path.name}",
            fill="black"
        )

    except Exception as e:

        print("ERROR:", path)
        print(e)

contact_output = OUT / "full_cross_class_phash_conflicts_contact_sheet.jpg"

sheet.save(
    contact_output,
    quality=95
)

print()
print("=" * 70)
print("CONTACT SHEET CREATED")
print("=" * 70)

print(contact_output)

print()
print("SHA REPORT:")
print(sha_output)

print()
print("No files were moved or deleted.")