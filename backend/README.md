# Spring Boot backend

Java 21, Maven, Spring Boot, PostgreSQL and Flyway. The public API base path is
`/api/v1`; Swagger is `/api/v1/swagger-ui/index.html`.

## Documentation

- [Frontend API](../docs/api/backend-api.md) and [authentication flows](../docs/AUTH_API_DOCUMENTATION.md)
- [AI integration](../docs/api/ai-api.md) and [Knowledge integration](../docs/api/knowledge-api.md)
- [Database schema](../docs/database/schema.md) and [V26 upgrade preflight](../docs/database/preflight-v26.sql)
- [Render/Neon/Brevo/B2 setup](../docs/DEPLOYMENT_SETUP.md) and [unfinished deployment checks](../docs/DEPLOYMENT_CHECKLIST.md)
- [Environment template](.env.example) and [shared contracts](../shared/contracts/)

## Run and test

Configure an ignored `.env` using the environment template. Both the repository
root and `backend/.env` are loaded during local startup. Use separate test settings
when exercising write flows; local mail can be captured by Mailpit.

```sh
cd backend
mvn spring-boot:run
mvn test -Ddocker.api.version=1.44
```

Tests require Docker for real PostgreSQL integration checks. The Docker API flag
supports Docker 29+ with the current Testcontainers version; omit it on older engines.
The root Compose file runs PostgreSQL, Mailpit and the backend. Mailpit's inbox
is `http://localhost:8025`.

## Runtime settings

- `real-ai` and `real-knowledge` select the real clients independently. Without
  each profile, its mock client is used. The checked-in Render Blueprint selects
  `real-ai,real-knowledge,production`; configured local environment files can also
  select profiles, so check `SPRING_PROFILES_ACTIVE` before starting.
- Render uses Neon and Brevo over HTTPS. SMTP remains available for local mail.
  Missing mail configuration makes sends fail; it does not silently simulate delivery.
- B2 stores private objects. PostgreSQL holds object keys, and clients receive
  expiring signed URLs regenerated on read. With storage disabled, uploads are not
  archived and usable image display URLs are unavailable.
- AI requests retry transient network/502/503/504 failures at most three times.
  Knowledge calls are not retried. Default connect/read timeouts are 10/90 seconds.
- Rate limiting is in memory and per instance. Shared enforcement is needed before
  horizontal scaling. Configure `TRUSTED_PROXY_CIDRS` only for actual trusted proxies.
- Configure exact browser origins through `CORS_ALLOWED_ORIGIN_PATTERNS`. Production
  defaults cover React development on localhost ports 5173 and 3000.
- Health/info are public; metrics require authentication. Admin application routes
  require the ADMIN role. Never expose credentials to clients.

Keep all Flyway migrations: an existing database validates its migration history.
Apply V26 only after the preflight passes for a populated V25 database. Deployment
readiness and live provider checks are tracked in the checklist linked above.
