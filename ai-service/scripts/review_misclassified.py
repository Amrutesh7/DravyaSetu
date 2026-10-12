
from pathlib import Path
import csv
import math

from PIL import Image, ImageDraw, ImageFont

PROJECT_ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = PROJECT_ROOT / "artifacts" / "evaluation" / "misclassified_images.csv"
OUTPUT_PATH = PROJECT_ROOT / "artifacts" / "evaluation" / "misclassified_contact_sheet.jpg"

CELL_W = 280
IMAGE_H = 210
TEXT_H = 78
COLUMNS = 3

with CSV_PATH.open("r", newline="", encoding="utf-8-sig") as f:
    rows = list(csv.DictReader(f))

if not rows:
    raise SystemExit("No misclassified images found.")

font = ImageFont.load_default()
rows_count = math.ceil(len(rows) / COLUMNS)

sheet = Image.new(
    "RGB",
    (COLUMNS * CELL_W, rows_count * (IMAGE_H + TEXT_H)),
    "white",
)
draw = ImageDraw.Draw(sheet)

for i, row in enumerate(rows):
    image_path = Path(row["image_path"])
    x = (i % COLUMNS) * CELL_W
    y = (i // COLUMNS) * (IMAGE_H + TEXT_H)

    try:
        with Image.open(image_path) as im:
            im = im.convert("RGB")
            im.thumbnail((CELL_W - 12, IMAGE_H - 12))
            px = x + (CELL_W - im.width) // 2
            py = y + (IMAGE_H - im.height) // 2
            sheet.paste(im, (px, py))
    except Exception as exc:
        draw.text((x + 8, y + 8), f"Could not open image: {exc}", fill="red")

    confidence = float(row["confidence"]) * 100
    draw.text((x + 8, y + IMAGE_H + 3), f"True: {row['true_label']}", fill="black", font=font)
    draw.text((x + 8, y + IMAGE_H + 22), f"Predicted: {row['predicted_label']}", fill="red", font=font)
    draw.text((x + 8, y + IMAGE_H + 41), f"Confidence: {confidence:.2f}%", fill="black", font=font)

OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUTPUT_PATH, quality=92)

print("Misclassified images:", len(rows))
print("Contact sheet saved to:", OUTPUT_PATH)