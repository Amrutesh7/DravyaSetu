"""Canonical response models for DravyaSetu."""

from typing import List, Literal, Optional
from pydantic import BaseModel, ConfigDict, Field


class StrictResponseModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ImageQualityResponse(StrictResponseModel):
    acceptable: bool
    issues: List[str]


class IdentificationResponse(StrictResponseModel):
    status: Literal["KNOWN", "UNKNOWN"]
    plant_id: Optional[str]
    confidence: float = Field(ge=0.0, le=1.0)


class AlternativeResponse(StrictResponseModel):
    plant_id: str
    common_name: str
    scientific_name: str
    confidence: float = Field(ge=0.0, le=1.0)


class SimilarSpeciesResponse(StrictResponseModel):
    plant_id: str
    name: str
    similarity: float = Field(ge=0.0, le=1.0)


class ExplanationResponse(StrictResponseModel):
    method: Literal["GRAD_CAM"]
    heatmap_url: Optional[str]


class MultiImageResponse(StrictResponseModel):
    used: bool
    image_count: int = Field(ge=1)


class AIResponse(StrictResponseModel):
    request_id: str
    model_version: str
    image_quality: ImageQualityResponse
    identification: IdentificationResponse
    alternatives: List[AlternativeResponse]
    similar_species: List[SimilarSpeciesResponse]
    explanation: ExplanationResponse
    multi_image: MultiImageResponse
    warnings: List[str]
