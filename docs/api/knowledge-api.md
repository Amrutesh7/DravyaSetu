# DravyaSetu Knowledge Service API

## Ownership
- Provider: Person 3 — RAG + Knowledge + Language + Voice
- Consumer: Person 2 — Spring Boot backend
- Web and Mobile clients do not call the Knowledge Service directly.

## Internal endpoints
The current architecture permits internal endpoint names such as:
- `POST /knowledge/chat`
- `POST /knowledge/chat/voice`

The exact internal route naming is an implementation choice, but the service boundary MUST remain:

Web/Mobile → Spring Boot → Knowledge Service

## Chat request contract
Person 2 sends the Knowledge Service:
- `request_id`
- `plant_id` when plant context exists
- `message`
- `language`
- conversation/session context when available
- voice/audio information for voice requests when applicable

When `plant_id` is present, it MUST use the shared mapping in `shared/constants/plant-classes.json`.

## Chat response contract
The response MUST conform to `shared/contracts/knowledge-response.schema.json`.

The response contains:
- `request_id`
- `answer`
- `language`
- `sources`
- voice output metadata when applicable
- `warnings`
- controlled errors when applicable

## Knowledge responsibilities
Person 3 owns curated knowledge content, document ingestion, cleaning/chunking, embeddings, vector retrieval, RAG, LLM generation, multilingual processing, speech-to-text, and text-to-speech.

Person 2 owns the public application API and PostgreSQL application layer.

## Error handling
Knowledge-service failures must be controlled responses, not Python stack traces.

Documented knowledge-domain error codes include:
- `KNOWLEDGE_NOT_FOUND`
- `RETRIEVAL_FAILED`
- `LLM_UNAVAILABLE`
- `LANGUAGE_UNSUPPORTED`
- `STT_FAILED`
- `TTS_FAILED`
- `INVALID_REQUEST`

## Timeouts
Knowledge operations can involve LLM, STT, TTS, and vector retrieval latency. Person 2 must apply predictable timeouts and translate service failures into controlled public errors.

## Plant context
When a user asks a plant-specific question, Person 2 passes the shared `plant_id` to the Knowledge Service. Person 3 uses it to prioritize the correct plant knowledge.

## Boundary rule
Do not expose a second public frontend API from the Knowledge Service.
