# DravyaSetu deployment checklist

Last updated: 2026-11-23. Target: Render backend, Neon Free PostgreSQL,
private Backblaze B2 storage, real AI and Knowledge services. No custom domain yet.

## Rules for continuing this work

Read this file before continuing deployment work. Keep every unfinished item in
the open list until its acceptance checks pass. Then move it to Verified with
the verification date and evidence; never delete unfinished work or treat merely
written configuration as a successful deployment. Never put credentials here.

## Open work

- [ ] D05 Configure Render's `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; confirm TLS,
  successful Flyway migration through V26 and application health using Neon.
  Local configuration and the packaged backend against Neon are verified; Render
  Environment and hosted health checks remain pending.
- [ ] D07 Create Brevo account, verify sender address, obtain API key, configure
  `MAIL_PROVIDER=brevo`, `BREVO_API_KEY`, `MAIL_FROM`, `MAIL_ENABLED=true` on Render.
  Verify actual delivery and register/verify/reset flows in a real inbox.
  Account/API authentication, active sender, one HTTP-accepted test email and inbox
  arrival confirmed by the user are complete. Render settings and hosted auth
  flows remain pending; do not send more emails without authorization.
- [ ] D08 Create a Render Docker web service from the prepared Blueprint with the
  production profile and Render-generated secrets; configure health check and verify its
  HTTPS onrender.com URL. No custom domain or certificate key is needed for that URL.
- [ ] D09 Configure exact React browser origins when supplied; verify preflight,
  login and uploads from a browser. Native Flutter clients are not subject to browser
  CORS; Flutter Web is. Confirm proxy trust without trusting all forwarding headers.
  Local files now allow localhost:5173 and localhost:3000; obsolete custom-domain
  settings were removed. Real HTTP preflight passes locally. Hosted browser flows
  and the teammate's eventual deployed origin remain pending.
- [ ] D10 Existing local B2 settings are present; verify bucket privacy, endpoint,
  region, scoped application key and permissions before enabling on Render. Set
  `B2_STORAGE_ENABLED=true` only when complete. Test upload, signed GET and cleanup
  with a disposable test object. Verify frontend image/heatmap display.
- [ ] D11 Configure B2 CORS for exact browser origins if fetch/canvas access is used;
  allow GET/HEAD only for the current backend-upload workflow. Keep the bucket private.
- [ ] D12 Verify the real AI and Knowledge request/response paths in staging. AI is
  currently reported unavailable; readiness remains open until real responses work.
  Voice input stays deferred by agreement.
- [ ] D13 Run final deployed smoke tests: health, registration, email verification,
  login, plant browse/detail, identification, B2 URLs, chat, ownership/history,
  reset, logout. Share HTTPS API URL, Swagger and API documentation with teammate.

## Verified

- [x] Repository cleanup (2026-11-23): removed empty frontend/Python scaffolds,
  redundant placeholders, obsolete status documentation, a completed one-off
  environment-copy helper, generated build/cache output and old startup logs.
  Kept requirements PDFs, all contracts, Java source/tests, V1–V26 migrations,
  private environment files, the database recovery dump and recovery tools.
  Root and teammate READMEs now link to canonical API/auth documentation; architecture
  notes are consolidated into `docs/architecture/integration-rules.md`. Corrected
  stale single-image AI documentation to match the agreed request contract.
  Validated 51 local documentation links, four sample response cases, shared JSON,
  both Compose configurations and Render build paths. No application logic changed.
- [x] Final local readiness audit (2026-11-23): reviewed the current backend scope
  and frontend handoff requirements in the PDFs against controllers and shared
  schemas. Fixed Swagger's camelCase schema mismatch by upgrading springdoc to
  the Spring Boot 4-compatible 3.2.1 and migrating two remaining Jackson imports.
  Request-ID filtering now precedes security, so 401 errors satisfy the error
  contract. Framework routing/method/binding errors retain their HTTP statuses;
  browser clients can read X-Request-Id and Retry-After. Logout now wraps token
  revocation and expired-token cleanup in one transaction, fixing an intermittent
  500 when expired revocations exist.
  Final package passed 177 tests, zero failures/errors/skips, including five real
  HTTP regression tests. An isolated PostgreSQL and local email capture service
  passed 45 HTTP checks covering auth/reset/logout, all 34 plant identities,
  multi-image identification/idempotency, chat/history, ownership, validation,
  CORS and HTTP errors. AI/Knowledge success paths used mocks and B2 was disabled
  in those smoke checks; this does not close D10 or D12. Browser Swagger checks
  verified health/languages 200, protected plants 401 with a nonempty request ID,
  and corrected snake_case plant fields. No additional external email was sent
  and no Neon application data was changed by the write tests. No CodeRabbit
  remote review was run because CLI availability could not be established.
