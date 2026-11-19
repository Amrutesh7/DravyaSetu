# DravyaSetu AI Service API

## Ownership
- Provider: Person 1 — AI/ML + Computer Vision
- Consumer: Person 2 — Spring Boot backend
- Internal service boundary. Web and Mobile clients do not call this service directly.

## Endpoint
### POST /ai/analyze
Purpose: analyze one or more plant images and return the canonical AI response.

## Request
The request is sent by Person 2 as `POST /ai/analyze` with
`Content-Type: application/json`. `X-Request-Id` carries the same value as
`request_id`.

| Field | Type | Required | Description |
|---|---|---|---|
| `request_id` | string (UUID) | yes | Identification-level id; also sent as the `X-Request-Id` header |
| `image` | array of strings | yes | One or more image URLs. Images live in cloud storage; each element is the URL of one image |
| `plant_part` | string | no | Plant-part metadata when the client supplied it; omit when unknown |

Documented plant-part values:
- `LEAF`
- `FLOWER`
- `STEM`
- `ROOT`
- `BARK`

```json
{
  "request_id": "3f1c9a52-8a4e-4f6b-9d0e-1a2b3c4d5e6f",
  "image": [
    "https://storage.example/plants/3f1c/leaf-1.jpg?X-Amz-Expires=600&X-Amz-Signature=...",
    "https://storage.example/plants/3f1c/leaf-2.jpg?X-Amz-Expires=600&X-Amz-Signature=..."
  ],
  "plant_part": "LEAF"
}
```

`plant_part` is optional; omit the key entirely when the part is unknown. URLs are
short-lived signed URLs, so Person 1 must fetch each image within the URL lifetime.

> Implementation status: the running backend still sends the previous
> multipart binary form until the cloud-storage upload path is implemented. This
> document is the agreed target contract for Person 1.

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

For a known identification, `plant_id` MUST refer to an entry in `shared/constants/plant-classes.json`.

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
7. Plant IDs must use the shared plant identity mapping; Person 1 must not create a separate plant numbering system.
