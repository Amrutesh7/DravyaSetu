from typing import List, Optional

from pydantic import BaseModel, Field


class ImageQuality(BaseModel):
    acceptable: bool
    issues: List[str]


class Identification(BaseModel):
    status: str
    plant_id: Optional[str] = None
    confidence: float = Field(
        ge=0.0,
        le=1.0
    )


class Alternative(BaseModel):
    plant_id: str
    common_name: str
    scientific_name: str
    confidence: float = Field(
        ge=0.0,
        le=1.0
    )


class SimilarSpecies(BaseModel):
    plant_id: str
    name: str
    similarity: float = Field(
        ge=0.0,
        le=1.0
    )


class Explanation(BaseModel):
    method: str
    heatmap_url: Optional[str] = None


class MultiImage(BaseModel):
    used: bool
    image_count: int = Field(
        ge=1
    )


class AIResponse(BaseModel):
    request_id: str
    model_version: str
    image_quality: ImageQuality
    identification: Identification
    alternatives: List[Alternative]
    similar_species: List[SimilarSpecies]
    explanation: Explanation
    multi_image: MultiImage
    warnings: List[str]