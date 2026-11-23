# DravyaSetu AI Service API

## Ownership
- Provider: Person 1 — AI/ML + Computer Vision
- Consumer: Person 2 — Spring Boot backend
- Internal service boundary. Web and Mobile clients do not call this service directly.

## Endpoint
### POST /ai/analyze
Deployed base URL: `https://dravyasetu-ai.onrender.com`
Purpose: analyze one or more uploaded plant images and return the canonical AI response.

## Request contract
Spring Boot sends `multipart/form-data`, repeating `image` for multiple uploads
(default maximum 5). The AI owner has confirmed multi-image and request-ID support;
live deployment verification remains open in the deployment checklist.
`X-Request-Id` is also sent for tracing.

| Field | Type | Required | Description |
|---|---|---|---|
| `image` | repeated binary file | yes | One or more JPEG, PNG, or WebP plant images in upload order |
| `request_id` | string | yes | Backend request identifier |
| `plant_part` | string | no | Optional caller-supplied plant-part hint |

Preserve `request_id` in the response for tracing. Spring Boot uses its own
request identifier for persistence and the frontend response; its current validator
requires the upstream response ID to be present but does not enforce equality.

```bash
curl -X POST "https://dravyasetu-ai.onrender.com/ai/analyze" \
  -F "image=@leaf.jpg;type=image/jpeg" \
  -F "image=@flower.png;type=image/png" \
  -F "request_id=REQ_001"
```

`multi_image.image_count` describes the images analyzed. Heatmap `image_index`
values are 1-based positions in the incoming image sequence. A configured service
URL does not establish that its current deployment is healthy or contract-compatible.

## Optional URL transport
For an AI deployment that accepts signed image URLs, set
`AI_IMAGE_TRANSPORT=url` (requires Backblaze B2 storage). The backend then sends
`application/json` with `request_id`, `image` as an array of short-lived
presigned URLs, and optional `plant_part`. The default is `multipart` because
that is what the deployed service accepts.

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

### Heatmaps in the response

The JSON contract carries JPEG/PNG heatmap bytes as base64 image data:

```json
"explanation": {
  "method": "GRAD_CAM",
  "heatmaps": [
    {
      "image_index": 1,
      "image_data": "<base64 JPEG>",
      "media_type": "image/jpeg"
    }
  ]
}
```

| Field | Description |
|---|---|
| `image_index` | 1-based index of the uploaded image this heatmap explains |
| `image_data` | Base64-encoded image bytes; a `data:` URI prefix is also tolerated |
| `media_type` | Declared media type (`image/jpeg`) |

The backend decodes `image_data`, validates the image signature and size, uploads
it to B2, and exposes it to clients as a presigned URL. `image_data` is never
persisted in the database or returned to Web/Mobile. Legacy deployments that
return `explanation.heatmap_url` or `explanation.heatmap_urls` remain supported:
the backend downloads and archives those URLs instead.

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
