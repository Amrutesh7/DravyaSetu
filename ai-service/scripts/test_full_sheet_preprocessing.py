
from pathlib import Path
import hashlib

import open_clip
import torch
from PIL import Image, ImageOps
from torchvision import transforms

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

model, _, _ = open_clip.create_model_and_transforms(
    "ViT-B-32",
    pretrained="laion2b_s34b_b79k",
    device="cpu",
)
model.eval()

# Preserve the entire sheet. No CenterCrop.
full_sheet_preprocess = transforms.Compose([
    transforms.Resize(
        (224, 224),
        interpolation=transforms.InterpolationMode.BICUBIC,
    ),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=(0.48145466, 0.4578275, 0.40821073),
        std=(0.26862954, 0.26130258, 0.27577711),
    ),
])

tensors = []
embeddings = []

for name in names:
    path = SHEETS_DIR / name

    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        tensor = full_sheet_preprocess(image)

    digest = hashlib.sha256(
        tensor.contiguous().numpy().tobytes()
    ).hexdigest()

    with torch.no_grad():
        embedding = model.encode_image(tensor.unsqueeze(0))
        embedding = embedding / embedding.norm(
            dim=-1, keepdim=True
        ).clamp_min(1e-12)

    tensors.append(tensor)
    embeddings.append(embedding)

    print(f"\n{name}")
    print("Tensor SHA256:", digest)
    print("Tensor mean:", round(tensor.mean().item(), 6))
    print("Embedding norm:", round(embedding.norm().item(), 6))

print("\n===== FULL-SHEET COMPARISON =====")

for i in range(1, len(names)):
    tensor_diff = (tensors[0] - tensors[i]).abs().max().item()
    embedding_similarity = torch.nn.functional.cosine_similarity(
        embeddings[0], embeddings[i]
    ).item()

    print(
        f"{names[0]} vs {names[i]}: "
        f"max tensor difference={tensor_diff:.8f}, "
        f"embedding cosine={embedding_similarity:.8f}"
    )

print("\nTest complete. No existing files were modified.")