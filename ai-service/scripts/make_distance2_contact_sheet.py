from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

items = [
    (
        "Ganike",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike\arya_shah_905a60055555.jpg")
    ),
    (
        "Ganike",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike\arya_shah_9372f2ef737e.jpg")
    ),
    (
        "Nagadali",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_3c7ef0876fe1.jpg")
    ),
    (
        "Nagadali",
        Path(r"D:\DravyaSetu\datasets\clean_candidate_pool\Nagadali\arya_shah_18ad7f646317.jpg")
    ),
]

OUTPUT = Path(
    r"D:\DravyaSetu\datasets\v2_expansion\metadata"
) / "ganike_nagadali_distance2_4file_contact_sheet.jpg"

CELL_W = 500
CELL_H = 430

sheet = Image.new(
    "RGB",
    (CELL_W * 2, CELL_H * 2),
    "white"
)

draw = ImageDraw.Draw(sheet)

try:
    font = ImageFont.truetype("arial.ttf", 20)
    small_font = ImageFont.truetype("arial.ttf", 16)
except:
    font = ImageFont.load_default()
    small_font = ImageFont.load_default()

for i, (class_name, path) in enumerate(items):

    x = (i % 2) * CELL_W
    y = (i // 2) * CELL_H

    print("Reading:", path)

    if not path.exists():
        draw.text(
            (x + 20, y + 20),
            "FILE NOT FOUND",
            fill="red",
            font=font
        )
        draw.text(
            (x + 20, y + 60),
            path.name,
            fill="black",
            font=small_font
        )
        continue

    with Image.open(path) as img:

        img = img.convert("RGB")
        img.thumbnail((460, 340))

        image_x = x + (CELL_W - img.width) // 2
        image_y = y + 10

        sheet.paste(img, (image_x, image_y))

    draw.text(
        (x + 15, y + 360),
        class_name,
        fill="black",
        font=font
    )

    draw.text(
        (x + 15, y + 390),
        path.stem,
        fill="black",
        font=small_font
    )

OUTPUT.parent.mkdir(
    parents=True,
    exist_ok=True
)

sheet.save(
    OUTPUT,
    quality=95
)

print()
print("=" * 60)
print("CONTACT SHEET CREATED")
print("=" * 60)
print(OUTPUT)