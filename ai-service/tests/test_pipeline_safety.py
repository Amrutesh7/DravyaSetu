import asyncio
import base64
import io
import json
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock
from uuid import uuid4

import numpy as np
import pytest
import torch
import jsonschema
from PIL import Image
from torch import nn

from app.explainability.gradcam import GradCAM
from app.inference.predictor import PlantPredictor
from app.inference.unknown_detector import UnknownDetector
from app.multi_image.evidence_fusion import EvidenceFusion
from app.pipeline import DravyaSetuPipeline, PipelineConfig
from app.api.upload_limits import (
    AnalyzeRequestBodyLimitMiddleware,
    UploadClientDisconnected,
)
from app.preprocessing.image_preprocessor import ImagePreprocessor
from app.preprocessing.quality_checker import ImageQualityChecker
from app.schemas.response import AIResponse
from app.schemas.response_builder import (
    InternalAlternative,
    InternalImageResult,
    InternalSimilarSpecies,
    ResponseBuilder,
)


class TinyClassifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.num_classes = 2
        self.grad_modes = []
        self.model = nn.Module()
        self.model.features = nn.Sequential(
            nn.Conv2d(3, 3, kernel_size=1, bias=False),
            nn.ReLU(),
        )
        self.model.pool = nn.AdaptiveAvgPool2d(1)
        self.model.classifier = nn.Linear(3, 2, bias=False)

    def forward(self, tensor):
        self.grad_modes.append(torch.is_grad_enabled())
        features = self.model.features(tensor)
        if getattr(self, "raise_after_features", False):
            raise RuntimeError("controlled model failure")
        return self.model.classifier(
            self.model.pool(features).flatten(1)
        )


class TinyPreprocessor:
    def preprocess(self, image):
        pixels = np.asarray(image.convert("RGB"), dtype=np.float32) / 255.0
        return torch.from_numpy(pixels).permute(2, 0, 1).unsqueeze(0)


class TinyConfidenceCalculator:
    def top_k(self, logits, k):
        probabilities = torch.softmax(logits, dim=1)
        values, indices = torch.topk(probabilities, k=k, dim=1)
        return [[
            SimpleNamespace(
                class_index=int(index),
                confidence=float(value),
            )
            for value, index in zip(values[0], indices[0])
        ]]


def build_predictor(preprocessor=None):
    torch.manual_seed(7)
    return PlantPredictor(
        model=TinyClassifier(),
        preprocessor=preprocessor or TinyPreprocessor(),
        confidence_calculator=TinyConfidenceCalculator(),
        class_names=["Plant_A", "Plant_B"],
        device="cpu",
    )


@pytest.mark.parametrize(
    "size",
    [
        (4032, 3024),
        (3024, 4032),
        (5000, 4000),
        (4000, 1000),
    ],
)
def test_high_resolution_preprocessing_and_gradcam(size):
    width, height = size
    pixels = np.random.default_rng(34).integers(
        50,
        206,
        (min(512, height), min(512, width), 3),
        dtype=np.uint8,
    )
    thumbnail = Image.fromarray(pixels, "RGB")
    image = thumbnail.resize(size, Image.Resampling.NEAREST)
    thumbnail.close()

    preprocessor = ImagePreprocessor(image_size=300)
    predictor = build_predictor(preprocessor=preprocessor)
    gradcam = GradCAM(predictor)
    result = None

    try:
        quality = ImageQualityChecker().check(image)
        assert quality.acceptable is True
        assert preprocessor.load_image(image) is image

        tensor = preprocessor.preprocess(image)
        assert tuple(tensor.shape) == (1, 3, 300, 300)

        result = gradcam.generate(image)
        scale = min(1.0, 1024 / max(size))
        expected_size = (
            max(1, round(width * scale)),
            max(1, round(height * scale)),
        )
        assert result["original_image"].size == expected_size
        assert result["heatmap_image"].size == expected_size
        assert result["overlay_image"].size == expected_size
    finally:
        if result is not None:
            for key in ("original_image", "heatmap_image", "overlay_image"):
                result[key].close()
        gradcam.close()
        image.close()


def test_extreme_aspect_ratio_is_rejected_by_existing_quality_policy():
    pixels = np.random.default_rng(34).integers(
        50,
        206,
        (512, 512, 3),
        dtype=np.uint8,
    )
    thumbnail = Image.fromarray(pixels, "RGB")
    image = thumbnail.resize((5000, 1000), Image.Resampling.NEAREST)
    thumbnail.close()

    try:
        quality = ImageQualityChecker().check(image)
        assert quality.acceptable is False
        assert quality.issues == ["INVALID_ASPECT_RATIO"]
    finally:
        image.close()


