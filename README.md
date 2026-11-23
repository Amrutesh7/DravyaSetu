# DravyaSetu

Spring Boot is the public API for the React/Flutter clients. It owns authentication,
PostgreSQL, identification and chat history, and calls the separately deployed AI
and Knowledge services. Frontend clients communicate with Spring Boot only.

## Integration documentation

| Audience | Start here | Contracts and examples |
| --- | --- | --- |
| React / Flutter | [Backend API](docs/api/backend-api.md), [authentication flows](docs/AUTH_API_DOCUMENTATION.md) | [Shared schemas](shared/contracts/), [sample responses](shared/sample-data/) |
| AI service | [AI request/response contract](docs/api/ai-api.md) | [AI response schema](shared/contracts/ai-response.schema.json), [plant IDs](shared/constants/plant-classes.json) |
| Knowledge service | [Knowledge request/response contract](docs/api/knowledge-api.md) | [Knowledge response schema](shared/contracts/knowledge-response.schema.json), [languages](shared/constants/supported-languages.json) |
| Backend / deployment | [Backend setup](backend/README.md), [database schema](docs/database/schema.md) | [Render setup](docs/DEPLOYMENT_SETUP.md), [deployment checklist](docs/DEPLOYMENT_CHECKLIST.md), [Render Blueprint](render.yaml) |

Local API: `http://localhost:8080/api/v1`.
Swagger: `http://localhost:8080/api/v1/swagger-ui/index.html`.
Replace the host with the Render service's HTTPS URL after deployment.

Shared plant IDs, languages, roles and error codes live in [shared/constants](shared/constants/).
[Integration rules](docs/architecture/integration-rules.md) describe service ownership
and how contract changes are coordinated. The requirements PDFs remain in `docs/`.

## Current integration limits

- Render configuration selects real AI and Knowledge clients. Live service checks
  and B2 verification remain open in the deployment checklist.
- Voice input is deferred; `/api/v1/chat/voice` returns `501 VOICE_INPUT_UNSUPPORTED`.
- React, Flutter and Python implementations are maintained by their respective
  teammates. Their folders currently contain handoff notes, rather than runnable apps.
- Keep credentials in ignored environment files or provider secret settings.
