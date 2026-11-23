# React frontend handoff

The React app has not been implemented. Create its build configuration when work begins.

- [Public backend endpoints](../docs/api/backend-api.md)
- [Registration, login and password reset](../docs/AUTH_API_DOCUMENTATION.md)
- [Shared response schemas](../shared/contracts/) and [examples](../shared/sample-data/)

Call Spring Boot at `/api/v1`; use bearer tokens for protected routes. Repeat the
`image` multipart field for multiple uploads. Plant URLs use shared `PLANT_###` IDs.
Coordinate the browser origin with the backend CORS configuration. Voice input is deferred.
