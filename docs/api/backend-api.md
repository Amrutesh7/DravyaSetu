# DravyaSetu Public Backend API

## Ownership
Person 2 owns the public Spring Boot API. Web and Mobile clients communicate with Spring Boot only.

Use `http://localhost:8080/api/v1` locally and the Render HTTPS host after deployment.
Swagger is `/api/v1/swagger-ui/index.html`. Authentication payloads and flows are
defined in [the authentication guide](../AUTH_API_DOCUMENTATION.md); AI and Knowledge
service details belong in [their](ai-api.md) [contracts](knowledge-api.md).

## Public endpoints
| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/v1/auth/register | Create a pending account and email a verification code |
| POST | /api/v1/auth/verify-email | Confirm the emailed code and activate the account |
| POST | /api/v1/auth/resend-verification | Send a new verification code |
| POST | /api/v1/auth/login | Authenticate and receive a bearer token |
| POST | /api/v1/auth/logout | Revoke the presented bearer token |
| GET | /api/v1/auth/me | Current identity, role and verification state |
| POST | /api/v1/auth/forgot-password | Email a password-reset code (`202` always) |
| POST | /api/v1/auth/verify-reset-otp | Exchange a reset code for a single-use ticket |
| POST | /api/v1/auth/reset-password | Set a new password with the reset ticket |
| POST | /api/v1/identifications | Plant identification |
| GET | /api/v1/identifications | Caller-owned paginated identification history |
| GET | /api/v1/identifications/{id} | One caller-owned identification |
| GET | /api/v1/plants | Search/browse plant library |
| GET | /api/v1/plants/{plantId} | Plant details |
| POST | /api/v1/chat | Text knowledge assistant |
| POST | /api/v1/chat/voice | Voice knowledge assistant |
| GET | /api/v1/chat/sessions | Caller-owned chat sessions |
| GET | /api/v1/chat/sessions/{sessionId} | One caller-owned chat session |
| GET | /api/v1/chat/sessions/{sessionId}/messages | Ordered chat messages and sources |
| GET | /api/v1/languages | Supported languages |
| GET | /api/v1/health | Basic service health |
| GET | /api/v1/actuator/health | Dependency-aware deployment health |

Auth bootstrap, health and languages are unauthenticated. Plant, identification,
chat, `/auth/me` and `/auth/logout` routes require `Authorization: Bearer <token>`.
The plant-code compatibility alias is described below. Operational routes also
include public `/actuator/info`, authenticated `/actuator/metrics` (and named
metrics), and ADMIN-only `/admin/test`.

## Identification flow
`POST /api/v1/identifications`

Web/Mobile → Spring Boot → IdentificationService → B2 image upload → AIClient → Person 1 AI Service → AIResponse → validation → heatmap archive → persistence → public IdentificationResponse → Web/Mobile

The request is `multipart/form-data` with one or more `image` parts (JPEG, PNG, or WebP; `IDENTIFICATION_MAX_IMAGES`, default 5) and an optional `plantPart`. Repeating the `image` part is the multi-image form; a single `image` part behaves exactly as before.

Each image is limited to 10 MB and the entire multipart request to 50 MB, including
multipart overhead. Use the upload limit to validate file selection in the client.

Uploaded images are archived in the private bucket whenever storage is enabled. The deployed AI service accepts multipart binary files, so `AI_IMAGE_TRANSPORT=multipart` is the default and the files are forwarded directly. For an AI deployment that accepts signed URLs, set `AI_IMAGE_TRANSPORT=url`; the service then receives short-lived presigned URLs (15 minutes by default).

The public identification response MUST conform to `shared/contracts/identification-response.schema.json`.

At minimum it carries `request_id`, `status`, `plant_id`, `confidence`, `model_version`, and `warnings`.

When storage is enabled it also carries `images[]`, one entry per uploaded image in order:

| Field | Description |
|---|---|
| `sequence_number` | 1-based position of the image |
| `plant_part` | Caller-supplied plant part, or null |
| `image_url` | Fresh presigned display URL (60 minutes by default) |
| `heatmap_urls` | Heatmap display URLs for this image (empty when none) |

`explanation.heatmap_url` keeps returning the first heatmap for backward compatibility. Signed URLs are generated on read, so `GET /api/v1/identifications/{id}` and the history endpoints always return fresh ones; the stored `response_payload` intentionally contains no expiring URLs.

