# DravyaSetu Integration Rules

## 1. Service topology
The current application topology is:

Web/Mobile → Spring Boot Public API → internal services

Spring Boot is the application gateway. Web and Mobile do not call the AI Service or Knowledge Service directly.

## 2. Plant identity
`shared/constants/plant-classes.json` is the authoritative shared mapping for supported plant classes.

The same `plant_id` must mean the same plant in Person 1 AI, Person 2 PostgreSQL/application layer, Person 3 Knowledge Service, and Person 4 Web/Mobile.

Do not create service-specific plant numbering.

## 3. AI boundary
Person 1 provides visual intelligence through the internal AI service. Person 2 calls the AI service and consumes `shared/contracts/ai-response.schema.json`.

Person 1 does not access PostgreSQL and does not own application business logic.

## 4. Knowledge boundary
Person 3 provides knowledge/RAG/language/voice intelligence through the internal Knowledge Service. Person 2 calls it.

Web/Mobile do not call Person 3 directly.

## 5. Contract changes
Changes to shared IDs, request/response fields, status values, error codes, or language codes must be coordinated before implementation code is changed.

## 6. Unknown vs failure
An AI result such as `UNKNOWN` is a valid result. A service failure such as `MODEL_UNAVAILABLE` is a system/service condition. Do not collapse the two.

## 7. Persistence
Person 2 owns PostgreSQL and application persistence. Person 1 does not write identification records directly. Person 3 does not create random application tables in Person 2's database.

## 8. Request tracing
Important cross-service requests carry a `request_id`, which should be preserved through service calls and logs.

## 9. Secrets
Frontend clients must never receive database credentials, service secrets, model paths, LLM keys, or JWT secrets.
