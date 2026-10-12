
from pathlib import Path
import hashlib

import open_clip
import torch
from PIL import Image, ImageOps, ImageChops

ROOT = Path(r"D:\DravyaSetu\ai-service")
SHEETS_DIR = (
    ROOT / "data" / "DravyaSetu_v2_1492"
    / "metadata" / "evidence_audit_sheets"
)

names = [
    "Aloe_Vera_obs_103520679.jpg",
    "Betel_obs_147533889.jpg",
    "Curry_Leaf_obs_353391815.jpg",
    "Nagadali_obs_105978787.jpg",
    "Wood_sorel_obs_64567674.jpg",
]

paths = [SHEETS_DIR / name for name in names]

print("===== ORIGINAL IMAGE PIXELS =====")

for path in paths:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        digest = hashlib.sha256(image.tobytes()).hexdigest()
        print(
            f"{path.name}\n"
            f"  Size: {image.size}\n"
            f"  Pixel SHA256: {digest}\n"
            f"  Pixel range: {image.getextrema()}"
        )

print("\n===== OPENCLIP PREPROCESSING =====")

model, _, preprocess = open_clip.create_model_and_transforms(
    "ViT-B-32",
    pretrained="laion2b_s34b_b79k",
    device="cpu",
)
model.eval()

processed = []

for path in paths:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        tensor = preprocess(image)

    digest = hashlib.sha256(
        tensor.contiguous().numpy().tobytes()
    ).hexdigest()

    processed.append(tensor)

    print(
        f"{path.name}\n"
        f"  Tensor shape: {tuple(tensor.shape)}\n"
        f"  Tensor SHA256: {digest}\n"
        f"  Tensor range: "
        f"{tensor.min().item():.5f} to {tensor.max().item():.5f}"
    )

print("\n===== PREPROCESSED TENSOR DIFFERENCES =====")

for i in range(1, len(processed)):
    difference = (
        processed[0] - processed[i]
    ).abs()

    print(
        f"Image 1 vs image {i + 1}: "
        f"max difference={difference.max().item():.8f}, "
        f"mean difference={difference.mean().item():.8f}"
    )

print("\nDiagnostic complete. No files were modified.")