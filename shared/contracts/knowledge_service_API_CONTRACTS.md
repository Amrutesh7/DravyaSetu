# DravyaSetu – Knowledge Service API Documentation

**Service:** Python FastAPI Knowledge Service  
**Service Owner:** Person 3 – Knowledge Service  
**API Consumer:** Person 2 – Spring Boot Backend  
**Frontend Consumer:** Person 4 – React Frontend  
**Deployment:** Render  
**Base URL:** https://dravyasetu-knowledge-service.onrender.com  
**API Documentation:** https://dravyasetu-knowledge-service.onrender.com/docs

---

# 1. Overview

The DravyaSetu Knowledge Service provides multilingual, context-aware Ayurvedic medicinal plant information through a Retrieval-Augmented Generation (RAG) pipeline.

The service is responsible for:

1. Multilingual input processing.
2. Language detection.
3. Query translation when required.
4. Retrieval of relevant plant knowledge.
5. Embedding-based semantic search.
6. Context-grounded answer generation using Gemini.
7. Conversation-context-aware response generation.
8. Incremental conversation summarization.
9. Voice input and output integration (under development).

The service communicates with the Spring Boot backend through REST APIs.

# 2. Technology Stack

| Component | Technology |
|---|---|
| API Framework | Python FastAPI |
| Language Detection | Custom language service |
| Translation | Existing multilingual translation service |
| Embeddings | Sentence Transformers |
| Vector Database | Qdrant |
| LLM | Google Gemini |
| Speech Recognition | Groq Whisper (voice integration in progress) |
| Text-to-Speech | Edge TTS (voice integration in progress) |
| Deployment | Render |

# 3. API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/knowledge/chat` | Knowledge chat |
| POST | `/api/v1/chat` | Versioned knowledge chat endpoint |
| POST | `/api/v1/chat/memory/summarize` | Conversation summarization |

The two chat endpoints provide the same chat functionality. The versioned endpoint is the preferred integration path.

Voice upload endpoints will be documented after implementation.

# 4. Health Check API

**Method:** GET  
**Endpoint:** `/health`

Purpose:
- Checks whether the Knowledge Service is running.
- Useful for deployment verification and service monitoring.

# 5. Knowledge Chat API

**Method:** POST  
**Endpoint:** `/api/v1/chat`

Backward-compatible endpoint: `/knowledge/chat`

**Purpose:**

Receives a user's question from the Spring Boot backend, processes it through the multilingual RAG pipeline and returns a generated answer with its retrieved sources.

## Request fields

See `knowledge-chat-request.schema.json`.

| Field | Type | Description |
|---|---|---|
| request_id | string | Unique request identifier |
| session_id | string | Conversation session identifier |
| message | string | Current user message |
| input_type | string | Input modality |
| input_language | string/null | Input language, when known |
| response_language | string/null | Desired answer language |
| plant_id | string/null | Selected plant identifier |
| conversation_context | object/null | Previous conversation context |
| conversation_context.summary | string/null | Previously stored conversation summary |
| conversation_context.recent_messages | array | Recent unsummarized messages |
| generate_audio | boolean | Whether audio output is requested |

## Example request

```json
{
  "request_id": "REQ_001",
  "session_id": "SESSION_001",
  "message": "What are the medicinal uses of Aloe Vera?",
  "input_type": "text",
  "input_language": "en",
  "response_language": "en",
  "plant_id": "PLANT_001",
  "conversation_context": {
    "summary": null,
    "recent_messages": []
  },
  "generate_audio": false
}
```

## Example response

```json
{
  "request_id": "REQ_001",
  "session_id": "SESSION_001",
  "detected_language": "en",
  "transcript": null,
  "answer": "Aloe Vera is traditionally used for several medicinal applications.",
  "language": "en",
  "sources": [],
  "voice_output": null,
  "warnings": []
}
```

The response above is illustrative. Actual answers and source entries depend on the retrieved knowledge.

See `knowledge-response.schema.json` for the response structure.

# 6. Multilingual Processing

The Knowledge Service supports multilingual query processing.

Currently tested languages:

- English
- Kannada
- Hindi
- Telugu

## Processing workflow

1. Receive the user message.
2. Detect the input language.
3. Translate the query into English when required by the retrieval pipeline.
4. Generate query embeddings.
5. Retrieve relevant knowledge from Qdrant.
6. Pass retrieved context to Gemini.
7. Generate a context-grounded answer.
8. Translate the answer into the requested response language when required.
9. Return the answer with source information and warnings.

The `input_language` and `response_language` fields allow the backend to communicate language preferences.

# 7. Conversation Context and Memory

Conversation memory enables the service to generate answers using earlier messages rather than processing every query independently.

The implementation separates responsibilities between Python and Spring Boot.

## Spring Boot responsibilities – Person 2

- Store conversation messages in PostgreSQL.
- Maintain session identifiers.
- Maintain the latest conversation summary.
- Track summarization checkpoints.
- Select messages eligible for summarization.
- Preserve recent messages.
- Send the saved summary and recent messages with subsequent chat requests.

## Python responsibilities – Person 3

- Receive conversation context.
- Use the previous summary and recent messages while generating answers.
- Generate updated summaries through the summarization API.
- Return the updated summary and summarization checkpoint.

## Context structure

```json
{
  "summary": "The user is learning about Aloe Vera and its traditional applications.",
  "recent_messages": [
    {
      "role": "user",
      "content": "Tell me about Aloe Vera."
    },
    {
      "role": "assistant",
      "content": "Aloe Vera is a medicinal plant."
    }
  ]
}
```

The current message is sent separately in the `message` field.

## Memory workflow

