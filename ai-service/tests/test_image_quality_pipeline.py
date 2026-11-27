from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

import numpy as np
import pytest
from PIL import Image

from app.inference.unknown_detector import UnknownDetector
from app.pipeline import DravyaSetuPipeline, PipelineConfig
from app.preprocessing.quality_checker import ImageQualityChecker
from app.schemas.response import AIResponse
from app.schemas.response_builder import ResponseBuilder


QUALITY_FIXTURE = (
    Path(__file__).resolve().parents[1] / "test_plant.jpg"
)


def blurred_fixture():
    with Image.open(QUALITY_FIXTURE) as image:
        return image.convert("RGB")


def solid_image(color, size=(640, 480)):
    return Image.new("RGB", size, color)


def checkerboard_image(size=(100, 100)):
    pixels = (
        np.indices((size[1], size[0])).sum(axis=0) % 2 * 255
    ).astype(np.uint8)
    return Image.fromarray(
        np.repeat(pixels[..., None], 3, axis=2),
        "RGB",
    )


def valid_texture_image():
    pixels = np.random.default_rng(15).integers(
        50,
        206,
        (480, 640, 3),
        dtype=np.uint8,
    )
    return Image.fromarray(pixels, "RGB")


def build_pipeline():
    top_prediction = SimpleNamespace(
        class_name="Amla",
        class_index=1,
        confidence=0.99,
    )
    predictor = Mock()
    predictor.predict.return_value = SimpleNamespace(
        top_prediction=top_prediction,
        alternatives=[top_prediction],
    )

    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.predictor = predictor
    pipeline.quality_checker = ImageQualityChecker()
    pipeline.unknown_detector = UnknownDetector()
    pipeline.config = PipelineConfig()
    pipeline.response_builder = ResponseBuilder()
    pipeline._add_gradcam = Mock()
    return pipeline


@pytest.mark.parametrize(
    ("image_factory", "expected_issue"),
    [
        (blurred_fixture, "BLURRY"),
        (lambda: solid_image((0, 0, 0)), "TOO_DARK"),
        (lambda: solid_image((255, 255, 255)), "OVEREXPOSED"),
        (checkerboard_image, "LOW_RESOLUTION"),
    ],
)
def test_quality_checker_rejects_existing_failure_cases(
    image_factory,
    expected_issue,
):
    checker = ImageQualityChecker()
    image = image_factory()

    result = checker.check(image)

    assert result.acceptable is False
    assert expected_issue in result.issues
    assert result.user_messages


def test_quality_checker_accepts_valid_image():
    result = ImageQualityChecker().check(valid_texture_image())

    assert result.acceptable is True
    assert result.issues == []
    assert result.user_messages == []
    assert result.width == 640
    assert result.height == 480


def test_quality_checker_reuses_rgb_image_without_exif_copy():
    image = valid_texture_image()

    assert ImageQualityChecker._load_image(image) is image


def test_quality_checker_still_applies_exif_orientation():
    image = valid_texture_image()
    exif = Image.Exif()
    exif[274] = 6
    image.info["exif"] = exif.tobytes()

    oriented = ImageQualityChecker._load_image(image)

    try:
        assert oriented is not image
        assert oriented.size == (image.height, image.width)
    finally:
        oriented.close()
        image.close()


@pytest.mark.parametrize(
    ("image_factory", "expected_issue"),
    [
        (blurred_fixture, "BLURRY"),
        (lambda: solid_image((0, 0, 0)), "TOO_DARK"),
        (lambda: solid_image((255, 255, 255)), "OVEREXPOSED"),
        (checkerboard_image, "LOW_RESOLUTION"),
    ],
)
def test_pipeline_rejects_poor_quality_before_prediction(
    image_factory,
    expected_issue,
):
    pipeline = build_pipeline()
    response = pipeline.analyze_image(
        image=image_factory(),
        image_id="poor-quality.jpg",
        request_id="quality-rejection",
    )
    validated = AIResponse.model_validate(
        response.model_dump(mode="json")
    )

    assert validated.image_quality.acceptable is False
    assert expected_issue in validated.image_quality.issues
    assert validated.identification.status == "UNKNOWN"
    assert validated.identification.plant_id is None
    assert validated.identification.confidence == 0.0
    assert validated.alternatives == []
    assert validated.similar_species == []
    assert any(
        ImageQualityChecker.DEFAULT_MESSAGES[expected_issue] in warning
        for warning in validated.warnings
    )
    assert any(
        "did not pass the image-quality checks" in warning
        for warning in validated.warnings
    )
    pipeline.predictor.predict.assert_not_called()
    pipeline._add_gradcam.assert_not_called()


def test_pipeline_accepts_valid_quality_and_runs_prediction():
    pipeline = build_pipeline()
    response = pipeline.analyze_image(
        image=valid_texture_image(),
        image_id="valid-quality.jpg",
        request_id="quality-accepted",
    )
    validated = AIResponse.model_validate(
        response.model_dump(mode="json")
    )

    assert validated.image_quality.acceptable is True
    assert validated.image_quality.issues == []
    assert validated.identification.status == "KNOWN"
    assert validated.identification.plant_id == "PLANT_002"
    assert validated.identification.confidence == pytest.approx(0.99)
    assert validated.warnings == []
    pipeline.predictor.predict.assert_called_once()
    pipeline._add_gradcam.assert_called_once()
