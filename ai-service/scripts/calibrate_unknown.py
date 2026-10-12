from __future__ import annotations

import json
import os

import numpy as np


def percentile_threshold(
    values,
    percentile,
):
    return float(
        np.percentile(
            np.asarray(values),
            percentile,
        )
    )


def main():

    """
    Calibration should be performed ONLY on validation data.

    Expected input:

        validation_unknown_scores.npz

    containing:

        classifier_confidence
        embedding_similarity
        prototype_similarity
        labels
        correct

    `correct` indicates whether the classifier prediction
    was correct for that validation image.
    """

    input_path = (
        "artifacts/embeddings/"
        "validation_unknown_scores.npz"
    )

    output_path = (
        "artifacts/models/"
        "unknown_detection_config.json"
    )

    if not os.path.exists(input_path):

        raise FileNotFoundError(
            f"Missing validation scores: {input_path}"
        )

    data = np.load(input_path)

    classifier_confidence = data[
        "classifier_confidence"
    ]

    embedding_similarity = data[
        "embedding_similarity"
    ]

    prototype_similarity = data[
        "prototype_similarity"
    ]

    correct = data[
        "correct"
    ].astype(bool)

    # We want thresholds that reject suspicious
    # validation examples while retaining genuine
    # known-class examples.

    known_classifier = classifier_confidence[
        correct
    ]

    known_embedding = embedding_similarity[
        correct
    ]

    known_prototype = prototype_similarity[
        correct
    ]

    if len(known_classifier) == 0:

        raise RuntimeError(
            "No correct validation predictions available."
        )

    # Conservative starting calibration.
    #
    # These are not universal magic numbers.
    # They are derived from validation distributions.

    confidence_threshold = percentile_threshold(
        known_classifier,
        10,
    )

    embedding_threshold = percentile_threshold(
        known_embedding,
        10,
    )

    prototype_threshold = percentile_threshold(
        known_prototype,
        10,
    )

    config = {

        "method": "hybrid_unknown_detection",

        "threshold_source": "validation",

        "confidence_threshold":
            confidence_threshold,

        "embedding_threshold":
            embedding_threshold,

        "prototype_threshold":
            prototype_threshold,

        "minimum_accept_signals": 2,

        "weights": {

            "classifier": 0.40,

            "embedding": 0.30,

            "prototype": 0.30,
        },
    }

    os.makedirs(
        os.path.dirname(output_path),
        exist_ok=True,
    )

    with open(
        output_path,
        "w",
        encoding="utf-8",
    ) as f:

        json.dump(
            config,
            f,
            indent=4,
        )

    print(
        json.dumps(
            config,
            indent=4,
        )
    )


if __name__ == "__main__":
    main()