Person 2 must not invent or recalculate confidence values returned by Person 1.

### Identification history

`GET /identifications?page=0&size=20&sort=newest` returns a Spring page with
`content` and pagination metadata. `page` starts at 0; `size` is 1–100; `sort`
is `newest` or `oldest`. `GET /identifications/{id}` returns one history item.
`id` here is the numeric identification ID, distinct from the shared plant ID.

Each item has `id`, `request_id`, `status`, `plant_id`, `confidence`,
`model_version`, `created_at` and `images`. History image fields are `plant_part`,
`sequence_number`, `image_reference` (a fresh signed display URL when storage is
enabled) and `heatmap_urls`. Creation responses instead name that URL `image_url`,
as specified in the identification response schema. Clients should refresh history
to renew expired URLs. Another user's identification returns `404`.

Send `Idempotency-Key` when retrying the same identification. Every response also
has `X-Request-Id` for tracing; browser clients can read it and `Retry-After`.

## Plant identity
`shared/constants/plant-classes.json` is the authoritative shared mapping of supported plant IDs.

The same `plant_id` must identify the same plant in PostgreSQL, AI results, knowledge content, and frontend data.

The canonical identity shape is defined in `shared/contracts/plant.schema.json`.

## Plant Library
The Plant Library is a public Spring Boot feature and is separate from image identification.

- `GET /api/v1/plants`
- `GET /api/v1/plants/{plantId}`

Both routes require a bearer token. `plantId` is the shared string (for example
`PLANT_001`), never the numeric database primary key. An unknown ID returns
`404 NOT_FOUND`. `/plants/code/{plantCode}` remains a compatibility alias with the
same response shape.

The list returns a JSON array; detail returns one object. Each object conforms
exactly to `shared/contracts/plant.schema.json`:

```json
{
  "plant_id": "PLANT_001",
  "plant_identifier": "Aloevera",
  "common_name": "Aloe Vera",
  "ayurvedic_regional_names": ["Kumari", "Ghritakumari"],
  "scientific_name": "Aloe vera (L.) Burm.f."
}
```

`GET /api/v1/plants?search=Kumari` uses the same array shape. Internal database IDs,
timestamps, and application content fields are not part of this identity contract.

Plant master/application data is stored by Person 2. Plant knowledge/RAG content remains owned by Person 3.

## Chat
- `POST /api/v1/chat` — text question; the backend calls Person 3's deployed
  `POST /knowledge/chat` and returns the answer with sources.
- `POST /api/v1/chat/voice` — present for clients, but Person 3 exposes no speech-to-text
  route, so it returns `501 VOICE_INPUT_UNSUPPORTED`. Request spoken output by sending text
  with `generate_audio: true`.
- `GET /api/v1/chat/sessions` — caller-owned sessions, newest activity first.
- `GET /api/v1/chat/sessions/{sessionId}` — one caller-owned session.
- `GET /api/v1/chat/sessions/{sessionId}/messages` — persisted messages, oldest first, with
  the sources stored alongside each answer.

Spring Boot authenticates/validates the request and calls the internal Knowledge Service. Web/Mobile must not call the Knowledge Service directly.

```json
{
  "message": "What are its traditional uses?",
  "plant_id": "PLANT_001",
  "language": "en",
  "session_id": null,
  "generate_audio": false
}
```

The first request creates a session; reuse its returned `session_id` for later
turns. Session/message lists are paginated with `page` (0+) and `size` (1–100).
Another user's session or messages return `404`. The legacy `conversation_context`
field is accepted but ignored; stored history is authoritative.

### Conversation memory
Person 3's deployed service does not keep session state, so the backend owns it: every
authenticated turn persists `chat_sessions` and `chat_messages`, and the last few messages are
prepended to the outbound `message` as an explicitly labelled context block. `session_id`
round-trips to Person 3 but is advisory only.

Tuning: `CHAT_HISTORY_ENABLED` (default `true`), `CHAT_HISTORY_MESSAGE_LIMIT` (default `6`),
`CHAT_HISTORY_CHAR_LIMIT` (default `2000`). Set `CHAT_HISTORY_ENABLED=false` to answer each
question independently, or if prepended history degrades retrieval quality.

