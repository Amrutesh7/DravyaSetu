# AI service handoff

The AI service is maintained and deployed by Person 1. This repository contains
the Spring Boot client and shared integration contracts, not the model implementation.

- [AI request/response contract](../docs/api/ai-api.md)
- [AI response schema](../shared/contracts/ai-response.schema.json)
- [Example response](../shared/sample-data/sample-ai-response.json)
- [Authoritative plant IDs](../shared/constants/plant-classes.json)

Spring Boot calls Modal's `POST /ai/analyze` with repeated `images` parts and a `request_id`.
Its public `/api/v1/identifications` endpoint still accepts frontend `image` parts.
The backend archives uploaded images and returned heatmaps in B2. Frontend clients
call Spring Boot only. Coordinate contract changes before changing fields or plant IDs.