def build_unknown_test_pipeline(class_name, confidence, alternatives):
    top_prediction = SimpleNamespace(
        class_name=class_name,
        class_index=0,
        confidence=confidence,
    )
    alternative_predictions = [top_prediction] + [
        SimpleNamespace(
            class_name=name,
            class_index=index,
            confidence=value,
        )
        for index, (name, value) in enumerate(alternatives, start=1)
    ]

    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.predictor = SimpleNamespace(
        predict=lambda image: SimpleNamespace(
            top_prediction=top_prediction,
            alternatives=alternative_predictions,
        )
    )
    pipeline.quality_checker = SimpleNamespace(
        check=lambda image: SimpleNamespace(
            acceptable=True,
            issues=[],
            user_messages=[],
        )
    )
    pipeline.unknown_detector = UnknownDetector()
    pipeline.config = PipelineConfig()
    pipeline.response_builder = ResponseBuilder()
    pipeline._add_gradcam = Mock()
    return pipeline


def make_result(color):
    image = Image.new("RGB", (16, 16), color=color)
    return {
        "original_image": image,
        "heatmap_image": image.copy(),
        "overlay_image": image.copy(),
    }


def test_pipeline_keeps_images_evidence_and_observation_ids_aligned():
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    analyzed = []

    def analyze_one(**kwargs):
        analyzed.append(kwargs)
        return SimpleNamespace(quality_acceptable=False)

    pipeline._analyze_one = analyze_one
    pipeline.response_builder = SimpleNamespace(
        build_multi_image_response=lambda **kwargs: kwargs
    )

    images = [
        Image.new("RGB", (2, 2), color=color)
        for color in ("red", "green", "blue")
    ]
    image_ids = ["leaf.jpg", "flower.jpg", "fruit.jpg"]
    evidence_types = ["LEAF", "FLOWER", "FRUIT"]

    pipeline.analyze_images(
        images=images,
        image_ids=image_ids,
        evidence_types=evidence_types,
        request_id="observation-123",
    )

    assert [item["image"] for item in analyzed] == images
    assert [item["image_id"] for item in analyzed] == image_ids
    assert [item["evidence_type"] for item in analyzed] == evidence_types
    assert {item["observation_id"] for item in analyzed} == {
        "observation-123"
    }


def build_multi_image_test_pipeline(class_indices):
    class_names = {0: "Amla", 1: "Neem"}
    predictions = iter(class_indices)

    def predict(image):
        class_index = next(predictions)
        prediction = SimpleNamespace(
            class_name=class_names[class_index],
            class_index=class_index,
            confidence=0.9,
        )
        return SimpleNamespace(
            top_prediction=prediction,
            alternatives=[prediction],
        )

    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.predictor = SimpleNamespace(predict=predict)
    pipeline.quality_checker = SimpleNamespace(
        check=lambda image: SimpleNamespace(
            acceptable=True,
            issues=[],
            user_messages=[],
        )
    )
    pipeline.unknown_detector = SimpleNamespace(
        detect=lambda **kwargs: SimpleNamespace(status="KNOWN")
    )
    pipeline.config = PipelineConfig()
    fusion_results = []

    class TrackingFusion:
        def fuse(self, evidence):
            result = EvidenceFusion().fuse(evidence)
            fusion_results.append((evidence, result))
            return result

    pipeline.evidence_fusion = TrackingFusion()
    pipeline.response_builder = ResponseBuilder()
    pipeline._add_gradcam = Mock()
    return pipeline, fusion_results


def test_three_image_consensus_preserves_fusion_and_response_contract():
    pipeline, fusion_results = build_multi_image_test_pipeline([0, 0, 0])
    image_ids = ["leaf.jpg", "flower.jpg", "fruit.jpg"]
    evidence_types = ["LEAF", "FLOWER", "FRUIT"]

    response = pipeline.analyze_images(
        images=[
            Image.new("RGB", (4, 4), color=color)
            for color in ("red", "yellow", "blue")
        ],
        image_ids=image_ids,
        evidence_types=evidence_types,
        request_id="consensus-observation",
    )

    evidence, fused = fusion_results[0]
    assert [item.image_id for item in evidence] == image_ids
    assert [item.evidence_type for item in evidence] == evidence_types
    assert fused.accepted is True
    assert fused.image_count == 3
    assert len(fused.supporting_images) == 3
    assert response.request_id == "consensus-observation"
    assert response.identification.status == "KNOWN"
    assert response.identification.confidence == pytest.approx(0.9)
    assert response.multi_image.used is True
    assert response.multi_image.image_count == 3
    assert response.explanation.method == "GRAD_CAM"
    assert all(
        call.kwargs["request_id"] == "consensus-observation"
        for call in pipeline._add_gradcam.call_args_list
    )


