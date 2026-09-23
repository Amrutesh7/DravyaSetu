# DravyaSetu Service Boundaries

| Person | Owns | Does not own |
|---|---|---|
| Person 1 | AI/CV, image quality, classification, confidence, unknown detection, multi-image fusion, Grad-CAM, visual similarity, AI service | Spring Boot, PostgreSQL, auth, RAG, LLM, language, voice, UI |
| Person 2 | Spring Boot public API, authentication, application orchestration, PostgreSQL, AI/Knowledge clients | AI model internals, RAG internals, UI |
| Person 3 | Knowledge content, ingestion, RAG, retrieval, LLM generation, multilingual processing, STT/TTS, Knowledge Service | Spring Boot public API, application DB ownership, UI, AI model internals |
| Person 4 | React Web, Flutter Mobile, user-facing flows | Direct AI/Knowledge service calls, DB access, service internals |

## Communication
- Person 4 → Person 2
- Person 2 → Person 1
- Person 2 → Person 3
- Person 1 → Person 2 through the AI contract
- Person 3 → Person 2 through the Knowledge contract

Normal application flow must not use Person 4 → Person 1 or Person 4 → Person 3 direct calls.
