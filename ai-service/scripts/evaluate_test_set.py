
from pathlib import Path
import csv
import json

from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
)

from app.inference.predictor import build_predictor


TEST_DIR = Path(r"D:\DravyaSetu\datasets\final_split\test")
OUTPUT_DIR = Path("artifacts/evaluation")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def main():
    class_dirs = sorted(
        [p for p in TEST_DIR.iterdir() if p.is_dir()],
        key=lambda p: p.name,
    )

    if not class_dirs:
        raise RuntimeError(f"No class folders found in {TEST_DIR}")

    image_items = []
    for folder in class_dirs:
        for image_path in sorted(folder.rglob("*")):
            if image_path.is_file() and image_path.suffix.lower() in EXTENSIONS:
                image_items.append((folder.name, image_path))

    print("Test images found:", len(image_items))
    print("Plant classes:", len(class_dirs))
    print("Loading existing trained checkpoint...")

    predictor = build_predictor(
        num_classes=34,
        pretrained=False,
        device="cpu",
    )

    true_labels = []
    predicted_labels = []
    rows = []
    errors = []

    for number, (true_label, image_path) in enumerate(image_items, start=1):
        try:
            result = predictor.predict(image_path)
            prediction = result.top_prediction

            true_labels.append(true_label)
            predicted_labels.append(prediction.class_name)

            rows.append({
                "image_path": str(image_path),
                "true_label": true_label,
                "predicted_label": prediction.class_name,
                "correct": true_label == prediction.class_name,
                "confidence": prediction.confidence,
            })

        except Exception as exc:
            errors.append({
                "image_path": str(image_path),
                "error": repr(exc),
            })

        if number % 50 == 0 or number == len(image_items):
            print(f"Processed {number}/{len(image_items)}")

    if not rows:
        raise RuntimeError("No images were successfully evaluated.")

    labels = [folder.name for folder in class_dirs]

    accuracy = accuracy_score(true_labels, predicted_labels)
    report = classification_report(
        true_labels,
        predicted_labels,
        labels=labels,
        output_dict=True,
        zero_division=0,
    )
    matrix = confusion_matrix(
        true_labels,
        predicted_labels,
        labels=labels,
    )

    def write_csv(path, fieldnames, data):
        with path.open("w", newline="", encoding="utf-8-sig") as file:
            writer = csv.DictWriter(file, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(data)

    prediction_fields = [
        "image_path",
        "true_label",
        "predicted_label",
        "correct",
        "confidence",
    ]

    write_csv(
        OUTPUT_DIR / "test_predictions.csv",
        prediction_fields,
        rows,
    )

    write_csv(
        OUTPUT_DIR / "misclassified_images.csv",
        prediction_fields,
        [row for row in rows if not row["correct"]],
    )

    class_metrics = []
    for label in labels:
        metrics = report[label]
        class_metrics.append({
            "class_name": label,
            "support": int(metrics["support"]),
            "precision": metrics["precision"],
            "recall": metrics["recall"],
            "f1_score": metrics["f1-score"],
        })

    write_csv(
        OUTPUT_DIR / "per_class_metrics.csv",
        ["class_name", "support", "precision", "recall", "f1_score"],
        class_metrics,
    )

    with (OUTPUT_DIR / "confusion_matrix.csv").open(
        "w", newline="", encoding="utf-8-sig"
    ) as file:
        writer = csv.writer(file)
        writer.writerow(["true\\predicted"] + labels)
        for label, values in zip(labels, matrix):
            writer.writerow([label] + values.tolist())

    summary = {
        "images_found": len(image_items),
        "images_processed": len(rows),
        "processing_errors": len(errors),
        "correct": sum(row["correct"] for row in rows),
        "incorrect": sum(not row["correct"] for row in rows),
        "accuracy": accuracy,
        "macro_precision": report["macro avg"]["precision"],
        "macro_recall": report["macro avg"]["recall"],
        "macro_f1": report["macro avg"]["f1-score"],
        "weighted_f1": report["weighted avg"]["f1-score"],
    }

    with (OUTPUT_DIR / "evaluation_summary.json").open(
        "w", encoding="utf-8"
    ) as file:
        json.dump(summary, file, indent=2)

    with (OUTPUT_DIR / "evaluation_errors.json").open(
        "w", encoding="utf-8"
    ) as file:
        json.dump(errors, file, indent=2)

    print("\n===== EVALUATION SUMMARY =====")
    print("Images found:", summary["images_found"])
    print("Images processed:", summary["images_processed"])
    print("Processing errors:", summary["processing_errors"])
    print("Correct:", summary["correct"])
    print("Incorrect:", summary["incorrect"])
    print(f"Accuracy: {accuracy * 100:.2f}%")
    print(f"Macro F1: {summary['macro_f1'] * 100:.2f}%")
    print(f"Weighted F1: {summary['weighted_f1'] * 100:.2f}%")
    print("\nReports saved to:", OUTPUT_DIR.resolve())


if __name__ == "__main__":
    main()