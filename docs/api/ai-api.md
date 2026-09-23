# DravyaSetu AI Service API

## Ownership
- Provider: Person 1 — AI/ML + Computer Vision
- Consumer: Person 2 — Spring Boot backend
- Internal service boundary. Web and Mobile clients do not call this service directly.

## Endpoint
### POST /ai/analyze
Purpose: analyze one or more plant images and return the canonical AI response.

## Request
The request is sent by Person 2 and contains:
- `request_id`
- one or more plant images
- plant-part metadata when available

Documented plant-part values:
- `LEAF`
- `FLOWER`
- `STEM`
- `ROOT`
- `BARK`

The exact multipart field names are an implementation detail and MUST be identical between Person 1 and Person 2.

## Response
The response MUST conform to `shared/contracts/ai-response.schema.json`.

Canonical top-level fields:
- `request_id`
- `model_version`
- `image_quality`
- `identification`
- `alternatives`
- `similar_species`
- `explanation`
- `multi_image`
- `warnings`

Identification status is `KNOWN` or `UNKNOWN`. `plant_id` is null when status is `UNKNOWN`. Confidence values are represented from 0 to 1.

Person 1 owns unknown-aware detection, confidence analysis, image-quality screening, multi-image evidence fusion, Grad-CAM generation, and visual similarity logic. Person 2 consumes the result and does not recalculate the AI result.

## Error behavior
Controlled service errors use `shared/contracts/error.schema.json`.

AI-domain codes are maintained in `shared/constants/error-codes.json`.

An AI service outage is translated by Person 2 into the public backend error `AI_SERVICE_UNAVAILABLE`.

## Contract rules
1. Person 1 does not access PostgreSQL.
2. Person 1's normal application-level consumer is Person 2.
3. Person 2 validates the AI response before persistence/use.
4. `UNKNOWN` is a valid AI result, not a transport failure.
5. `LOW_CONFIDENCE` is an AI-domain condition and is not the same as a service outage.
6. `model_version` must be returned for reproducibility.