def test_three_image_gradcam_urls_and_response_order_remain_positionally_attached():
    image_ids = ["leaf.jpg", "flower.jpg", "fruit.jpg"]
    evidence_types = ["LEAF", "FLOWER", "FRUIT"]

    for request_number in range(2):
        pipeline, _ = build_multi_image_test_pipeline([0, 1, 0])

        def attach_distinct_gradcam(result, image, request_id):
            result.heatmap_data = (
                base64.b64encode(
                    result.image_id.encode("ascii")
                ).decode("ascii")
            )
            result.heatmap_url = (
                f"/artifacts/gradcam/{request_id}_{result.image_id}.jpg"
            )
            result.heatmap_media_type = "image/jpeg"

        pipeline._add_gradcam.side_effect = attach_distinct_gradcam
        request_id = f"strict-order-{request_number}"

        response = pipeline.analyze_images(
            images=[
                Image.new("RGB", (4, 4), color=color)
                for color in ("red", "yellow", "blue")
            ],
            image_ids=image_ids,
            evidence_types=evidence_types,
            request_id=request_id,
        )

        heatmaps = response.explanation.heatmaps
        assert [heatmap.image_index for heatmap in heatmaps] == [1, 2, 3]
        assert [heatmap.image_data for heatmap in heatmaps] == [
            base64.b64encode(
                image_id.encode("ascii")
            ).decode("ascii")
            for image_id in image_ids
        ]

        gradcam_calls = pipeline._add_gradcam.call_args_list
        assert [
            call.kwargs["result"].image_id
            for call in gradcam_calls
        ] == image_ids
        assert [
            call.kwargs["image"].getpixel((0, 0))
            for call in gradcam_calls
        ] == [(255, 0, 0), (255, 255, 0), (0, 0, 255)]


def test_two_image_conflict_is_unknown_and_reports_both_images():
    pipeline, fusion_results = build_multi_image_test_pipeline([0, 1])
    image_ids = ["leaf.jpg", "flower.jpg"]
    evidence_types = ["LEAF", "FLOWER"]

    response = pipeline.analyze_images(
        images=[
            Image.new("RGB", (4, 4), color="red"),
            Image.new("RGB", (4, 4), color="yellow"),
        ],
        image_ids=image_ids,
        evidence_types=evidence_types,
        request_id="conflict-observation",
    )

    evidence, fused = fusion_results[0]
    assert [item.image_id for item in evidence] == image_ids
    assert [item.evidence_type for item in evidence] == evidence_types
    assert fused.accepted is False
    assert fused.image_count == 2
    assert len(fused.supporting_images) == 1
    assert response.identification.status == "UNKNOWN"
    assert response.identification.plant_id is None
    assert response.multi_image.used is True
    assert response.multi_image.image_count == 2
    assert response.alternatives == []
    assert response.similar_species == []


def test_single_image_evidence_type_is_forwarded():
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline._analyze_one = Mock(
        return_value=SimpleNamespace(quality_acceptable=False)
    )
    pipeline.response_builder = SimpleNamespace(
        build_single_image_response=lambda **kwargs: kwargs
    )

    pipeline.analyze_image(
        image=Image.new("RGB", (2, 2)),
        image_id="flower.jpg",
        request_id="observation-123",
        evidence_type="FLOWER",
    )

    assert (
        pipeline._analyze_one.call_args.kwargs["evidence_type"]
        == "FLOWER"
    )


def test_single_image_evidence_type_is_stored_in_internal_result():
    pipeline = build_unknown_test_pipeline(
        class_name="Amla",
        confidence=0.99,
        alternatives=[],
    )

    result = pipeline._analyze_one(
        image=Image.new("RGB", (16, 16), color="green"),
        image_id="flower.jpg",
        evidence_type="FLOWER",
        observation_id="observation-123",
    )

    assert result.evidence_type == "FLOWER"
    assert result.observation_id == "observation-123"


