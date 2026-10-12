
from pathlib import Path

import open_clip
import torch
from PIL import Image, ImageOps

ROOT = Path(r"D:\DravyaSetu\ai-service")
SHEETS_DIR = (
    ROOT / "data" / "DravyaSetu_v2_1492"
    / "metadata" / "evidence_audit_sheets"
)

MODEL_NAME = "ViT-B-32"
PRETRAINED_TAG = "laion2b_s34b_b79k"
DEVICE = "cpu"

PROMPTS = {
    "WHOLE_PLANT": [
        "a photograph showing an entire medicinal plant growing in soil",
        "a full view of the complete medicinal plant including its overall shape",
    ],
    "LEAF": [
        "a close-up photograph showing medicinal plant leaves",
        "a photograph focused mainly on individual leaves of a plant",
    ],
    "FLOWER": [
        "a close-up photograph showing flowers of a medicinal plant",
        "a photograph focused mainly on the blossoms of a plant",
    ],
    "FRUIT": [
        "a close-up photograph showing fruits or berries of a medicinal plant",
        "a photograph focused mainly on plant fruit",
    ],
    "STEM_BARK": [
        "a close-up photograph showing the stem, trunk, or bark of a plant",
        "a photograph focused mainly on medicinal plant bark or stem",
    ],
    "MULTI_PART": [
        "a set of photographs showing multiple different parts of the same plant",
        "a botanical evidence sheet showing leaves and flowers or fruits and stems together",
    ],
    "OTHER": [
        "a medicinal plant specimen or plant-related image showing another plant part",
        "a botanical specimen image that does not fit the listed plant-part categories",
    ],
    "UNCLEAR": [
        "a blurry, obscured, or unclear plant photograph where the plant part cannot be identified",
        "an image with insufficient visual detail to identify the plant part",
    ],
}

sheets = sorted(
    p for p in SHEETS_DIR.iterdir()
    if p.suffix.lower() in {".jpg", ".jpeg", ".png"}
)

if len(sheets) < 2:
    raise RuntimeError("Need at least two evidence sheets.")

# Pick sheets spread across the directory.
indexes = sorted({
    round(i * (len(sheets) - 1) / 9)
    for i in range(10)
})
sample = [sheets[i] for i in indexes]

model, _, preprocess = open_clip.create_model_and_transforms(
    MODEL_NAME,
    pretrained=PRETRAINED_TAG,
    device=DEVICE,
)
tokenizer = open_clip.get_tokenizer(MODEL_NAME)
model.eval()

prompts = [
    "A botanical evidence photograph: " + prompt
    for descriptions in PROMPTS.values()
    for prompt in descriptions
]
prompt_categories = [
    category
    for category, descriptions in PROMPTS.items()
    for _ in descriptions
]

with torch.no_grad():
    tokens = tokenizer(prompts).to(DEVICE)
    text_features = model.encode_text(tokens)
    text_features /= text_features.norm(
        dim=-1, keepdim=True
    ).clamp_min(1e-12)

print("\n===== TEN-SHEET DIAGNOSTIC =====")

embeddings = []

for path in sample:
    with Image.open(path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
        print(
            f"\n{path.name}"
            f"\n  Dimensions: {image.size}"
            f"\n  File bytes: {path.stat().st_size}"
        )
        tensor = preprocess(image).unsqueeze(0).to(DEVICE)

    with torch.no_grad():
        features = model.encode_image(tensor)
        features /= features.norm(
            dim=-1, keepdim=True
        ).clamp_min(1e-12)
        scores = (features @ text_features.T)[0]

    embeddings.append(features.cpu())

    category_scores = {}
    start = 0
    for category, descriptions in PROMPTS.items():
        count = len(descriptions)
        category_scores[category] = scores[start:start + count].mean().item()
        start += count

    ranked = sorted(
        category_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    print("  Top 3 category scores:")
    for category, score in ranked[:3]:
        print(f"    {category:12s} {score:.6f}")

print("\n===== IMAGE EMBEDDING DIFFERENCES =====")
for i in range(1, len(sample)):
    similarity = torch.nn.functional.cosine_similarity(
        embeddings[0], embeddings[i]
    ).item()
    print(
        f"Sheet 1 vs sheet {i + 1}: "
        f"embedding cosine similarity = {similarity:.8f}"
    )

print("\nDiagnostic complete. No files were modified.")