1. Store all messages in PostgreSQL.
2. When the summarization threshold is reached, select eligible messages.
3. Send the existing summary and newly eligible messages to Python.
4. Python generates an updated summary.
5. Spring Boot stores the returned summary and checkpoint.
6. Future chat requests include the saved summary and recent messages.

**Important:** Original messages are never deleted after summarization. Summaries supplement the original conversation history.

# 8. Conversation Summarization API

**Method:** POST  
**Endpoint:** `/api/v1/chat/memory/summarize`

**Purpose:**

Incrementally summarizes conversation history using the existing summary and newly eligible messages.

## Request fields

See `memory-summarize-request.schema.json`.

| Field | Type | Description |
|---|---|---|
| request_id | string | Unique request identifier |
| session_id | string | Conversation session identifier |
| existing_summary | string/null | Previously stored summary |
| messages_to_summarize | array | Messages selected for summarization |
| message_id | string | Original message identifier |
| role | string | user or assistant |
| content | string | Message content |
| created_at | date-time | Original message timestamp |
| summary_max_tokens | integer | Target summary token limit |
| language | string | Summary language |
| metadata | object | Additional context, including plant_id when applicable |

## Example request

```json
{
  "request_id": "REQ_SUM_001",
  "session_id": "SESSION_001",
  "existing_summary": null,
  "messages_to_summarize": [
    {
      "message_id": "MSG_001",
      "role": "user",
      "content": "Tell me about Aloe Vera.",
      "created_at": "2026-11-27T10:00:00Z"
    },
    {
      "message_id": "MSG_002",
      "role": "assistant",
      "content": "Aloe Vera is a medicinal plant.",
      "created_at": "2026-11-27T10:01:00Z"
    }
  ],
  "summary_max_tokens": 300,
  "language": "en",
  "metadata": {
    "plant_id": "PLANT_001"
  }
}
```

## Response fields

See `memory-summarize-response.schema.json`.

| Field | Type | Description |
|---|---|---|
| request_id | string | Original request identifier |
| session_id | string | Conversation session identifier |
| summary | string | Updated conversation summary |
| summarized_message_count | integer | Number of messages summarized |
| summarized_through_message_id | string/null | Last message included in the summary |
| summary_token_count | integer | Estimated summary token count |
| warnings | array | Summarization warnings |

## Example response

```json
{
  "request_id": "REQ_SUM_001",
  "session_id": "SESSION_001",
  "summary": "The user is learning about Aloe Vera and its medicinal applications.",
  "summarized_message_count": 2,
  "summarized_through_message_id": "MSG_002",
  "summary_token_count": 20,
  "warnings": []
}
```

# 9. Voice Assistant Integration

**Status: Under development**

The planned voice pipeline uses:

| Function | Technology |
|---|---|
| Speech-to-Text | Groq Whisper |
| Text-to-Speech | Edge TTS |

## Planned workflow

1. User submits audio.
2. Groq Whisper converts speech into text.
3. The language service detects the input language.
4. Query translation is performed when required.
5. The existing RAG pipeline generates an answer.
6. The answer is translated into the requested language.
7. Edge TTS generates speech.
8. The service returns the answer and audio output.

The existing `voice.schema.json` describes a preliminary voice response envelope.

The final audio payload structure, upload endpoint, encoding and transport format will be documented after implementation and testing.

# 10. Error Handling

The service uses warnings and errors to communicate processing issues.

- `warnings`: Non-fatal issues encountered during processing.
- `errors`: Error details, where included in the response contract.

Refer to `error.schema.json` for the shared error structure.

# 11. Plant Identification and Shared Plant Data

DravyaSetu uses fixed plant identifiers shared across the project.

The Knowledge Service receives `plant_id` when the conversation relates to a selected plant.

The plant identification response and plant master data are governed by:

- `identification-response.schema.json`
- `plant.schema.json`

The fixed plant class directory must remain consistent across the AI, backend, knowledge and frontend components.

# 12. Responsibility Division

| Team Member | Responsibility |
|---|---|
| Person 2 | Spring Boot backend, PostgreSQL, API orchestration, session and conversation persistence |
| Person 3 | Python Knowledge Service, multilingual processing, RAG, Qdrant retrieval, Gemini generation, summarization and voice integration |
| Person 4 | React frontend, user interface, response rendering, audio recording and playback integration |

# 13. Integration Guidelines

1. Use the deployed Knowledge Service base URL.
2. Generate a unique `request_id` for every request.
3. Maintain the same `session_id` throughout a conversation.
4. Pass the current message separately from recent conversation messages.
5. Pass saved conversation context when available.
6. Store conversation messages and summaries in PostgreSQL through the backend.
7. Do not delete original messages after summarization.
8. Use the shared schemas as the agreed integration reference.
9. Preserve existing API fields when extending functionality.
10. Confirm any contract changes with the other responsible team members.

# 14. Implementation Status

| Feature | Status |
|---|---|
| FastAPI Knowledge Service | Implemented |
| Health endpoint | Implemented |
| Knowledge chat API | Implemented |
| RAG retrieval | Implemented |
| Gemini answer generation | Implemented |
| Multilingual processing | Implemented and tested |
| Kannada, Hindi and Telugu testing | Completed |
| Conversation-context-aware generation | Implemented and tested |
| Incremental summarization endpoint | Implemented and tested |
| PostgreSQL conversation persistence | Backend responsibility |
| Automatic summarization scheduling | Backend responsibility |
| Groq Whisper speech recognition | Integration in progress |
| Edge TTS speech generation | Integration in progress |
| End-to-end voice assistant | Pending integration testing |

---

**Document ownership:** Person 3 – Python Knowledge Service

**Contract location:** `shared/contracts/`

This documentation serves as the integration reference for the DravyaSetu Knowledge Service.
