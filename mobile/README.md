# Flutter frontend handoff

The Flutter app has not been implemented. Generate the app when work begins.

- [Public backend endpoints](../docs/api/backend-api.md)
- [Registration, login and password reset](../docs/AUTH_API_DOCUMENTATION.md)
- [Shared response schemas](../shared/contracts/) and [examples](../shared/sample-data/)

Call Spring Boot at `/api/v1`; use bearer tokens for protected routes. Plant URLs
use shared `PLANT_###` IDs. Native Flutter is not subject to browser CORS;
Flutter Web needs an allowed origin. Voice input is deferred.
