
from pathlib import Path
import csv
import torch
import open_clip
from PIL import Image

ROOT = Path(r"D:\DravyaSetu\ai-service\data\DravyaSetu_v2_1492")
RAW = ROOT / "raw"
OUT = ROOT / "metadata" / "full_evidence_audit" / "individual_photo_test_4.csv"

OBSERVATIONS = [
    ("Aloe_Vera", "103520679", "WHOLE_PLANT"),
    ("Aloe_Vera", "103838408", "WHOLE_PLANT"),
    ("Aloe_Vera", "109592995", "FLOWER"),
    ("Amla", "102393828", "LEAF"),
]

CATEGORIES = {
    "WHOLE_PLANT": [
        "a photograph showing the entire medicinal plant",
        "a full view of a whole plant growing outdoors",
    ],
    "LEAF": [
        "a close-up photograph of medicinal plant leaves",
        "a photograph focused on individual leaves",
    ],
    "FLOWER": [
        "a close-up photograph of flowers on a plant",
        "a photograph focused on plant blossoms",
    ],
    "FRUIT": [
        "a photograph focused on fruits or berries on a plant",
        "a close-up photograph of plant fruit",
    ],
    "STEM_BARK": [
        "a close-up photograph of a plant stem or bark",
        "a photograph focused on a tree trunk or stem",
    ],
    "MULTI_PART": [
        "a photograph showing several different parts of a plant",
        "a botanical evidence image showing multiple plant parts",
    ],
    "OTHER": [
        "a photograph of a plant part other than leaves flowers fruit or bark",
        "a botanical photograph of another plant feature",
    ],
    "UNCLEAR": [
        "a blurry or obscured plant photograph with insufficient detail",
        "a plant image where the evidence is difficult to identify",
    ],
}


def main():
    device = "cpu"
    model, _, preprocess = open_clip.create_model_and_transforms(
        "ViT-B-32",
        pretrained="laion2b_s34b_b79k",
    )
    tokenizer = open_clip.get_tokenizer("ViT-B-32")
    model = model.to(device).eval()

    prompts = []
    prompt_labels = []

    for label, texts in CATEGORIES.items():
        for prompt in texts:
            prompts.append(prompt)
            prompt_labels.append(label)

    with torch.no_grad():
        tokens = tokenizer(prompts).to(device)
        text_features = model.encode_text(tokens)
        text_features /= text_features.norm(dim=-1, keepdim=True)

    rows = []

    for class_name, obs_id, human_label in OBSERVATIONS:
        folder = RAW / class_name
        pattern = f"{class_name}_inat_{obs_id}_*"
        images = sorted(folder.glob(pattern))

        if not images:
            print(f"NO PHOTOS: {class_name} {obs_id}")
            continue

        per_photo_predictions = []

        for image_path in images:
            try:
                with Image.open(image_path) as image:
                    image = image.convert("RGB")
                    tensor = preprocess(image).unsqueeze(0).to(device)

                with torch.no_grad():
                    image_features = model.encode_image(tensor)
                    image_features /= image_features.norm(dim=-1, keepdim=True)
                    similarities = (100.0 * image_features @ text_features.T)[0]
                    category_scores = {}

                    for label in CATEGORIES:
                        indices = [
                            i for i, candidate in enumerate(prompt_labels)
                            if candidate == label
                        ]
                        category_scores[label] = float(
                            similarities[indices].mean().item()
                        )

                predicted = max(category_scores, key=category_scores.get)
                ranked = sorted(
                    category_scores.items(),
                    key=lambda item: item[1],
                    reverse=True,
                )

                print("Top 3 category scores:")
                for label, score in ranked[:3]:
                    print(f"  {label}: {score:.4f}")

            except Exception as exc:
                print(f"ERROR processing {image_path.name}: {exc}")

        if per_photo_predictions:
            # Observation-level result: majority vote across individual photos.
            counts = {}
            for label in per_photo_predictions:
                counts[label] = counts.get(label, 0) + 1

            majority = max(counts, key=counts.get)
            print(
                f"OBSERVATION RESULT: {class_name} {obs_id} | "
                f"human={human_label} | majority={majority} | "
                f"match={majority == human_label}\n"
            )

    OUT.parent.mkdir(parents=True, exist_ok=True)

    with OUT.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=[
            "class_name", "observation_id", "human_label",
            "image_filename", "photo_prediction",
            "photo_matches_human", "status",
        ])
        writer.writeheader()
        writer.writerows(rows)

    print(f"\nPhotos processed: {len(rows)}")
    print(f"Results saved to: {OUT}")
    print("Predictions are experimental suggestions, not probabilities.")


if __name__ == "__main__":
    main()