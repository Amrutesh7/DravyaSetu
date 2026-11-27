
"""Regression tests for DravyaSetu multi-image evidence fusion."""

import pytest

from app.multi_image.evidence_fusion import (
    EvidenceFusion,
    ImageEvidence,
)


@pytest.fixture
def fusion():
    return EvidenceFusion()


def make_evidence(
    image_id,
    predicted_class,
    confidence=0.98,
    evidence_type="OTHER",
    observation_id="test_observation",
):
    return ImageEvidence(
        image_id=image_id,
        predicted_class=predicted_class,
        confidence=confidence,
        evidence_type=evidence_type,
        observation_id=observation_id,
    )


def test_unanimous_predictions_are_accepted(fusion):
    evidence = [
        make_evidence("image_1", 7),
        make_evidence("image_2", 7),
        make_evidence("image_3", 7),
    ]

    result = fusion.fuse(evidence)

    assert result.predicted_class == 7
    assert result.accepted is True
    assert result.agreement_ratio == pytest.approx(1.0)
    assert result.image_count == 3


def test_strong_disagreement_is_rejected(fusion):
    evidence = [
        make_evidence("image_1", 7),
        make_evidence("image_2", 7),
        make_evidence("image_3", 7),
        make_evidence("image_4", 12),
        make_evidence("image_5", 12),
    ]

    result = fusion.fuse(evidence)

    assert result.accepted is False
    assert result.agreement_ratio < 0.80
    assert result.image_count == 5
    assert len(result.supporting_images) == 3


def test_different_evidence_types_are_supported(fusion):
    evidence = [
        make_evidence("leaf_1", 4, evidence_type="LEAF"),
        make_evidence("whole_1", 4, evidence_type="WHOLE_PLANT"),
        make_evidence("flower_1", 4, evidence_type="FLOWER"),
    ]

    result = fusion.fuse(evidence)

    assert result.predicted_class == 4
    assert result.accepted is True
    assert result.agreement_ratio == pytest.approx(1.0)


def test_all_supported_evidence_types_can_enter_fusion(fusion):
    evidence_types = [
        "WHOLE_PLANT",
        "LEAF",
        "FLOWER",
        "FRUIT",
        "STEM_BARK",
        "ROOT",
        "SEED",
        "OTHER",
    ]
    evidence = [
        make_evidence(
            f"image_{index}",
            4,
            evidence_type=evidence_type,
        )
        for index, evidence_type in enumerate(evidence_types)
    ]

    result = fusion.fuse(evidence)

    assert result.predicted_class == 4
    assert result.accepted is True
    assert result.image_count == len(evidence_types)
    assert result.supporting_images == [
        f"image_{index}"
        for index in range(len(evidence_types))
    ]


def test_five_seed_one_whole_plant_regression(fusion):
    evidence = [
        make_evidence(
            f"seed_{index}",
            predicted_class=7,
            confidence=0.99,
            evidence_type="SEED",
        )
        for index in range(5)
    ]
    evidence.append(
        make_evidence(
            "whole_plant",
            predicted_class=12,
            confidence=0.99,
            evidence_type="WHOLE_PLANT",
        )
    )

    typed_result = fusion.fuse(evidence)

    assert typed_result.accepted is False
    assert typed_result.agreement_ratio == pytest.approx(
        0.7894736842,
    )

    fallback_evidence = [
        make_evidence(
            item.image_id,
            predicted_class=item.predicted_class,
            confidence=item.confidence,
            evidence_type="OTHER",
            observation_id=item.observation_id,
        )
        for item in evidence
    ]
    fallback_result = fusion.fuse(fallback_evidence)

    assert fallback_result.accepted is True
    assert fallback_result.agreement_ratio == pytest.approx(
        0.8333333333,
    )


def test_supporting_images_are_returned(fusion):
    evidence = [
        make_evidence("image_1", 7),
        make_evidence("image_2", 7),
        make_evidence("image_3", 12),
    ]

    result = fusion.fuse(evidence)

    assert "image_1" in result.supporting_images
    assert "image_2" in result.supporting_images
    assert "image_3" not in result.supporting_images


def test_empty_evidence_is_rejected(fusion):
    with pytest.raises(ValueError):
        fusion.fuse([])


def test_different_observations_are_rejected(fusion):
    evidence = [
        make_evidence(
            "image_1",
            7,
            observation_id="observation_A",
        ),
        make_evidence(
            "image_2",
            7,
            observation_id="observation_B",
        ),
    ]

    with pytest.raises(ValueError):
        fusion.fuse(evidence)
