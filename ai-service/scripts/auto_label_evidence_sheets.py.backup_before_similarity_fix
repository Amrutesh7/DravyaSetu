from __future__ import annotations

import csv
import re
import sys
from pathlib import Path
from typing import Any

import open_clip
import torch
from PIL import Image, ImageOps

# ============================================================
# DravyaSetu paths
# ============================================================
ROOT = Path(r"D:\DravyaSetu\ai-service")
DATA_ROOT = ROOT / "data" / "DravyaSetu_v2_1492"
SHEETS_DIR = DATA_ROOT / "metadata" / "evidence_audit_sheets"
AUDIT_DIR = DATA_ROOT / "metadata" / "full_evidence_audit"

# Read-only human decisions; never modify this file.
DECISIONS_CSV = AUDIT_DIR / "evidence_review_decisions.csv"

# Output files are separate from the original review CSV.
OUTPUT_CSV = AUDIT_DIR / "evidence_auto_suggestions.csv"
FAILURES_CSV = AUDIT_DIR / "evidence_auto_suggestions_failures.csv"
BACKUP_CSV = AUDIT_DIR / "evidence_auto_suggestions_previous.csv"

# ============================================================
# OpenCLIP model
# ============================================================
MODEL_NAME = "ViT-B-32"
PRETRAINED_TAG = "laion2b_s34b_b79k"
DEVICE = "cpu"

