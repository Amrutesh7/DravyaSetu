# DravyaSetu AI Service API

## Ownership
- Provider: Person 1 — AI/ML + Computer Vision
- Consumer: Person 2 — Spring Boot backend
- Internal service boundary. Web and Mobile clients do not call this service directly.

## Endpoint
### POST /ai/analyze
Purpose: analyze one or more plant images and return the canonical AI response.

## Request
The request is multipart form data and contains:
- Optional `request_id`; the AI service generates one when omitted.
- One to eight repeated `images` file fields, in evidence order.
No evidence-type field is required. Each image is assigned the internal
`OTHER` evidence type for multi-image fusion.

Supported image formats are JPEG, PNG, WebP, BMP, GIF, and TIFF. Each file
may be at most 12 MiB, the total request image payload at most 64 MiB, each
image at most 20 megapixels, and the request at most 40 megapixels combined.
Oversized uploads or dimensions are rejected before inference.

## Response
Successful responses use `multipart/mixed`. The first part is
`application/json` with the name `manifest` and contains the existing AI
analysis fields. Subsequent parts are `image/jpeg` attachments with:
- `Content-ID: <heatmap-{image_index}>`
- `Content-Disposition: attachment; filename="<unique-file-name>.jpg"`
- `Content-Length`
- `X-Request-ID`

The manifest conforms to `shared/contracts/ai-response.schema.json`. Heatmap
entries contain `image_index`, `file_name`, `content_id`, and `media_type`;
they do not contain Base64 or image bytes. Binary JPEG bytes are delivered
only in the corresponding binary parts. Heatmap entries and their binary parts
are emitted in original image order; a part's `Content-ID` must match exactly
one manifest entry.

Example (the JPEG part body below represents raw bytes, not text):

```http
HTTP/1.1 200 OK
Content-Type: multipart/mixed; boundary=dravya-<random-boundary>
X-Request-ID: request-123

--dravya-<random-boundary>
Content-Type: application/json
Content-Disposition: form-data; name="manifest"
Content-ID: <manifest>

{"request_id":"request-123","model_version":"efficientnetv2-s-dravyasetu-v2","explanation":{"method":"GRAD_CAM","heatmaps":[{"image_index":1,"file_name":"request-123_leaf_heatmap.jpg","content_id":"heatmap-1","media_type":"image/jpeg"}]}}
--dravya-<random-boundary>
Content-Type: image/jpeg
Content-Disposition: attachment; filename="request-123_leaf_heatmap.jpg"
Content-ID: <heatmap-1>
Content-Length: <byte-count>
X-Request-ID: request-123

<raw JPEG bytes>
--dravya-<random-boundary>--
```

The manifest is the `manifest` part; every binary part is addressed by its
`Content-ID`, not by multipart arrival timing or filename. Consumers must
validate the media type and JPEG content and reject duplicate, missing, or
unreferenced parts. The AI service allows at most 8 heatmaps and limits each
heatmap to `DRAVYASETU_MAX_HEATMAP_BYTES` (12 MiB by default).

Missing or invalid heatmap artifacts are omitted from the manifest and not
reported as successful heatmaps. Identification results remain available.

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

For a quality-acceptable single-image `UNKNOWN` result, `confidence` retains
the classifier score while `alternatives` and
`similar_species` are empty. Images rejected by quality checks return a
confidence of 0.

For a known identification, `plant_id` MUST refer to an entry in `shared/constants/plant-classes.json`.

Person 1 owns unknown-aware detection, confidence analysis, image-quality screening, multi-image evidence fusion, Grad-CAM generation, and visual similarity logic. Person 2 consumes the result and does not recalculate the AI result.

## Error behavior
Controlled service errors use `shared/contracts/error.schema.json`.

AI-domain codes are maintained in `shared/constants/error-codes.json`.

Invalid image content or evidence metadata is rejected with a 400 response.
Requests exceeding the upload or decoded-dimension limits are rejected with a
413 response. Inference failures return a generic 500 detail; implementation
exceptions are recorded in server logs rather than returned to the caller.

An AI service outage is translated by Person 2 into the public backend error `AI_SERVICE_UNAVAILABLE`.

## Contract rules
1. Person 1 does not access PostgreSQL.
2. Person 1's normal application-level consumer is Person 2.
3. Person 2 validates the AI response before persistence/use.
4. `UNKNOWN` is a valid AI result, not a transport failure.
5. `LOW_CONFIDENCE` is an AI-domain condition and is not the same as a service outage.
6. `model_version` must be returned for reproducibility.
7. Plant IDs must use the shared plant identity mapping; Person 1 must not create a separate plant numbering system.