The request and response bodies are frozen in `docs/api/knowledge-api.md`; the response MUST conform to `shared/contracts/knowledge-response.schema.json`.

## Languages
`GET /api/v1/languages` returns the languages defined in `shared/constants/supported-languages.json`.

## Errors
Public errors MUST use `shared/contracts/error.schema.json`. Public error vocabulary is maintained in `shared/constants/error-codes.json`.

Oversized uploads return `413 UPLOAD_TOO_LARGE`; unexpected application errors
return `500 INTERNAL_ERROR`. These codes are included in the public vocabulary.
Requests rejected by the reverse proxy may have its own error body; frontend
clients must handle non-JSON HTTP errors as well.

## Authentication and security
The backend owns authentication, JWT/session handling, authorization, validation, request-size limits, file validation, rate limiting, and centralized error handling.

Frontend clients do not receive database credentials, AI service URLs, AI model paths, RAG database credentials, LLM keys, or JWT secrets.

### Auth flows

The complete client-facing contract is in `docs/AUTH_API_DOCUMENTATION.md`. Summary:

- **Registration:** `POST /auth/register` creates a pending account and emails a 6-digit code. The account cannot log in until `POST /auth/verify-email` succeeds. Existing user accounts return `409 CONFLICT`; repeating an active pending registration returns `201` without replacing its password or sending another code. Use resend-verification for a new code.
- **Verification:** codes are single use, expire after 5 minutes (default), allow 5 wrong attempts before being burned, and are bound to the user and purpose. `POST /auth/verify-email` is idempotent for an already-verified account.
- **Login:** `POST /auth/login` returns `{token, tokenType, expiresIn}` for a verified account. Wrong credentials and unknown addresses both return `401 INVALID_CREDENTIALS`; a correct password on an unverified account returns `403 EMAIL_NOT_VERIFIED` (checked after the password, so it cannot reveal registration to someone who does not know the password).
- **Token use:** send `Authorization: Bearer <token>` on protected requests. Tokens are stateless and valid for 1 hour by default. `GET /auth/me` returns the caller's role and verification state.
- **Logout:** `POST /auth/logout` revokes the presented token immediately; other devices stay signed in.
- **Password reset:** `POST /auth/forgot-password` always answers `202` (no account enumeration). `POST /auth/verify-reset-otp` exchanges a valid code for a single-use ticket; `POST /auth/reset-password` takes `reset_token`, `password`, `confirm_password` and updates the existing account. The reset increments `users.token_version`, so every previously issued token stops working.

### Storage and secrets

- Passwords are BCrypt-hashed; plaintext passwords are never stored or logged.
- OTPs are stored only as an HMAC-SHA-256 digest (`OTP_HASH_SECRET`, falling back to `JWT_SECRET`), never in plaintext, never in responses and never in logs.
- Reset tickets are stored as SHA-256 digests in `password_reset_tokens` and are single use.
- Logout revocations are recorded in `revoked_tokens` by JWT `jti` until natural expiry.
- Render email uses Brevo HTTPS (`MAIL_PROVIDER=brevo`); SMTP (`MAIL_PROVIDER=smtp`) remains available for local Mailpit. Missing mail configuration makes sends fail with `503 EMAIL_DELIVERY_FAILED` instead of silently succeeding.
- Plant images and heatmaps are stored in a private Backblaze B2 bucket. The database stores object keys only; clients receive short-lived presigned GET URLs. B2 credentials live in environment variables and are never returned to clients. Storage failures return `503 STORAGE_UNAVAILABLE`.

### Authorization

- Every route is authenticated by default except the endpoints listed under **Public endpoints** that are explicitly `permitAll` (registration, verification, login, forgot-password, verify-reset-otp, reset-password, health, languages, Swagger and Actuator health/info).
- Roles are `USER` (default) and `ADMIN`. `@PreAuthorize` guards `/admin/**`; a `USER` receives `403`.
- Ownership is enforced per record: identification history and chat sessions are scoped to the authenticated user, and another user's id returns `404`.
- The JWT filter rejects tokens for unverified accounts, revoked `jti`s, and tokens whose `tv` is older than `users.token_version`. Client-side route guards are convenience only; the server enforces every rule.
