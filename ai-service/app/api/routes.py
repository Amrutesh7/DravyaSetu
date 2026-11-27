
from __future__ import annotations

import io
import logging
import uuid
import warnings

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError
from starlette.concurrency import run_in_threadpool

from app.inference.predictor import build_predictor
from app.inference.unknown_detector import UnknownDetector
from app.models.embeddings import EmbeddingExtractor
from app.multi_image.evidence_fusion import EvidenceFusion
from app.pipeline import DravyaSetuPipeline, PipelineConfig
from app.preprocessing.quality_checker import ImageQualityChecker
from app.schemas.response import AIResponse
from app.schemas.response_builder import ResponseBuilder

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["AI"])

_predictor = build_predictor()

pipeline = DravyaSetuPipeline(
    predictor=_predictor,
    quality_checker=ImageQualityChecker(),
    confidence_calculator=_predictor.confidence_calculator,
    unknown_detector=UnknownDetector(confidence_threshold=0.60),
    embedding_extractor=EmbeddingExtractor(model=_predictor.model),
    evidence_fusion=EvidenceFusion(),
    response_builder=ResponseBuilder(),
    config=PipelineConfig(),
)

MAX_IMAGES = 8
MAX_IMAGE_BYTES = 12 * 1024 * 1024
MAX_TOTAL_IMAGE_BYTES = 64 * 1024 * 1024
MAX_REQUEST_BODY_BYTES = MAX_TOTAL_IMAGE_BYTES + 1024 * 1024
MAX_IMAGE_PIXELS = 20_000_000
MAX_TOTAL_IMAGE_PIXELS = 40_000_000
SUPPORTED_IMAGE_FORMATS = {
    "BMP",
    "GIF",
    "JPEG",
    "PNG",
    "TIFF",
    "WEBP",
}


async def read_uploaded_image(
    upload: UploadFile,
    *,
    max_bytes: int = MAX_IMAGE_BYTES,
    max_pixels: int = MAX_IMAGE_PIXELS,
    size_limit_detail: str = (
        "Uploaded image exceeds the 12 MiB file size limit."
    ),
    pixel_limit_detail: str = (
        "Image dimensions exceed the 20 megapixel limit."
    ),
) -> tuple[Image.Image, int, int]:
    """Read and validate an uploaded image."""

    if not upload.content_type:
        raise HTTPException(
            status_code=400,
            detail="Image content type is missing.",
        )

    if not upload.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="Uploaded file must be an image.",
        )

    try:
        image_bytes = await upload.read(max_bytes + 1)

        if len(image_bytes) > max_bytes:
            raise HTTPException(
                status_code=413,
                detail=size_limit_detail,
            )

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="Uploaded image is empty.",
            )

        with warnings.catch_warnings():
            warnings.simplefilter(
                "error",
                Image.DecompressionBombWarning,
            )

            with io.BytesIO(image_bytes) as verify_stream:
                with Image.open(verify_stream) as image:
                    image_format = image.format

                    if image_format not in SUPPORTED_IMAGE_FORMATS:
                        raise HTTPException(
                            status_code=400,
                            detail="Unsupported image format.",
                        )

                    width, height = image.size
                    pixel_count = width * height

                    if pixel_count > max_pixels:
                        raise HTTPException(
                            status_code=413,
                            detail=pixel_limit_detail,
                        )

                    image.verify()

            with io.BytesIO(image_bytes) as decode_stream:
                decoded = None
                oriented = None
                rgb_image = None
                try:
                    decoded = Image.open(decode_stream)
                    if decoded.format != image_format:
                        raise UnidentifiedImageError(
                            "Image format changed between validation passes."
                        )
                    decoded.load()
                    orientation = decoded.getexif().get(274)
                    oriented = (
                        ImageOps.exif_transpose(decoded)
                        if orientation not in (None, 1)
                        else decoded
                    )
                    rgb_image = (
                        oriented
                        if oriented.mode == "RGB"
                        else oriented.convert("RGB")
                    )
                finally:
                    if (
                        oriented is not None
                        and oriented is not decoded
                        and oriented is not rgb_image
                    ):
                        try:
                            oriented.close()
                        except Exception:
                            logger.exception(
                                "Failed to close transformed source image."
                            )
                    if decoded is not None and decoded is not rgb_image:
                        try:
                            decoded.close()
                        except Exception:
                            logger.exception(
                                "Failed to close decoded source image."
                            )

        return rgb_image, len(image_bytes), pixel_count

    except HTTPException:
        raise

    except (
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
    ) as exc:
        raise HTTPException(
            status_code=413,
            detail=pixel_limit_detail,
        ) from exc

    except (UnidentifiedImageError, OSError, SyntaxError, ValueError) as exc:
        raise HTTPException(
            status_code=400,
            detail="Invalid image file. Please upload a valid image.",
        ) from exc