# ============================================================
# Evidence categories and prompts
# ============================================================
CATEGORIES: dict[str, list[str]] = {
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

# Provisional triage thresholds only. These are not calibrated probabilities.
MIN_COSINE_MARGIN_FOR_HIGH_REVIEW = 0.03
MEDIUM_COSINE_MARGIN = 0.06
LOW_PRIORITY_MARGIN = 0.10
ALWAYS_REVIEW_CATEGORIES = {"UNCLEAR"}


def is_truthy(value: Any) -> bool:
    return str(value or "").strip().lower() in {"true", "1", "yes", "y"}


def get_observation_details(sheet_path: Path) -> tuple[str, str]:
    """Extract class name and observation ID from the sheet filename."""
    match = re.match(r"^(.*)_obs_(\d+)$", sheet_path.stem, re.IGNORECASE)
    if match:
        return match.group(1), match.group(2)
    return "", ""


def build_prompt_data() -> tuple[list[str], list[str]]:
    prompts: list[str] = []
    prompt_categories: list[str] = []
    for category, descriptions in CATEGORIES.items():
        for description in descriptions:
            prompts.append("A botanical evidence photograph: " + description)
            prompt_categories.append(category)
    return prompts, prompt_categories


def load_human_decisions() -> dict[tuple[str, str], dict[str, str]]:
    """
    Load only explicitly reviewed, valid labels.
    Uses (class_name, observation_id) as key to avoid ID collisions.
    The input CSV is never modified.
    """
    decisions: dict[tuple[str, str], dict[str, str]] = {}

    if not DECISIONS_CSV.exists():
        print("No reviewer decisions file found; continuing without it.")
        return decisions

    with DECISIONS_CSV.open("r", newline="", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)
        columns = set(reader.fieldnames or [])

        if not {"observation_id", "evidence_type"}.issubset(columns):
            print(
                "WARNING: decisions CSV is missing required columns "
                "'observation_id' and/or 'evidence_type'. Human labels not loaded."
            )
            return decisions

        for row in reader:
            observation_id = str(row.get("observation_id", "")).strip()
            class_name = str(row.get("class_name", "")).strip()
            label = str(row.get("evidence_type", "")).strip().upper()

            # If a reviewed column exists, require an explicit positive value.
            # If it does not exist, do not assume a label is confirmed.
            reviewed = (
                is_truthy(row.get("reviewed", ""))
                if "reviewed" in columns
                else False
            )

            if observation_id and reviewed and label in CATEGORIES:
                decisions[(class_name.casefold(), observation_id)] = row

    print(f"Verified human decisions loaded: {len(decisions)}")
    return decisions


def backup_output_if_needed() -> None:
    """Save the first existing suggestions CSV before replacing it."""
    if OUTPUT_CSV.exists() and not BACKUP_CSV.exists():
        BACKUP_CSV.write_bytes(OUTPUT_CSV.read_bytes())
        print(f"Previous suggestions backed up to: {BACKUP_CSV}")
    elif OUTPUT_CSV.exists() and BACKUP_CSV.exists():
        print(
            "A previous-output backup already exists and will not be overwritten."
        )


def get_review_priority(
    top_label: str,
    margin: float,
) -> tuple[str, str]:
    """
    Heuristic triage only. Must be calibrated against human-reviewed data.
    LOW means candidate for faster review, never automatic approval.
    """
    if top_label in ALWAYS_REVIEW_CATEGORIES:
        return "HIGH", "Leading category is UNCLEAR; human review required"

    if margin < MIN_COSINE_MARGIN_FOR_HIGH_REVIEW:
        return "HIGH", "Top two category similarities are too close"

    if margin < MEDIUM_COSINE_MARGIN:
        return "MEDIUM", "Small gap between top two categories; review recommended"

    if margin < LOW_PRIORITY_MARGIN:
        return "MEDIUM", "Moderate gap only; validate against human labels"

    return "LOW", "Larger relative gap; still a suggestion requiring validation"


def write_csv(
    path: Path,
    fieldnames: list[str],
    rows: list[dict[str, Any]],
) -> None:
    with path.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    if not SHEETS_DIR.exists():
        raise FileNotFoundError(f"Evidence sheets directory not found: {SHEETS_DIR}")

    AUDIT_DIR.mkdir(parents=True, exist_ok=True)
    sheets = sorted(
        path for path in SHEETS_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in {".jpg", ".jpeg", ".png"}
    )
    if not sheets:
        raise RuntimeError(f"No image sheets found in {SHEETS_DIR}")

    print(f"Sheets found: {len(sheets)}")
    print(f"Loading OpenCLIP {MODEL_NAME} ({PRETRAINED_TAG}) on {DEVICE}...")

    model, _, preprocess = open_clip.create_model_and_transforms(
        MODEL_NAME,
        pretrained=PRETRAINED_TAG,
        device=DEVICE,
    )
    tokenizer = open_clip.get_tokenizer(MODEL_NAME)
    model.eval()

    prompts, prompt_categories = build_prompt_data()
    with torch.no_grad():
        text_tokens = tokenizer(prompts).to(DEVICE)
        text_features = model.encode_text(text_tokens)
        text_features = text_features / text_features.norm(
            dim=-1, keepdim=True
        ).clamp_min(1e-12)

    category_prompt_indexes = {
        category: [
            i for i, prompt_category in enumerate(prompt_categories)
            if prompt_category == category
        ]
        for category in CATEGORIES
    }

    human_decisions = load_human_decisions()
    rows: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []

    for index, sheet_path in enumerate(sheets, start=1):
        class_name, observation_id = get_observation_details(sheet_path)

        try:
            with Image.open(sheet_path) as opened:
                image = ImageOps.exif_transpose(opened).convert("RGB")
                image_tensor = preprocess(image).unsqueeze(0).to(DEVICE)

            with torch.no_grad():
                image_features = model.encode_image(image_tensor)
                image_features = image_features / image_features.norm(
                    dim=-1, keepdim=True
                ).clamp_min(1e-12)

                # Cosine similarity only. These values are not probabilities.
                prompt_similarities = (image_features @ text_features.T)[0]

            # Average the prompt similarities within each category.
            category_scores: dict[str, float] = {}
            for category, prompt_indexes in category_prompt_indexes.items():
                category_scores[category] = float(
                    prompt_similarities[prompt_indexes].mean().item()
                )

            ranked = sorted(
                category_scores.items(),
                key=lambda item: item[1],
                reverse=True,
            )
            top_label, top_score = ranked[0]
            second_label, second_score = ranked[1]
            margin = top_score - second_score

            priority, reason = get_review_priority(top_label, margin)

            human = human_decisions.get(
                (class_name.casefold(), observation_id), {}
            )
            human_label = str(human.get("evidence_type", "")).strip().upper()
            human_reviewed = human_label in CATEGORIES

            rows.append({
                "observation_id": observation_id,
                "class_name": class_name,
                "sheet_filename": sheet_path.name,
                "sheet_path": str(sheet_path),
                "suggested_evidence_type": top_label,
                "top_candidate": top_label,
                "top_cosine_similarity": round(top_score, 6),
                "second_candidate": second_label,
                "second_cosine_similarity": round(second_score, 6),
                "cosine_similarity_margin": round(margin, 6),
                "review_priority": priority,
                "review_reason": reason,
                "human_reviewed": str(human_reviewed).lower(),
                "human_evidence_type": human_label if human_reviewed else "",
                "score_interpretation": "cosine similarity; not a probability",
                "model_name": f"OpenCLIP {MODEL_NAME} {PRETRAINED_TAG}",
                "status": "SUGGESTION_ONLY",
            })

        except Exception as exc:
            failures.append({
                "sheet_filename": sheet_path.name,
                "observation_id": observation_id,
                "class_name": class_name,
                "error": repr(exc),
            })

        if index % 25 == 0 or index == len(sheets):
            print(f"Processed {index}/{len(sheets)} sheets")

    output_fields = [
        "observation_id",
        "class_name",
        "sheet_filename",
        "sheet_path",
        "suggested_evidence_type",
        "top_candidate",
        "top_cosine_similarity",
        "second_candidate",
        "second_cosine_similarity",
        "cosine_similarity_margin",
        "review_priority",
        "review_reason",
        "human_reviewed",
        "human_evidence_type",
        "score_interpretation",
        "model_name",
        "status",
    ]

    # Preserve existing suggestions before overwriting the output file.
    backup_output_if_needed()
    write_csv(OUTPUT_CSV, output_fields, rows)

    if failures:
        write_csv(
            FAILURES_CSV,
            ["sheet_filename", "observation_id", "class_name", "error"],
            failures,
        )
        print(f"Failure details: {FAILURES_CSV}")
    elif FAILURES_CSV.exists():
        print(
            "No failures in this run. An older failure report was left untouched: "
            f"{FAILURES_CSV}"
        )

    print("\nDONE")
    print(f"Sheets found: {len(sheets)}")
    print(f"Suggestions written: {len(rows)}")
    print(f"Failures: {len(failures)}")
    print(f"Output: {OUTPUT_CSV}")
    print("Original human-review CSV and model weights were not modified.")
    print(
        "IMPORTANT: priorities are provisional heuristics, not validated "
        "accuracy estimates. Do not auto-accept suggestions."
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        raise