def test_single_image_pipeline_builds_canonical_response(monkeypatch):
    import app.pipeline as pipeline_module
    from app.models.plant_registry import get_plant_by_id, get_plant_by_name

    top_prediction = SimpleNamespace(
        class_name="Amla",
        class_index=0,
        confidence=0.95,
    )
    predictor = SimpleNamespace(
        predict=lambda image: SimpleNamespace(
            top_prediction=top_prediction,
            alternatives=[top_prediction],
        )
    )
    quality_checker = SimpleNamespace(
        check=lambda image: SimpleNamespace(
            acceptable=True,
            issues=[],
            user_messages=[],
        )
    )
    unknown_detector = SimpleNamespace(
        detect=lambda **kwargs: SimpleNamespace(status="KNOWN")
    )
    monkeypatch.setattr(pipeline_module, "build_gradcam", lambda predictor: None)

    pipeline = DravyaSetuPipeline(
        predictor=predictor,
        quality_checker=quality_checker,
        confidence_calculator=None,
        unknown_detector=unknown_detector,
        embedding_extractor=None,
        evidence_fusion=None,
        response_builder=ResponseBuilder(),
        config=PipelineConfig(),
    )
    pipeline._add_gradcam = Mock()

    def add_gradcam(result, image, request_id):
        result.heatmap_data = "aGVsbG8="
        result.heatmap_url = (
            "/artifacts/gradcam/single-pipeline-request_leaf.jpg"
        )
        result.heatmap_media_type = "image/jpeg"

    pipeline._add_gradcam.side_effect = add_gradcam

    response = pipeline.analyze_image(
        image=Image.new("RGB", (16, 16), color="green"),
        image_id="leaf.jpg",
        request_id="single-pipeline-request",
        model_version="test-model",
        evidence_type="LEAF",
    )

    assert response.request_id == "single-pipeline-request"
    assert response.model_version == "test-model"
    assert response.identification.status == "KNOWN"
    registry_entry = get_plant_by_name("Amla")
    assert registry_entry["plant_id"] == "PLANT_002"
    assert (
        get_plant_by_id(response.identification.plant_id)[
            "plant_identifier"
        ]
        == "Amla"
    )
    assert response.identification.plant_id == registry_entry["plant_id"]
    assert 0.95 <= response.identification.confidence <= 1.0
    assert response.image_quality.acceptable is True
    assert response.image_quality.issues == []
    assert response.explanation.method == "GRAD_CAM"
    assert len(response.explanation.heatmaps) == 1
    assert response.explanation.heatmaps[0].image_index == 1
    assert (
        response.explanation.heatmaps[0].image_data
        == "aGVsbG8="
    )
    assert response.explanation.heatmaps[0].media_type == "image/jpeg"
    assert response.multi_image.used is False
    assert response.multi_image.image_count == 1
    pipeline._add_gradcam.assert_called_once()
    assert (
        pipeline._add_gradcam.call_args.kwargs["request_id"]
        == "single-pipeline-request"
    )


def test_low_confidence_prediction_returns_unknown_contract_response():
    unknown_baseline = json.loads(
        (
            Path(__file__).resolve().parents[2]
            / "shared"
            / "sample-data"
            / "sample-ai-response.json"
        ).read_text(encoding="utf-8")
    )["unknown_case"]
    expected_identification = unknown_baseline["identification"]

    pipeline = build_unknown_test_pipeline(
        class_name="Amla",
        confidence=expected_identification["confidence"],
        alternatives=[("Neem", 0.18)],
    )
    detector_result = pipeline.unknown_detector.detect(
        classifier_confidence=expected_identification["confidence"],
    )
    assert detector_result.status == "UNKNOWN"
    assert detector_result.accepted is False
    assert pipeline.unknown_detector.confidence_threshold == 0.95
    assert (
        pipeline.unknown_detector.detect(
            classifier_confidence=0.9499,
        ).status
        == "UNKNOWN"
    )
    assert (
        pipeline.unknown_detector.detect(
            classifier_confidence=0.95,
        ).status
        == "KNOWN"
    )

    response = pipeline.analyze_image(
        image=Image.new("RGB", (16, 16), color="green"),
        image_id="unknown-plant.jpg",
        request_id="low-confidence-unknown",
    )
    validated = AIResponse.model_validate(
        response.model_dump(mode="json")
    )

    assert (
        validated.identification.status
        == expected_identification["status"]
    )
    assert (
        validated.identification.plant_id
        == expected_identification["plant_id"]
    )
    assert validated.identification.confidence == pytest.approx(
        expected_identification["confidence"]
    )
    assert validated.alternatives == unknown_baseline["alternatives"]
    assert (
        validated.similar_species
        == unknown_baseline["similar_species"]
    )
    assert validated.image_quality.acceptable is True


