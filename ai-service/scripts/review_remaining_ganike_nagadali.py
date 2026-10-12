from pathlib import Path
import hashlib
import pandas as pd
from PIL import Image, ImageDraw, ImageFont

MASTER = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\duplicate_review\cross_class_review.csv"
)

METADATA = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata"
)

df = pd.read_csv(MASTER)

# Only Ganike <-> Nagadali
pairs = df[
    (
        (df["class_a"] == "Ganike") &
        (df["class_b"] == "Nagadali")
    )
    |
    (
        (df["class_a"] == "Nagadali") &
        (df["class_b"] == "Ganike")
    )
]

# Only distance 4 and 6
pairs = pairs[pairs["distance"].isin([4, 6])]

# Collect only files that are STILL in clean_candidate_pool
files = {}

for _, row in pairs.iterrows():

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
print("REMAINING GANike / NAGADALI pHASH REVIEW")
print("=" * 70)

print("Remaining unique active files:", len(files))
print()

# SHA-256 audit
rows = []

for item in files.values():

    path = item["path"]

    sha = hashlib.sha256(path.read_bytes()).hexdigest()

    rows.append({
        "class": item["class"],
        "filename": path.name,
        "path": str(path),
        "sha256": sha
    })

audit = pd.DataFrame(rows)

sha_output = METADATA / "ganike_nagadali_distance4_6_active_sha256.csv"
audit.to_csv(sha_output, index=False)

print(audit[["class", "filename", "sha256"]].to_string(index=False))

print()
print("Cross-class exact SHA-256 matches:")

found = False

for sha, group in audit.groupby("sha256"):

    if len(set(group["class"])) > 1:

        found = True
        print(group.to_string(index=False))
        print()

if not found:
    print("NONE")

# ------------------------------------------------------------
# CONTACT SHEET
# ------------------------------------------------------------

thumb_w = 400
thumb_h = 320
cols = 2
rows_count = (len(files) + cols - 1) // cols

sheet = Image.new(
    "RGB",
    (cols * thumb_w, rows_count * thumb_h),
    "white"
)

draw = ImageDraw.Draw(sheet)

for i, item in enumerate(files.values()):

    path = item["path"]
    class_name = item["class"]

    try:
        img = Image.open(path).convert("RGB")
        img.thumbnail((thumb_w - 20, thumb_h - 70))

        x = (i % cols) * thumb_w
        y = (i // cols) * thumb_h

        image_x = x + (thumb_w - img.width) // 2
        image_y = y + 10

        sheet.paste(img, (image_x, image_y))

        label = f"{class_name}\n{path.name}"

        draw.multiline_text(
            (x + 10, y + thumb_h - 55),
            label,
            fill="black"
        )

    except Exception as e:
        print("Could not read:", path)
        print("Error:", e)

contact_output = METADATA / "ganike_nagadali_distance4_6_active_contact_sheet.jpg"

sheet.save(contact_output, quality=95)

print()
print("=" * 70)
print("CONTACT SHEET CREATED")
print("=" * 70)
print(contact_output)

print()
print("SHA REPORT:")
print(sha_output)