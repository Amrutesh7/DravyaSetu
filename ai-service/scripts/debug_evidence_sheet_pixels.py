from pathlib import Path
import hashlib
from PIL import Image, ImageOps, ImageStat

root = Path(r"D:\DravyaSetu\ai-service")
sheets_dir = (
    root / "data" / "DravyaSetu_v2_1492"
    / "metadata" / "evidence_audit_sheets"
)

names = [
    "Aloe_Vera_obs_103520679.jpg",
    "Betel_obs_147533889.jpg",
    "Curry_Leaf_obs_353391815.jpg",
    "Nagadali_obs_105978787.jpg",
    "Wood_sorel_obs_64567674.jpg",
]

for name in names:
    path = sheets_dir / name
    print(f"\n--- {name} ---")
    print("Exists:", path.exists())

    if not path.exists():
        continue

    print("File size:", path.stat().st_size)

    with Image.open(path) as source:
        print("Dimensions:", source.size)
        print("Mode:", source.mode)
        image = ImageOps.exif_transpose(source).convert("RGB")
        stats = ImageStat.Stat(image)
        print(
            "Pixel SHA256:",
            hashlib.sha256(image.tobytes()).hexdigest()
        )
        print("Pixel means:", stats.mean)
        print("Pixel extrema:", stats.extrema)

print("\nDiagnostic complete. No files modified.")