def test_unregistered_high_confidence_prediction_stays_unknown(caplog):
    pipeline = build_unknown_test_pipeline(
        class_name="Unsupported_Plant",
        confidence=0.99,
        alternatives=[("Amla", 0.01)],
    )
    assert (
        pipeline.unknown_detector.detect(
            classifier_confidence=0.99,
        ).status
        == "KNOWN"
    )

    response = pipeline.analyze_image(
        image=Image.new("RGB", (16, 16), color="green"),
        image_id="unsupported-plant.jpg",
        request_id="unregistered-class",
    )
    validated = AIResponse.model_validate(
        response.model_dump(mode="json")
    )

    assert validated.identification.status == "UNKNOWN"
    assert validated.identification.plant_id is None
    assert validated.identification.confidence == pytest.approx(0.99)
    assert validated.alternatives == []
    assert validated.similar_species == []
    assert "missing from the official registry" in caplog.text


def test_unknown_response_builder_discards_candidate_suggestions():
    result = InternalImageResult(
        image_id="unknown-plant.jpg",
        quality_acceptable=True,
        status="UNKNOWN",
        confidence=0.21,
        alternatives=[
            InternalAlternative(
                plant_id="PLANT_002",
                common_name="Indian Gooseberry",
                scientific_name="Phyllanthus emblica L.",
                confidence=0.21,
            )
        ],
        similar_species=[
            InternalSimilarSpecies(
                plant_id="PLANT_002",
                name="Indian Gooseberry",
                similarity=0.4,
            )
        ],
    )

    response = ResponseBuilder().build_single_image_response(
        request_id="unknown-response-builder",
        result=result,
    )

    assert response.identification.status == "UNKNOWN"
    assert response.identification.plant_id is None
    assert response.identification.confidence == pytest.approx(0.21)
    assert response.alternatives == []
    assert response.similar_species == []


def test_gradcam_and_prediction_share_model_lock():
    predictor = build_predictor()
    gradcam = GradCAM(predictor)
    image_for_cam = Image.new("RGB", (16, 16), color="black")
    image_for_prediction = Image.new("RGB", (16, 16), color="white")
    expected = np.asarray(
        gradcam.generate(image_for_cam)["heatmap_image"]
    )

    cam_paused = threading.Event()
    resume_cam = threading.Event()
    prediction_started = threading.Event()
    prediction_forward_entered = threading.Event()
    errors = []
    cam_results = []

    def pause_gradcam_hook(module, inputs, output):
        if threading.current_thread().name == "gradcam-worker":
            cam_paused.set()
            if not resume_cam.wait(timeout=5):
                raise TimeoutError("test did not resume Grad-CAM")

    def detect_prediction_forward(module, inputs):
        if float(inputs[0].mean()) > 0.9:
            prediction_forward_entered.set()

    block_handle = gradcam.target_layer.register_forward_hook(
        pause_gradcam_hook
    )
    model_handle = predictor.model.register_forward_pre_hook(
        detect_prediction_forward
    )

    def run_gradcam():
        try:
            cam_results.append(
                gradcam.generate(image_for_cam)["heatmap_image"]
            )
        except Exception as exc:
            errors.append(exc)

    def run_prediction():
        prediction_started.set()
        try:
            predictor.predict(image_for_prediction)
        except Exception as exc:
            errors.append(exc)

    cam_thread = threading.Thread(
        target=run_gradcam,
        name="gradcam-worker",
    )
    prediction_thread = threading.Thread(target=run_prediction)

    try:
        cam_thread.start()
        assert cam_paused.wait(timeout=5)

        prediction_thread.start()
        assert prediction_started.wait(timeout=5)
        assert not prediction_forward_entered.wait(timeout=0.1)
    finally:
        resume_cam.set()
        cam_thread.join(timeout=5)
        if prediction_thread.ident is not None:
            prediction_thread.join(timeout=5)
        block_handle.remove()
        model_handle.remove()
        gradcam.close()

    assert not cam_thread.is_alive()
    assert not prediction_thread.is_alive()
    assert errors == []
    assert len(cam_results) == 1
    np.testing.assert_array_equal(np.asarray(cam_results[0]), expected)


def test_gradcam_clears_captured_state_after_model_exception():
    predictor = build_predictor()
    gradcam = GradCAM(predictor)
    predictor.model.raise_after_features = True

    with pytest.raises(RuntimeError, match="controlled model failure"):
        gradcam.generate(Image.new("RGB", (16, 16), color="green"))

    assert gradcam.activations is None
    assert gradcam.gradients is None
    assert all(parameter.grad is None for parameter in predictor.model.parameters())
    gradcam.close()


