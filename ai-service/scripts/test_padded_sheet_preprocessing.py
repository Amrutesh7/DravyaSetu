
from pathlib import Path
import hashlib

from PIL import Image, ImageOps
import torch
import open_clip
from torchvision import transforms

ROOT = Path(r"D:\DravyaSetu\ai-service")
SHEETS_DIR = (
    ROOT / "data" / "DravyaSetu_v2_1492"
    / "metadata" / "evidence_audit_sheets"
)

NAMES = [
    "Aloe_Vera_obs_103520679.jpg",
    "Betel_obs_147533889.jpg",
    "Curry_Leaf_obs_353391815.jpg",
    "Nagadali_obs_105978787.jpg",
    "Wood_sorel_obs_64567674.jpg",
]

SIZE = 224

model, _, _ = open_clip.create_model_and_transforms(
    "ViT-B-32",
    pretrained="laion2b_s34b_b79k",
    device="cpu",
)
model.eval()

to_tensor = transforms.ToTensor()
normalize = transforms.Normalize(
    (0.48145466, 0.4578275, 0.40821073),
    (0.26862954, 0.26130258, 0.27577711),
)

tensors = []
embeddings = []

for name in NAMES:
    path = SHEETS_DIR / name

    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    original_size = image.size

    # Fit the ENTIRE image inside 224x224.
    scale = min(SIZE / image.width, SIZE / image.height)
    new_width = round(image.width * scale)
    new_height = round(image.height * scale)

    image = image.resize(
        (new_width, new_height),
        Image.Resampling.LANCZOS,
    )

    canvas = Image.new("RGB", (SIZE, SIZE), (128, 128, 128))
    x = (SIZE - new_width) // 2
    y = (SIZE - new_height) // 2
    canvas.paste(image, (x, y))

    tensor = normalize(to_tensor(canvas))

    with torch.no_grad():
        embedding = model.encode_image(tensor.unsqueeze(0))
        embedding = embedding / embedding.norm(
            dim=-1, keepdim=True
        ).clamp_min(1e-12)

    tensors.append(tensor)
    embeddings.append(embedding)

    digest = hashlib.sha256(
        tensor.contiguous().numpy().tobytes()
    ).hexdigest()

    print(f"\n{name}")
    print("Original size:", original_size)
    print("Resized size:", image.size)
    print("Tensor SHA256:", digest)

print("\n===== COMPARISON =====")

for i in range(1, len(NAMES)):
    diff = (tensors[0] - tensors[i]).abs().max().item()
    similarity = torch.nn.functional.cosine_similarity(
        embeddings[0], embeddings[i]
    ).item()

    print(
        f"Image 1 vs {i + 1}: "
        f"max tensor difference={diff:.8f}, "
        f"cosine similarity={similarity:.8f}"
    )

print("\nDiagnostic complete. No existing files modified.")