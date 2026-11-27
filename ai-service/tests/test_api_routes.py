import asyncio
import base64
import io
import sys
import importlib
import struct
import zlib
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Lock
from uuid import UUID
from unittest.mock import MagicMock

import pytest
from fastapi import FastAPI, HTTPException, UploadFile
from fastapi.testclient import TestClient
from PIL import Image
from starlette.datastructures import Headers


# ---------- Mock pipeline setup BEFORE importing routes ----------

import app.inference.predictor as predictor_module
import app.pipeline as pipeline_module
import app.models.embeddings as embeddings_module
import app.preprocessing.quality_checker as quality_module
import app.inference.unknown_detector as unknown_module
import app.multi_image.evidence_fusion as fusion_module
import app.schemas.response_builder as builder_module


fake_predictor = MagicMock()
fake_predictor.model = MagicMock()
fake_predictor.confidence_calculator = MagicMock()

fake_pipeline = MagicMock()

original_modules = {
    predictor_module: {"build_predictor": predictor_module.build_predictor},
    pipeline_module: {"DravyaSetuPipeline": pipeline_module.DravyaSetuPipeline},
    embeddings_module: {"EmbeddingExtractor": embeddings_module.EmbeddingExtractor},
    quality_module: {"ImageQualityChecker": quality_module.ImageQualityChecker},
    unknown_module: {"UnknownDetector": unknown_module.UnknownDetector},
    fusion_module: {"EvidenceFusion": fusion_module.EvidenceFusion},
    builder_module: {"ResponseBuilder": builder_module.ResponseBuilder},
}

predictor_module.build_predictor = MagicMock(return_value=fake_predictor)
pipeline_module.DravyaSetuPipeline = MagicMock(return_value=fake_pipeline)
embeddings_module.EmbeddingExtractor = MagicMock()
quality_module.ImageQualityChecker = MagicMock()
unknown_module.UnknownDetector = MagicMock()
fusion_module.EvidenceFusion = MagicMock()
builder_module.ResponseBuilder = MagicMock()


sys.modules.pop("app.api.routes", None)
routes_module = importlib.import_module("app.api.routes")
routes_module.pipeline = fake_pipeline

for module, attributes in original_modules.items():
    for attribute, value in attributes.items():
        setattr(module, attribute, value)


app = FastAPI()
app.include_router(routes_module.router)
client = TestClient(app)


# ---------- Helpers ----------

def make_image(color="green", size=(600, 600)):
    buffer = io.BytesIO()
    with Image.new("RGB", size, color=color) as img:
        img.save(buffer, format="JPEG")
    return buffer.getvalue()


def make_response(request_id, image_count=1, multi_used=False):
    return {
        "request_id": request_id,
        "model_version": "efficientnetv2-s-dravyasetu-v2",
        "image_quality": {
            "acceptable": True,
            "issues": [],
        },
        "identification": {
            "status": "KNOWN",
            "plant_id": "Amla",
            "confidence": 0.95,
        },
        "alternatives": [],
        "similar_species": [],
        "explanation": {
            "method": "GRAD_CAM",
            "heatmaps": [],
        },
        "multi_image": {
            "used": multi_used,
            "image_count": image_count,
        },
        "warnings": [],
    }


@pytest.fixture(autouse=True)
def reset_pipeline():
    fake_pipeline.reset_mock()
    yield


# ---------- Tests ----------

def test_openapi_does_not_expose_evidence_types():
    schema = app.openapi()
    operation = schema["paths"]["/ai/analyze"]["post"]
    request_schema_ref = operation["requestBody"]["content"]["multipart/form-data"]["schema"]["$ref"]
    request_schema_name = request_schema_ref.rsplit("/", 1)[-1]
    request_schema = schema["components"]["schemas"][request_schema_name]
    properties = request_schema["properties"]

    assert "images" in properties
    assert "request_id" in properties
    assert "evidence_types" not in properties