def test_inference_disables_grad_and_gradcam_enables_it():
    predictor = build_predictor()
    gradcam = GradCAM(predictor)
    image = Image.new("RGB", (16, 16), color="blue")

    predictor.predict(image)
    gradcam.generate(image)

    assert predictor.model.grad_modes == [False, True]
    gradcam.close()


def test_gradient_cleanup_failure_does_not_mask_model_exception(
    monkeypatch,
    caplog,
):
    predictor = build_predictor()
    gradcam = GradCAM(predictor)
    predictor.model.raise_after_features = True
    original_zero_grad = predictor.model.zero_grad
    call_count = 0

    def fail_final_cleanup(set_to_none=True):
        nonlocal call_count
        call_count += 1
        if call_count == 2:
            raise OSError("controlled gradient cleanup failure")
        return original_zero_grad(set_to_none=set_to_none)

    monkeypatch.setattr(predictor.model, "zero_grad", fail_final_cleanup)

    with pytest.raises(RuntimeError, match="controlled model failure"):
        gradcam.generate(Image.new("RGB", (16, 16), color="green"))

    assert "Failed to clear Grad-CAM model gradients" in caplog.text
    assert gradcam.activations is None
    assert gradcam.gradients is None
    gradcam.close()


def test_gradcam_hooks_are_not_duplicated_and_close_removes_them():
    gradcam = GradCAM(build_predictor())
    forward_hook_count = len(gradcam.target_layer._forward_hooks)
    backward_hook_count = len(gradcam.target_layer._backward_hooks)

    for _ in range(3):
        gradcam.generate(Image.new("RGB", (16, 16), color="green"))

    assert len(gradcam.target_layer._forward_hooks) == forward_hook_count
    assert len(gradcam.target_layer._backward_hooks) == backward_hook_count
    assert gradcam.activations is None
    assert gradcam.gradients is None

    gradcam.close()

    assert len(gradcam.target_layer._forward_hooks) == 0
    assert len(gradcam.target_layer._backward_hooks) == 0


def test_gradcam_artifacts_are_safe_unique_and_return_valid_urls(tmp_path):
    gradcam = GradCAM.__new__(GradCAM)
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.gradcam = gradcam
    pipeline.gradcam_dir = tmp_path
    result = make_result("red")
    gradcam.generate = lambda image: result

    payloads = [
        pipeline._generate_gradcam_payload(
            image=Image.new("RGB", (16, 16)),
            request_id="../../request",
            image_id="../leaf:one.jpg",
        )
        for _ in range(2)
    ]

    urls = [payload["heatmap_url"] for payload in payloads]
    assert all(url.startswith("/artifacts/gradcam/") for url in urls)
    assert len(set(urls)) == 2
    for payload in payloads:
        heatmap_bytes = base64.b64decode(payload["heatmap_data"])
        with Image.open(io.BytesIO(heatmap_bytes)) as heatmap:
            heatmap.load()
            assert heatmap.format == "JPEG"

    expected_prefix = (
        f"{pipeline._safe_filename('../../request')}_"
        f"{pipeline._safe_filename('../leaf:one.jpg')}_"
    )
    for url in urls:
        filename = url.rsplit("/", 1)[-1]
        assert filename.startswith(expected_prefix)
        assert filename not in {"", ".", ".."}
        assert "/" not in filename
        assert "\\" not in filename
        assert (Path(tmp_path) / filename).is_file()


def test_serialized_gradcam_response_matches_locked_schema_and_usable_jpeg(tmp_path):
    gradcam = GradCAM.__new__(GradCAM)
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.gradcam = gradcam
    pipeline.gradcam_dir = tmp_path
    result = make_result("red")
    gradcam.generate = lambda image: result

    payload = pipeline._generate_gradcam_payload(
        image=Image.new("RGB", (16, 16), color="red"),
        request_id="schema-request",
        image_id="leaf.jpg",
    )

    image_result = InternalImageResult(
        image_id="leaf.jpg",
        quality_acceptable=True,
        status="KNOWN",
        plant_id="PLANT_002",
        confidence=0.99,
        heatmap_data=payload["heatmap_data"],
        heatmap_url=payload["heatmap_url"],
        heatmap_media_type=payload["media_type"],
    )
    response = ResponseBuilder().build_single_image_response(
        request_id="schema-request",
        result=image_result,
        model_version="test-model",
    )
    serialized = response.model_dump(mode="json")

    schema_path = (
        Path(__file__).resolve().parents[2]
        / "shared"
        / "contracts"
        / "ai-response.schema.json"
    )
    with schema_path.open("r", encoding="utf-8") as schema_file:
        schema = json.load(schema_file)
    jsonschema.validate(serialized, schema)

    heatmap = serialized["explanation"]["heatmaps"][0]
    assert heatmap["image_index"] == 1
    assert "image_url" not in heatmap
    heatmap_bytes = base64.b64decode(heatmap["image_data"])
    with Image.open(io.BytesIO(heatmap_bytes)) as usable_heatmap:
        usable_heatmap.load()
        assert usable_heatmap.format == "JPEG"


