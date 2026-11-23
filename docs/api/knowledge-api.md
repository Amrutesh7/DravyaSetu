# DravyaSetu Knowledge Service API

## Ownership
- Provider: Person 3 — RAG + Knowledge + Language + Voice
- Consumer: Person 2 — Spring Boot backend
- Web and Mobile clients do not call the Knowledge Service directly.

## Deployed service
- Base URL: `https://dravyasetu-knowledge-service.onrender.com`
- OpenAPI document: `/openapi.json`, interactive docs: `/docs`
- Exposed routes: `GET /health`, `GET /`, `POST /knowledge/chat`
- There is **no** `/knowledge/chat/voice` route. The service accepts text input only.

The Render instance sleeps when idle, so the first request after a quiet period can take tens
of seconds. The backend therefore uses a `90s` read timeout by default and a `10s` connect
timeout, both configurable (`KNOWLEDGE_READ_TIMEOUT_MS`, `KNOWLEDGE_CONNECT_TIMEOUT_MS`).

## Chat request contract
`POST /knowledge/chat`, `Content-Type: application/json`.

The backend also sends `X-Request-Id` so a slow call can be traced even if the response is lost.

```json
{
  "request_id": "REQ_001",
  "session_id": "SESSION_001",
  "message": "What are the traditional uses of Tulasi?",
  "input_type": "text",
  "input_language": "en",
  "response_language": "en",
  "plant_id": "PLANT_033",
  "generate_audio": false
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `request_id` | yes | Trace identifier created by Spring Boot. |
| `session_id` | yes | Backend-generated UUID identifying the chat session; history is currently owned by Spring Boot. |
| `message` | yes | 1–4000 characters from the client. The backend may prepend a labelled block of recent turns, because this service keeps no session memory. |
| `input_type` | no | Fixed literal `"text"`; the route rejects audio. |
| `input_language` | no | Defaults to `en`. The backend sends the caller's requested language. |
| `response_language` | no | Defaults to `en`. |
| `plant_id` | no | Shared `PLANT_###` identity from `shared/constants/plant-classes.json`. |
| `generate_audio` | no | `true` asks for `voice_output` metadata. Defaults to `false`. |

## Chat response contract
`HTTP 200`, `Content-Type: application/json`.

```json
{
  "request_id": "REQ_001",
  "session_id": "SESSION_001",
  "detected_language": "en",
  "transcript": null,
  "answer": "Tulasi is traditionally used for various purposes...",
  "language": "en",
  "sources": [
    {
      "source_id": "SRC_001",
      "title": "Tulasi",
      "reference": "Tulasi.pdf"
    }
  ],
  "voice_output": null,
  "warnings": []
}
```

The response MUST conform to `shared/contracts/knowledge-response.schema.json`. All nine
fields are required; `transcript` is `null` for text input and `voice_output` is `null`
unless `generate_audio` was requested.

## Conversation memory
Person 3's deployed service is stateless: it echoes `session_id` but does not use it for
history, and it ignores an undeclared `conversation_context` field. Person 2 therefore owns
conversation state. The backend persists every turn and prepends a labelled block of recent
turns to the outbound `message`. `session_id` remains in the contract so that Person 3 can
adopt server-side memory later without a wire change.

## Voice capability
- Voice **input** (speech-to-text) is not exposed by the deployed service. Spring Boot's
  `POST /chat/voice` therefore returns `501 VOICE_INPUT_UNSUPPORTED`; clients should send text
  to `POST /chat` and set `generate_audio: true`.
- Voice **output** (text-to-speech) is requested per chat through `generate_audio` and returned
  as `voice_output`.

## Error handling
Knowledge-service failures must be controlled responses, not Python stack traces. The backend
maps any transport failure, timeout, or non-2xx response to
`503 KNOWLEDGE_SERVICE_UNAVAILABLE`. Runtime logs include the request ID, duration,
upstream HTTP status or a DNS/TLS/connect/read failure category. Request/response
bodies are not included in these diagnostic messages. Response validation failures
also log their request ID and reason. A public 503 alone does not distinguish
transport failure from an invalid upstream response; inspect the correlated log.

Documented knowledge-domain error codes include:
- `KNOWLEDGE_NOT_FOUND`
- `RETRIEVAL_FAILED`
- `LLM_UNAVAILABLE`
- `LANGUAGE_UNSUPPORTED`
- `STT_FAILED`
- `TTS_FAILED`
- `INVALID_REQUEST`

## Plant context
When a user asks a plant-specific question, Person 2 passes the shared `plant_id` to the
Knowledge Service. Person 3 uses it to prioritize the correct plant knowledge. Plant identity
must never be renumbered by Person 3.

## Boundary rule
Do not expose a second public frontend API from the Knowledge Service.
