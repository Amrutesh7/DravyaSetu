
import open_clip
import torch
from PIL import Image, ImageOps

model, _, preprocess = open_clip.create_model_and_transforms(
    "ViT-B-32",
    pretrained="laion2b_s34b_b79k",
    device="cpu",
)

print("===== PREPROCESS TRANSFORM =====")
print(preprocess)

folder = (
    r"D:\DravyaSetu\ai-service\data\DravyaSetu_v2_1492"
    r"\metadata\evidence_audit_sheets"
)

names = [
    "Aloe_Vera_obs_103520679.jpg",
    "Betel_obs_147533889.jpg",
]

tensors = []

for name in names:
    path = folder + "\\" + name

    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        print(f"\n{name}: original size={image.size}")

        # Check preprocessing output.
        tensor = preprocess(image)
        tensors.append(tensor)

        print("Tensor shape:", tuple(tensor.shape))
        print("Tensor mean:", tensor.mean().item())
        print("Tensor std:", tensor.std().item())
        print("Tensor first 12 values:", tensor.flatten()[:12].tolist())

print("\n===== COMPARISON =====")
print("Exact tensor equality:", torch.equal(tensors[0], tensors[1]))
print(
    "Maximum absolute difference:",
    (tensors[0] - tensors[1]).abs().max().item(),
)

print("\nDiagnostic complete.")