def test_concurrent_pipeline_gradcam_urls_match_their_heatmaps(tmp_path):
    gradcam = GradCAM.__new__(GradCAM)
    gradcam.generate = lambda image: make_result(
        image.getpixel((0, 0))
    )
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline.gradcam = gradcam
    pipeline.gradcam_dir = tmp_path
    colors = [
        (240, 20, 20),
        (20, 230, 20),
        (20, 20, 240),
        (230, 220, 10),
    ]

    def generate_payload(index):
        return pipeline._generate_gradcam_payload(
            image=Image.new("RGB", (16, 16), color=colors[index]),
            request_id=f"request-{index}",
            image_id=f"image-{index}.jpg",
        )

    with ThreadPoolExecutor(max_workers=len(colors)) as executor:
        payloads = list(executor.map(generate_payload, range(len(colors))))

    filenames = [
        payload["heatmap_url"].rsplit("/", 1)[-1]
        for payload in payloads
    ]
    assert len(set(filenames)) == len(colors)

    for index, (filename, color) in enumerate(zip(filenames, colors)):
        assert filename.startswith(
            f"request-{index}_image-{index}_jpg_"
        )
        with Image.open(tmp_path / filename) as heatmap:
            actual_color = heatmap.convert("RGB").getpixel((8, 8))
        assert all(
            abs(actual - expected) < 12
            for actual, expected in zip(actual_color, color)
        )


def test_main_static_mount_serves_artifacts_and_returns_404_for_missing():
    import app.main as main_module
    from fastapi.testclient import TestClient

    static_mount = next(
        route
        for route in main_module.app.routes
        if getattr(route, "path", None) == "/artifacts/gradcam"
    )
    assert (
        Path(static_mount.app.directory).resolve()
        == main_module.GRADCAM_DIR.resolve()
    )

    filename = f"request-id-static-test-{uuid4().hex}_heatmap.jpg"
    artifact_path = main_module.GRADCAM_DIR / filename
    Image.new("RGB", (8, 8), color="purple").save(
        artifact_path,
        format="JPEG",
    )
    try:
        with TestClient(main_module.app) as client:
            response = client.get(
                f"/artifacts/gradcam/{filename}"
            )
            missing = client.get(
                "/artifacts/gradcam/not-a-real-heatmap.jpg"
            )
    finally:
        if artifact_path.exists():
            artifact_path.unlink()

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"
    assert response.content
    assert missing.status_code == 404


def test_gradcam_generation_failure_does_not_fail_identification(caplog):
    pipeline = build_unknown_test_pipeline(
        class_name="Amla",
        confidence=0.99,
        alternatives=[],
    )
    pipeline.gradcam = SimpleNamespace(
        generate=Mock(side_effect=RuntimeError("controlled CAM failure"))
    )
    pipeline._add_gradcam = lambda result, image, request_id: (
        DravyaSetuPipeline._add_gradcam(
            pipeline,
            result=result,
            image=image,
            request_id=request_id,
        )
    )

    response = pipeline.analyze_image(
        image=Image.new("RGB", (16, 16), color="green"),
        image_id="leaf.jpg",
        request_id="gradcam-generation-error",
    )

    assert response.identification.status == "KNOWN"
    assert response.identification.plant_id == "PLANT_002"
    assert response.explanation.heatmaps == []
    assert "Grad-CAM generation failed" in caplog.text


def test_concurrent_artifact_saves_do_not_overwrite(tmp_path):
    gradcam = GradCAM.__new__(GradCAM)
    results = [
        make_result(color)
        for color in ("red", "green", "blue", "yellow")
    ]

    with ThreadPoolExecutor(max_workers=4) as executor:
        saved = list(executor.map(
            lambda result: gradcam.save(
                result,
                output_dir=tmp_path,
                prefix="../../same/request",
            ),
            results,
        ))

    heatmap_paths = [Path(paths["heatmap"]) for paths in saved]
    assert len({path.name for path in heatmap_paths}) == len(results)
    assert all(path.parent.resolve() == tmp_path.resolve() for path in heatmap_paths)
    assert all(path.is_file() for path in heatmap_paths)
    assert len({path.read_bytes() for path in heatmap_paths}) == len(results)