def test_client_request_id_is_preserved():
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"]
    )

    response = client.post(
        "/ai/analyze",
        data={"request_id": "client-123"},
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    assert response.json()["request_id"] == "client-123"

    call_kwargs = fake_pipeline.analyze_image.call_args.kwargs
    assert call_kwargs["request_id"] == "client-123"


def test_request_id_is_generated_when_missing():
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"]
    )

    response = client.post(
        "/ai/analyze",
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    response_request_id = response.json()["request_id"]
    assert str(UUID(response_request_id)) == response_request_id
    assert (
        fake_pipeline.analyze_image.call_args.kwargs["request_id"]
        == response_request_id
    )


def test_missing_multi_image_request_id_is_generated_once():
    fake_pipeline.analyze_images.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"],
        image_count=len(kwargs["images"]),
        multi_used=True,
    )

    response = client.post(
        "/ai/analyze",
        data={"evidence_types": ["LEAF", "FLOWER"]},
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
            ("images", ("flower.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    response_request_id = response.json()["request_id"]
    assert str(UUID(response_request_id)) == response_request_id
    assert (
        fake_pipeline.analyze_images.call_args.kwargs["request_id"]
        == response_request_id
    )


def test_simultaneous_single_and_multi_requests_keep_ids_and_complete():
    request_count = 8
    synchronization_barrier = Barrier(request_count, timeout=3)

    class BarrierPipeline:
        def __init__(self):
            self.calls = []
            self.calls_lock = Lock()

        def _respond(self, kind, kwargs):
            synchronization_barrier.wait()
            with self.calls_lock:
                self.calls.append(
                    (kind, kwargs["request_id"], len(kwargs.get("images", [])))
                )
            image_count = (
                len(kwargs["images"])
                if kind == "multi"
                else 1
            )
            return make_response(
                kwargs["request_id"],
                image_count=image_count,
                multi_used=kind == "multi",
            )

        def analyze_image(self, **kwargs):
            return self._respond("single", kwargs)

        def analyze_images(self, **kwargs):
            return self._respond("multi", kwargs)

    stress_pipeline = BarrierPipeline()
    original_pipeline = routes_module.pipeline
    routes_module.pipeline = stress_pipeline

    requests = []
    for index in range(request_count):
        request_id = (
            "duplicate-request-id"
            if index < 2
            else f"stress-request-{index}"
        )
        files = [
            ("images", (f"image-{index}-0.jpg", make_image(), "image/jpeg"))
        ]
        data = {"request_id": request_id}
        if index % 2:
            files.append(
                (
                    "images",
                    (f"image-{index}-1.jpg", make_image(), "image/jpeg"),
                )
            )
            data["evidence_types"] = ["LEAF", "FLOWER"]
        requests.append((index, request_id, files, data))

    def send_request(request):
        index, request_id, files, data = request
        response = request_client.post(
            "/ai/analyze",
            data=data,
            files=files,
        )
        return index, request_id, response

    try:
        with TestClient(app) as request_client:
            with ThreadPoolExecutor(
                max_workers=request_count
            ) as executor:
                results = list(executor.map(send_request, requests))
    finally:
        routes_module.pipeline = original_pipeline

    assert len(stress_pipeline.calls) == request_count
    assert sum(call[0] == "single" for call in stress_pipeline.calls) == 4
    assert sum(call[0] == "multi" for call in stress_pipeline.calls) == 4
    assert sum(
        call[1] == "duplicate-request-id"
        for call in stress_pipeline.calls
    ) == 2

    for index, expected_id, response in results:
        assert response.status_code == 200
        body = response.json()
        assert body["request_id"] == expected_id
        assert body["multi_image"]["used"] is bool(index % 2)
        assert body["multi_image"]["image_count"] == (
            2 if index % 2 else 1
        )


def test_single_image_workflow_preserves_response_contract():
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"]
    )

    response = client.post(
        "/ai/analyze",
        data={"request_id": "single-request-123"},
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == "single-request-123"
    assert body["model_version"] == "efficientnetv2-s-dravyasetu-v2"
    assert body["image_quality"] == {
        "acceptable": True,
        "issues": [],
    }
    assert body["identification"] == {
        "status": "KNOWN",
        "plant_id": "Amla",
        "confidence": 0.95,
    }
    assert body["alternatives"] == []
    assert body["similar_species"] == []
    assert body["explanation"] == {
        "method": "GRAD_CAM",
        "heatmaps": [],
    }
    assert body["multi_image"] == {
        "used": False,
        "image_count": 1,
    }
    assert body["warnings"] == []

    fake_pipeline.analyze_image.assert_called_once()
    fake_pipeline.analyze_images.assert_not_called()
    call_kwargs = fake_pipeline.analyze_image.call_args.kwargs
    assert call_kwargs["image_id"] == "leaf.jpg"
    assert call_kwargs["evidence_type"] == "OTHER"


def test_multi_image_explicit_evidence_types_are_preserved():
    fake_pipeline.analyze_images.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"],
        image_count=len(kwargs["images"]),
        multi_used=True,
    )

    response = client.post(
        "/ai/analyze",
        data={
            "request_id": "multi-123",
        },
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
            ("images", ("flower.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    assert response.json()["request_id"] == "multi-123"
    assert response.json()["multi_image"] == {
        "used": True,
        "image_count": 2,
    }

    call_kwargs = fake_pipeline.analyze_images.call_args.kwargs
    assert call_kwargs["evidence_types"] == ["OTHER", "OTHER"]
    assert call_kwargs["image_ids"] == ["leaf.jpg", "flower.jpg"]
    assert call_kwargs["request_id"] == "multi-123"
    assert len(call_kwargs["images"]) == 2
    fake_pipeline.analyze_images.assert_called_once()
    fake_pipeline.analyze_image.assert_not_called()


def test_image_order_is_preserved_with_explicit_evidence_types():
    colors = ["red", "yellow", "blue"]
    filenames = ["leaf.jpg", "flower.jpg", "fruit.jpg"]
    encoded_images = [make_image(color) for color in colors]
    files = [
        ("images", (filename, image_bytes, "image/jpeg"))
        for filename, image_bytes in zip(filenames, encoded_images)
    ]
    data = {
        "request_id": "ordered-observation",
    }
    forwarded_pixels = []

    def analyze_images(**kwargs):
        forwarded_pixels.extend(
            image.getpixel((0, 0))
            for image in kwargs["images"]
        )
        return make_response(
            kwargs["request_id"],
            image_count=len(kwargs["images"]),
            multi_used=True,
        )

    fake_pipeline.analyze_images.side_effect = analyze_images

    response = client.post("/ai/analyze", data=data, files=files)

    assert response.status_code == 200
    call_kwargs = fake_pipeline.analyze_images.call_args.kwargs
    assert call_kwargs["image_ids"] == filenames
    assert call_kwargs["evidence_types"] == ["OTHER", "OTHER", "OTHER"]
    assert forwarded_pixels == [
        Image.open(io.BytesIO(image_bytes)).convert("RGB").getpixel((0, 0))
        for image_bytes in encoded_images
    ]
    assert call_kwargs["request_id"] == "ordered-observation"
    assert response.json()["request_id"] == "ordered-observation"
    fake_pipeline.analyze_images.assert_called_once()
    fake_pipeline.analyze_image.assert_not_called()


def test_three_image_gradcam_data_remains_attached_across_repeated_requests():
    filenames = ["leaf.jpg", "flower.jpg", "fruit.jpg"]
    colors = ["red", "yellow", "blue"]
    encoded_images = [make_image(color) for color in colors]
    files = [
        ("images", (filename, image_bytes, "image/jpeg"))
        for filename, image_bytes in zip(filenames, encoded_images)
    ]
    expected_data = [
        base64.b64encode(image_id.encode("ascii")).decode("ascii")
        for image_id in filenames
    ]

    def analyze_images(**kwargs):
        response = make_response(
            kwargs["request_id"],
            image_count=len(kwargs["images"]),
            multi_used=True,
        )
        response["explanation"]["heatmaps"] = [
            {
                "image_index": index,
                "image_data": expected_data[index - 1],
                "media_type": "image/jpeg",
            }
            for index in range(1, 4)
        ]
        return response

    fake_pipeline.analyze_images.side_effect = analyze_images

    for request_number in range(2):
        response = client.post(
            "/ai/analyze",
            data={
                "request_id": f"ordered-observation-{request_number}",
            },
            files=files,
        )

        assert response.status_code == 200
        call_kwargs = fake_pipeline.analyze_images.call_args.kwargs
        assert call_kwargs["image_ids"] == filenames
        assert [
            heatmap["image_index"]
            for heatmap in response.json()["explanation"]["heatmaps"]
        ] == [1, 2, 3]
        assert [
            heatmap["image_data"]
            for heatmap in response.json()["explanation"]["heatmaps"]
        ] == expected_data


def test_single_image_uses_internal_other_evidence_type():
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"]
    )

    response = client.post(
        "/ai/analyze",
        files=[("images", ("leaf.jpg", make_image(), "image/jpeg"))],
    )

    assert response.status_code == 200
    assert (
        fake_pipeline.analyze_image.call_args.kwargs["evidence_type"]
        == "OTHER"
    )


def test_multi_image_without_evidence_metadata_uses_other():
    fake_pipeline.analyze_images.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"],
        image_count=len(kwargs["images"]),
        multi_used=True,
    )

    response = client.post(
        "/ai/analyze",
        files=[
            ("images", ("leaf.jpg", make_image(), "image/jpeg")),
            ("images", ("flower.jpg", make_image(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    call_kwargs = fake_pipeline.analyze_images.call_args.kwargs
    assert call_kwargs["evidence_types"] == ["OTHER", "OTHER"]


def test_eight_images_are_accepted_in_upload_order():
    files = [
        ("images", (f"image_{index}.jpg", make_image(), "image/jpeg"))
        for index in range(8)
    ]
    fake_pipeline.analyze_images.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"],
        image_count=len(kwargs["images"]),
        multi_used=True,
    )

    response = client.post(
        "/ai/analyze",
        files=files,
    )

    assert response.status_code == 200
    call_kwargs = fake_pipeline.analyze_images.call_args.kwargs
    assert len(call_kwargs["images"]) == 8
    assert call_kwargs["image_ids"] == [
        f"image_{index}.jpg" for index in range(8)
    ]
    assert call_kwargs["evidence_types"] == ["OTHER"] * 8


def test_more_than_max_images_is_rejected():
    files = [
        ("images", (f"image_{i}.jpg", make_image(), "image/jpeg"))
        for i in range(9)
    ]

    response = client.post("/ai/analyze", files=files)

    assert response.status_code == 400
    assert "maximum of 8 images" in response.json()["detail"]


def test_empty_image_is_rejected():
    response = client.post(
        "/ai/analyze",
        files=[("images", ("empty.jpg", b"", "image/jpeg"))],
    )

    assert response.status_code == 400
    assert "empty" in response.json()["detail"].lower()


def make_upload_file(content=None):
    return UploadFile(
        filename="leaf.jpg",
        file=io.BytesIO(content if content is not None else make_image()),
        headers=Headers({"content-type": "image/jpeg"}),
    )


def test_route_closes_upload_and_decoded_image_after_success(monkeypatch):
    upload = make_upload_file()
    decoded_images = []
    original_reader = routes_module.read_uploaded_image

    async def tracking_reader(upload, **kwargs):
        decoded = await original_reader(upload, **kwargs)
        decoded_images.append(decoded[0])
        return decoded

    monkeypatch.setattr(
        routes_module,
        "read_uploaded_image",
        tracking_reader,
    )
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: make_response(
        kwargs["request_id"]
    )

    response = asyncio.run(
        routes_module.analyze_image(
            images=[upload],
            request_id="cleanup-success",
        )
    )

    assert response.request_id == "cleanup-success"
    assert upload.file.closed
    assert len(decoded_images) == 1
    with pytest.raises(ValueError, match="closed image"):
        decoded_images[0].getpixel((0, 0))


def test_route_closes_upload_on_validation_failure():
    uploads = [make_upload_file() for _ in range(9)]

    with pytest.raises(HTTPException) as error:
        asyncio.run(
            routes_module.analyze_image(
                images=uploads,
                request_id="cleanup-validation",
            )
        )

    assert error.value.status_code == 400
    assert all(upload.file.closed for upload in uploads)


def test_inference_failure_is_generic_logged_and_cleans_resources(
    monkeypatch,
    caplog,
):
    upload = make_upload_file()
    decoded_images = []
    original_reader = routes_module.read_uploaded_image

    async def tracking_reader(upload, **kwargs):
        decoded = await original_reader(upload, **kwargs)
        decoded_images.append(decoded[0])
        return decoded

    monkeypatch.setattr(
        routes_module,
        "read_uploaded_image",
        tracking_reader,
    )
    fake_pipeline.analyze_image.side_effect = RuntimeError(
        "private checkpoint location"
    )

    with pytest.raises(HTTPException) as error:
        asyncio.run(
            routes_module.analyze_image(
                images=[upload],
                request_id="cleanup-inference-error",
            )
        )

    assert error.value.status_code == 500
    assert error.value.detail == "AI pipeline execution failed."
    assert "private checkpoint location" not in error.value.detail
    assert "AI pipeline execution failed" in caplog.text
    assert upload.file.closed
    with pytest.raises(ValueError, match="closed image"):
        decoded_images[0].getpixel((0, 0))


def test_cancelled_upload_read_still_closes_upload():
    upload = make_upload_file()

    async def cancelled_read(size=-1):
        raise asyncio.CancelledError

    upload.read = cancelled_read

    with pytest.raises(asyncio.CancelledError):
        asyncio.run(
            routes_module.analyze_image(
                images=[upload],
                request_id="cleanup-cancelled",
            )
        )

    assert upload.file.closed


def test_oversized_image_file_is_rejected(monkeypatch):
    monkeypatch.setattr(routes_module, "MAX_IMAGE_BYTES", 128)
    response = client.post(
        "/ai/analyze",
        files=[
            ("images", ("large.jpg", b"x" * 129, "image/jpeg")),
        ],
    )

    assert response.status_code == 413
    assert "file size limit" in response.json()["detail"]


def test_rgb_upload_decode_avoids_full_resolution_conversion(monkeypatch):
    convert_calls = []
    original_convert = Image.Image.convert

    def track_convert(image, mode=None, *args, **kwargs):
        convert_calls.append(mode)
        return original_convert(image, mode, *args, **kwargs)

    monkeypatch.setattr(Image.Image, "convert", track_convert)
    decoded, _, pixel_count = asyncio.run(
        routes_module.read_uploaded_image(make_upload_file())
    )

    try:
        assert decoded.mode == "RGB"
        assert pixel_count == 600 * 600
        assert convert_calls == []
        assert decoded.getpixel((0, 0))[1] > 100
    finally:
        decoded.close()


def test_maximum_pixel_image_reaches_pipeline():
    observed_sizes = []
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: (
        observed_sizes.append(kwargs["image"].size)
        or make_response(kwargs["request_id"])
    )
    response = client.post(
        "/ai/analyze",
        files=[
            (
                "images",
                ("max-size.jpg", make_image(size=(5000, 4000)), "image/jpeg"),
            ),
        ],
    )

    assert response.status_code == 200
    assert observed_sizes == [(5000, 4000)]


def test_upload_exif_orientation_is_applied_before_pipeline():
    observed_sizes = []
    image = Image.new("RGB", (800, 400), color="green")
    exif = Image.Exif()
    exif[274] = 6
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", exif=exif.tobytes())
    image.close()
    fake_pipeline.analyze_image.side_effect = lambda **kwargs: (
        observed_sizes.append(kwargs["image"].size)
        or make_response(kwargs["request_id"])
    )

    response = client.post(
        "/ai/analyze",
        files=[
            ("images", ("oriented.jpg", buffer.getvalue(), "image/jpeg")),
        ],
    )

    assert response.status_code == 200
    assert observed_sizes == [(400, 800)]


def test_combined_maximum_pixel_images_reach_multi_image_pipeline():
    observed_sizes = []

    def analyze_images(**kwargs):
        observed_sizes.append(
            [image.size for image in kwargs["images"]]
        )
        return make_response(
            kwargs["request_id"],
            image_count=len(kwargs["images"]),
            multi_used=True,
        )

    fake_pipeline.analyze_images.side_effect = analyze_images
    response = client.post(
        "/ai/analyze",
        files=[
            (
                "images",
                ("max-size-1.jpg", make_image(size=(5000, 4000)), "image/jpeg"),
            ),
            (
                "images",
                ("max-size-2.jpg", make_image(size=(5000, 4000)), "image/jpeg"),
            ),
        ],
    )

    assert response.status_code == 200
    assert response.json()["multi_image"]["image_count"] == 2
    assert observed_sizes == [[(5000, 4000), (5000, 4000)]]


def make_png_header(width, height):
    def chunk(name, data):
        body = name + data
        return (
            struct.pack(">I", len(data))
            + body
            + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IEND", b"")
    )


def test_extreme_image_dimensions_are_rejected_before_decode():
    oversized_dimensions = make_png_header(5_000_000, 5)
    response = client.post(
        "/ai/analyze",
        files=[
            (
                "images",
                ("extreme.png", oversized_dimensions, "image/png"),
            ),
        ],
    )

    assert response.status_code == 413
    assert "20 megapixel" in response.json()["detail"]


@pytest.mark.parametrize(
    "image_bytes",
    [
        b"not an image",
        make_image()[:-20],
    ],
)
def test_corrupt_or_truncated_images_are_rejected(image_bytes):
    response = client.post(
        "/ai/analyze",
        files=[("images", ("broken.jpg", image_bytes, "image/jpeg"))],
    )

    assert response.status_code == 400
    assert "valid image" in response.json()["detail"]


def test_combined_image_pixel_limit_is_enforced(monkeypatch):
    monkeypatch.setattr(routes_module, "MAX_TOTAL_IMAGE_PIXELS", 1_000_000)
    files = [
        ("images", (f"large_{index}.jpg", make_image(size=(800, 800)), "image/jpeg"))
        for index in range(2)
    ]

    response = client.post(
        "/ai/analyze",
        files=files,
    )

    assert response.status_code == 413
    assert "40 megapixel request limit" in response.json()["detail"]