async def _close_uploaded_files(
    uploads: list[UploadFile],
    request_id: str,
) -> None:
    for upload in uploads:
        try:
            await upload.close()
        except Exception:
            logger.exception(
                "Failed to close uploaded file for request_id=%s.",
                request_id,
            )


def _close_plant_images(
    images: list[Image.Image],
    request_id: str,
) -> None:
    for image in images:
        try:
            image.close()
        except Exception:
            logger.exception(
                "Failed to close decoded image for request_id=%s.",
                request_id,
            )


@router.post("/analyze", response_model=AIResponse)
async def analyze_image(
    images: list[UploadFile] = File(...),
    request_id: str | None = Form(default=None),
):
    """
    Analyze one or multiple images of the same plant observation.

    One image:
        Uses single-image inference.

    Multiple images:
        Uses multi-image evidence fusion.
    """

    request_id = request_id or str(uuid.uuid4())
    uploads = images or []
    plant_images = []

    try:
        if not uploads:
            raise HTTPException(
                status_code=400,
                detail="Upload at least one image using 'images'.",
            )

        if len(uploads) > MAX_IMAGES:
            raise HTTPException(
                status_code=400,
                detail=f"A maximum of {MAX_IMAGES} images is allowed.",
            )

        normalized_types = ["OTHER"] * len(uploads)

        total_image_bytes = 0
        total_image_pixels = 0

        for upload in uploads:
            remaining_bytes = MAX_TOTAL_IMAGE_BYTES - total_image_bytes
            remaining_pixels = MAX_TOTAL_IMAGE_PIXELS - total_image_pixels
            if remaining_bytes <= 0:
                raise HTTPException(
                    status_code=413,
                    detail=(
                        "Combined image uploads exceed the 64 MiB "
                        "request size limit."
                    ),
                )
            if remaining_pixels <= 0:
                raise HTTPException(
                    status_code=413,
                    detail=(
                        "Combined image dimensions exceed the "
                        "40 megapixel request limit."
                    ),
                )

            max_bytes = min(MAX_IMAGE_BYTES, remaining_bytes)
            max_pixels = min(MAX_IMAGE_PIXELS, remaining_pixels)
            size_limit_detail = (
                "Combined image uploads exceed the "
                "64 MiB request size limit."
                if remaining_bytes < MAX_IMAGE_BYTES
                else "Uploaded image exceeds the 12 MiB file size limit."
            )
            pixel_limit_detail = (
                "Combined image dimensions exceed the "
                "40 megapixel request limit."
                if remaining_pixels < MAX_IMAGE_PIXELS
                else "Image dimensions exceed the 20 megapixel limit."
            )

            plant_image, image_bytes, image_pixels = (
                await read_uploaded_image(
                    upload,
                    max_bytes=max_bytes,
                    max_pixels=max_pixels,
                    size_limit_detail=size_limit_detail,
                    pixel_limit_detail=pixel_limit_detail,
                )
            )
            plant_images.append(plant_image)
            total_image_bytes += image_bytes
            total_image_pixels += image_pixels

        try:
            if len(plant_images) == 1:
                response = await run_in_threadpool(
                    pipeline.analyze_image,
                    image=plant_images[0],
                    image_id=uploads[0].filename or "image_0",
                    request_id=request_id,
                    evidence_type=normalized_types[0],
                    model_version="efficientnetv2-s-dravyasetu-v2",
                )
            else:
                response = await run_in_threadpool(
                    pipeline.analyze_images,
                    images=plant_images,
                    image_ids=[
                        upload.filename or f"image_{index}"
                        for index, upload in enumerate(uploads)
                    ],
                    evidence_types=normalized_types,
                    request_id=request_id,
                    model_version="efficientnetv2-s-dravyasetu-v2",
                )

            return AIResponse.model_validate(response)

        except Exception as exc:
            logger.exception(
                "AI pipeline execution failed for request_id=%s.",
                request_id,
            )
            raise HTTPException(
                status_code=500,
                detail="AI pipeline execution failed.",
            ) from exc
    finally:
        _close_plant_images(plant_images, request_id)
        await _close_uploaded_files(uploads, request_id)