def test_failed_artifact_save_removes_partial_files_and_preserves_error(
    monkeypatch,
    tmp_path,
    caplog,
):
    gradcam = GradCAM.__new__(GradCAM)
    result = make_result("red")
    original_save = Image.Image.save
    save_count = 0

    def fail_second_save(image, *args, **kwargs):
        nonlocal save_count
        save_count += 1
        if save_count == 2:
            raise OSError("controlled artifact write failure")
        return original_save(image, *args, **kwargs)

    original_unlink = Path.unlink

    def fail_cleanup(path, *args, **kwargs):
        if path.name.startswith("cleanup-test_"):
            raise PermissionError("controlled cleanup failure")
        return original_unlink(path, *args, **kwargs)

    monkeypatch.setattr(Image.Image, "save", fail_second_save)
    monkeypatch.setattr(Path, "unlink", fail_cleanup)

    with pytest.raises(OSError, match="controlled artifact write failure"):
        gradcam.save(
            result,
            output_dir=tmp_path,
            prefix="cleanup-test",
        )

    assert "Failed to remove partial Grad-CAM artifact" in caplog.text
    assert any(tmp_path.iterdir())


def test_gradcam_failure_is_logged_without_failing_pipeline_result(caplog):
    pipeline = DravyaSetuPipeline.__new__(DravyaSetuPipeline)
    pipeline._generate_gradcam_payload = Mock(
        side_effect=RuntimeError("controlled Grad-CAM failure")
    )
    result = SimpleNamespace(
        image_id="leaf.jpg",
        heatmap_url=None,
        heatmap_media_type="image/jpeg",
    )

    pipeline._add_gradcam(
        result=result,
        image=Image.new("RGB", (4, 4)),
        request_id="request-123",
    )

    assert result.heatmap_url is None
    assert "Grad-CAM generation failed" in caplog.text


def test_streaming_request_limit_rejects_chunked_oversize_body():
    request_messages = iter(
        [
            {
                "type": "http.request",
                "body": b"123456",
                "more_body": True,
            },
            {
                "type": "http.request",
                "body": b"789",
                "more_body": True,
            },
        ]
    )
    sent_messages = []
    app_called = False

    async def receive():
        return next(request_messages)

    async def send(message):
        sent_messages.append(message)

    async def inner_app(scope, receive, send):
        nonlocal app_called
        app_called = True
        while (await receive()).get("more_body", False):
            pass

    middleware = AnalyzeRequestBodyLimitMiddleware(
        inner_app,
        max_bytes=8,
    )
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/ai/analyze",
        "headers": [],
    }

    asyncio.run(middleware(scope, receive, send))

    assert app_called
    assert sent_messages[0]["status"] == 413
    assert b"upload size limit" in sent_messages[1]["body"]


def test_multipart_disconnect_is_signaled_for_parser_cleanup():
    sent_messages = []
    app_called = False

    async def receive():
        return {"type": "http.disconnect"}

    async def send(message):
        sent_messages.append(message)

    async def inner_app(scope, receive, send):
        nonlocal app_called
        app_called = True
        await receive()

    middleware = AnalyzeRequestBodyLimitMiddleware(
        inner_app,
        max_bytes=1024,
    )
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/ai/analyze",
        "headers": [
            (b"content-type", b"multipart/form-data; boundary=example")
        ],
    }

    with pytest.raises(UploadClientDisconnected):
        asyncio.run(middleware(scope, receive, send))

    assert app_called
    assert sent_messages == []


def test_shutdown_hook_cleanup_does_not_mask_lifespan_failure(
    monkeypatch,
    caplog,
):
    import app.main as main_module

    close_hooks = Mock(side_effect=OSError("controlled hook cleanup failure"))
    monkeypatch.setattr(
        main_module,
        "pipeline",
        SimpleNamespace(gradcam=SimpleNamespace(close=close_hooks)),
    )

    async def fail_lifespan():
        async with main_module.lifespan(main_module.app):
            raise RuntimeError("original lifespan failure")

    with pytest.raises(RuntimeError, match="original lifespan failure"):
        asyncio.run(fail_lifespan())

    close_hooks.assert_called_once()
    assert "Failed to remove Grad-CAM hooks at shutdown" in caplog.text
