
from pathlib import Path
import csv
import re

import open_clip
import torch
from PIL import Image, ImageOps

ROOT = Path(r"D:\DravyaSetu\ai-service")
DATA_ROOT = ROOT / "data" / "DravyaSetu_v2_1492"
SHEETS_DIR = DATA_ROOT / "metadata" / "evidence_audit_sheets"
AUDIT_DIR = DATA_ROOT / "metadata" / "full_evidence_audit"

DECISIONS_CSV = AUDIT_DIR / "evidence_review_decisions.csv"
OUTPUT_CSV = AUDIT_DIR / "preprocessing_validation_41.csv"

MODEL_NAME = "ViT-B-32"
PRETRAINED_TAG = "laion2b_s34b_b79k"
DEVICE = "cpu"
IMAGE_SIZE = 224

CATEGORIES = {
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

def main():
    if not DECISIONS_CSV.exists():
        raise FileNotFoundError(DECISIONS_CSV)

    if not SHEETS_DIR.exists():
        raise FileNotFoundError(SHEETS_DIR)

    decisions = {}
    with DECISIONS_CSV.open("r", newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            if row.get("reviewed", "").strip().lower() not in {
                "true", "1", "yes", "y"
            }:
                continue

            label = row.get("evidence_type", "").strip().upper()
            if label not in CATEGORIES:
                continue

            key = (
                row.get("class_name", "").strip(),
                row.get("observation_id", "").strip(),
            )
            decisions[key] = row

    sheets = {}
    for path in SHEETS_DIR.glob("*.jpg"):
        match = re.match(r"^(.*)_obs_(\d+)$", path.stem, re.I)
        if match:
            sheets[(match.group(1), match.group(2))] = path

    samples = []
    for key, row in decisions.items():
        path = sheets.get(key)
        if path:
            samples.append((row, path))

    if not samples:
        raise RuntimeError("No matching reviewed sheets found.")

    print(f"Human-reviewed labels: {len(decisions)}")
    print(f"Matching sheets: {len(samples)}")

    model, _, default_preprocess = open_clip.create_model_and_transforms(
        MODEL_NAME,
        pretrained=PRETRAINED_TAG,
    )
    model = model.to(DEVICE).eval()
    tokenizer = open_clip.get_tokenizer(MODEL_NAME)

    prompts = []
    prompt_categories = []
    for category, descriptions in CATEGORIES.items():
        for description in descriptions:
            prompts.append("A botanical evidence photograph: " + description)
            prompt_categories.append(category)

    with torch.no_grad():
        text_tokens = tokenizer(prompts).to(DEVICE)
        text_features = model.encode_text(text_tokens)
        text_features /= text_features.norm(dim=-1, keepdim=True)

    mean = torch.tensor(
        [0.48145466, 0.4578275, 0.40821073]
    ).view(3, 1, 1)
    std = torch.tensor(
        [0.26862954, 0.26130258, 0.27577711]
    ).view(3, 1, 1)

    output_rows = []

    for index, (row, path) in enumerate(samples, start=1):
        try:
            with Image.open(path) as im:
                image = ImageOps.exif_transpose(im).convert("RGB")

                # Old method: OpenCLIP default resize + center crop.
                old_tensor = default_preprocess(image)

                # Corrected candidate: preserve full sheet, fit and pad.
                fitted = ImageOps.contain(
                    image,
                    (IMAGE_SIZE, IMAGE_SIZE),
                    method=Image.Resampling.LANCZOS,
                )
                canvas = Image.new(
                    "RGB", (IMAGE_SIZE, IMAGE_SIZE), (128, 128, 128)
                )
                x = (IMAGE_SIZE - fitted.width) // 2
                y = (IMAGE_SIZE - fitted.height) // 2
                canvas.paste(fitted, (x, y))

                new_tensor = (
                    torch.from_numpy(
                        __import__("numpy").array(canvas).copy()
                    ).permute(2, 0, 1).float() / 255.0
                )
                new_tensor = (new_tensor - mean) / std

            with torch.no_grad():
                image_batch = torch.stack([old_tensor, new_tensor]).to(DEVICE)
                image_features = model.encode_image(image_batch)
                image_features /= image_features.norm(dim=-1, keepdim=True)
                similarities = image_features @ text_features.T

            predictions = []
            for method_index in range(2):
                category_scores = {}
                for category in CATEGORIES:
                    indexes = [
                        j for j, cat in enumerate(prompt_categories)
                        if cat == category
                    ]
                    category_scores[category] = similarities[
                        method_index, indexes
                    ].mean().item()

                ranking = sorted(
                    category_scores.items(),
                    key=lambda item: item[1],
                    reverse=True,
                )
                predictions.append((ranking[0][0], ranking[0][1],
                                    ranking[1][0], ranking[1][1]))

            old_pred, old_score, old_second, old_second_score = predictions[0]
            new_pred, new_score, new_second, new_second_score = predictions[1]
            human_label = row["evidence_type"].strip().upper()

            output_rows.append({
                "class_name": row["class_name"],
                "observation_id": row["observation_id"],
                "sheet_filename": path.name,
                "human_label": human_label,
                "old_center_crop_prediction": old_pred,
                "old_top_score": round(old_score, 6),
                "old_second_candidate": old_second,
                "old_margin": round(old_score - old_second_score, 6),
                "old_matches_human": old_pred == human_label,
                "fit_pad_prediction": new_pred,
                "fit_pad_top_score": round(new_score, 6),
                "fit_pad_second_candidate": new_second,
                "fit_pad_margin": round(new_score - new_second_score, 6),
                "fit_pad_matches_human": new_pred == human_label,
                "status": "DIAGNOSTIC_ONLY_NOT_CALIBRATED",
            })

            if index % 5 == 0 or index == len(samples):
                print(f"Processed {index}/{len(samples)}")

        except Exception as exc:
            print(f"FAILED: {path.name}: {exc}")

    if not output_rows:
        raise RuntimeError("No predictions completed.")

    # Write only to a new file; never alter the existing audit CSVs.
    temp_path = OUTPUT_CSV.with_suffix(".tmp")
    with temp_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(output_rows[0].keys()))
        writer.writeheader()
        writer.writerows(output_rows)
    temp_path.replace(OUTPUT_CSV)

    print("\nVALIDATION COMPLETE")
    print(f"Rows written: {len(output_rows)}")
    print(f"Results: {OUTPUT_CSV}")
    print("Predictions are suggestions, not calibrated probabilities.")

    for method, field in [
        ("Old center crop", "old_matches_human"),
        ("Fit and pad", "fit_pad_matches_human"),
    ]:
        correct = sum(
            str(r[field]).lower() == "true" for r in output_rows
        )
        print(f"{method}: {correct}/{len(output_rows)} exact label matches")

if __name__ == "__main__":
    main()