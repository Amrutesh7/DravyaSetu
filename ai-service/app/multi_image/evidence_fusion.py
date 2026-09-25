from collections import defaultdict


def fuse_predictions(
    predictions_per_image: list[list[dict]]
) -> dict:
    """
    Combine predictions from multiple images.

    Each image should provide a list of predictions
    containing:
        plant_id
        confidence
        common_name
        scientific_name

    The confidence scores are averaged across images
    for each plant.
    """

    if not predictions_per_image:
        raise ValueError(
            "At least one image prediction is required."
        )

    plant_scores = defaultdict(list)
    plant_metadata = {}

    # Collect confidence scores for each plant
    for predictions in predictions_per_image:

        if not predictions:
            continue

        for prediction in predictions:

            plant_id = prediction["plant_id"]
            confidence = float(
                prediction["confidence"]
            )

            plant_scores[plant_id].append(
                confidence
            )

            plant_metadata[plant_id] = prediction

    if not plant_scores:
        raise ValueError(
            "No valid predictions were provided."
        )

    # Average confidence for each plant
    fused_predictions = []

    for plant_id, scores in plant_scores.items():

        average_confidence = sum(scores) / len(scores)

        metadata = plant_metadata[plant_id]

        fused_predictions.append({
            "plant_id": plant_id,
            "common_name": metadata["common_name"],
            "scientific_name": metadata["scientific_name"],
            "confidence": average_confidence,
            "images_supporting": len(scores)
        })

    # Highest combined confidence first
    fused_predictions.sort(
        key=lambda x: x["confidence"],
        reverse=True
    )

    best_prediction = fused_predictions[0]

    return {
        "used": len(predictions_per_image) > 1,
        "image_count": len(predictions_per_image),
        "prediction": best_prediction,
        "alternatives": fused_predictions[1:]
    }