- [x] D03 (2026-11-23) Neon direct endpoint authenticated over client TLS; destination
  public schema was verified empty. Actual destination PostgreSQL is 18.7, newer
  than source PostgreSQL 16.16. Singapore region selected in `render.yaml`.
- [x] D04 (2026-11-23) Source V25 preflight passed with no other source clients.
  Backed up to ignored `backups/local-before-neon-20261123T132118Z-bab7d9a7.dump`.
  Restored atomically with no owners/ACLs; every table count and sequence state
  matched. Preserved 7 users, 34 plants and all other data. Source data was not deleted.
- [x] Local Neon activation (2026-11-23): packaged backend started with real AI,
  real Knowledge and production profiles, applied V26 and returned health UP and
  the languages response. Both local `.env` files now use Neon and Brevo. Previous
  local connection settings are preserved in ignored `.env.local` and
  `backend/.env.local`, all private files mode 0600. The temporary startup test
  has stopped; no Render service has been created by this work.
- [x] Brevo account check (2026-11-23): API key authenticated; supplied sender is
  active. The one authorized deployment test was accepted (HTTP 201), and the user
  confirmed inbox arrival. The free sender domain is not DKIM-authenticated.
- [x] D01 (2026-11-23) Generated independent 48-byte random JWT/OTP secrets into both
  ignored environment files. Verified 96-character hex values, independence,
  cross-file equality and file mode 0600 without displaying values. Existing JWTs
  and OTPs issued under the old secrets become invalid after restart.
- [x] D02 (2026-11-23) Both Compose configurations, local environment files and the
  Render Blueprint enable real AI/Knowledge profiles. A profile-selection test
  proves mocks are absent even when the configured real URLs are unavailable.
  Live responses remain separately tracked in D12.
- [x] D06 (2026-11-23) Implemented Brevo HTTPS sender while retaining SMTP. Tests
  verify request shape, credential/provider failures and exactly one sender per
  provider selection. Inbox verification remains D07. Suite plus the added profile
  test: 172 passing tests, zero failures/errors. Compose and Blueprint structure
  checks pass; Render has not been deployed by this work.
- Database/application changes through V26 passed 168 tests before deployment work,
  including real PostgreSQL, concurrency and migration-upgrade tests. This does not
  establish hosted deployment readiness or live service availability.

## Inputs still needed

- Render service URL once created; React dev port(s) and eventual web-app origin.
- Copy saved Neon and Brevo settings into Render's secure Environment form; don't
  paste secret values into tracked files or documentation.
- Confirmation of existing B2 bucket/key settings and eventual browser origins.

Detailed setup steps: `docs/DEPLOYMENT_SETUP.md`.

## Local DNS and database recovery notes

This machine's resolver fails for the valid Neon hostname; Google and Cloudflare
DNS-over-HTTPS both resolve it. Migration tools used a per-process address with
the original hostname retained for TLS SNI. `scripts/neon-verify.py` uses a temporary
JVM hosts file only on this machine. No IP address is pinned in `DB_URL`, and no
system DNS settings were changed. Render must use its normal DNS resolution.

For local verification, build the backend package and run
`python3 scripts/neon-verify.py`; add `--serve` to keep the backend on localhost:18088.
It never sends email automatically.

Original database volume: `dravyasetu_postgres_data`. Original container's port
5432 was occupied by another service; a migration container named
`dravyasetu-migration-source` exposed the same existing data on localhost:15432.
That temporary container is now stopped; its container and data volume remain
available for recovery. `docker start dravyasetu-migration-source` restarts the
original local copy on that port without touching Neon.
Do not delete the volume or backups. Do not run `neon-migrate.py restore` after
cutover: it deliberately refuses matching source/target settings or nonempty targets.
