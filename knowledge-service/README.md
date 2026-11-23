# Knowledge service handoff

The Knowledge/RAG service is maintained and deployed by Person 3. This repository
contains its Spring Boot client and shared contracts, not the RAG implementation.

- [Knowledge request/response contract](../docs/api/knowledge-api.md)
- [Knowledge response schema](../shared/contracts/knowledge-response.schema.json)
- [Example response](../shared/sample-data/sample-chat-response.json)
- [Plant IDs and supported languages](../shared/constants/)

Spring Boot calls `POST /knowledge/chat` and owns authenticated conversation history.
Frontend clients call Spring Boot only. Voice input is deferred until the service
provides speech-to-text support. Coordinate contract changes before implementation.
