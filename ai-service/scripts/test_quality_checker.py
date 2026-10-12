
from pathlib import Path

from app.preprocessing.quality_checker import ImageQualityChecker

checker = ImageQualityChecker()

image_folder = Path(
    r"D:\DravyaSetu\datasets\clean_candidate_pool\Ganike"
)

if not image_folder.exists():
    raise FileNotFoundError(
        f"Image folder not found: {image_folder}"
    )

extensions = {".jpg", ".jpeg", ".png", ".webp"}
image_paths = [
    path for path in image_folder.rglob("*")
    if path.is_file() and path.suffix.lower() in extensions
]

if not image_paths:
    print("No images found.")
else:
    for path in image_paths[:20]:
        result = checker.check(path)

        print(f"\nImage: {path.name}")
        print(f"Acceptable : {result.acceptable}")
        print(f"Score      : {result.score:.3f}")
        print(f"Dimensions : {result.width} x {result.height}")
        print(f"Brightness : {result.brightness:.2f}")
        print(f"Blur score : {result.blur_score:.2f}")
        print(f"Issues     : {result.issues}")