# DravyaSetu Public Backend API

## Ownership
Person 2 owns the public Spring Boot API. Web and Mobile clients communicate with Spring Boot only.

## Public endpoints
| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/v1/auth/register | User registration |
| POST | /api/v1/auth/login | User authentication |
| POST | /api/v1/identifications | Plant identification |
| GET | /api/v1/plants | Search/browse plant library |
| GET | /api/v1/plants/{plantId} | Plant details |
| POST | /api/v1/chat | Text knowledge assistant |
| POST | /api/v1/chat/voice | Voice knowledge assistant |
| GET | /api/v1/languages | Supported languages |

## Identification flow
`POST /api/v1/identifications`

Web/Mobile → Spring Boot → IdentificationService → AIClient → Person 1 AI Service → AIResponse → validation → persistence → public IdentificationResponse → Web/Mobile

The public identification response MUST conform to `shared/contracts/identification-response.schema.json`.

At minimum it carries `request_id`, `status`, `plant_id`, `confidence`, `model_version`, and `warnings`.

Person 2 must not invent or recalculate confidence values returned by Person 1.

## Plant identity
`shared/constants/plant-classes.json` is the authoritative shared mapping of supported plant IDs.

The same `plant_id` must identify the same plant in PostgreSQL, AI results, knowledge content, and frontend data.

The canonical identity shape is defined in `shared/contracts/plant.schema.json`.

## Plant Library
The Plant Library is a public Spring Boot feature and is separate from image identification.

- `GET /api/v1/plants`
- `GET /api/v1/plants/{plantId}`

Plant master/application data is stored by Person 2. Plant knowledge/RAG content remains owned by Person 3.

## Chat
- `POST /api/v1/chat`
- `POST /api/v1/chat/voice`

Spring Boot authenticates/validates the request and calls the internal Knowledge Service. Web/Mobile must not call the Knowledge Service directly.

The Knowledge Service response MUST conform to `shared/contracts/knowledge-response.schema.json`.

## Languages
`GET /api/v1/languages` returns the languages defined in `shared/constants/supported-languages.json`.

## Errors
Public errors MUST use `shared/contracts/error.schema.json`. Public error vocabulary is maintained in `shared/constants/error-codes.json`.

## Authentication and security
The backend owns authentication, JWT/session handling, authorization, validation, request-size limits, file validation, rate limiting, and centralized error handling.

Frontend clients do not receive database credentials, AI service URLs, AI model paths, RAG database credentials, LLM keys, or JWT secrets.
