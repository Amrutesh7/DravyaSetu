# DravyaSetu Database Schema

## Ownership
PostgreSQL is owned by Person 2.

## Starting application tables
The current mini-project starts with:
- `users`
- `plants`
- `identifications`
- `identification_images`

Knowledge documents/vector indexes belong to Person 3; this application does not
create a `knowledge_documents` table. There are twelve application tables after V26.

Tables added by later migrations:
- `password_reset_tokens`
- `chat_sessions`
- `chat_messages`
- `email_otps`
- `revoked_tokens`
- `identification_heatmaps` (migration `V23`)
- `auth_audit_log` (migration `V24`) — append-only security audit events
- `pending_registrations` (migration `V25`) — OTP-before-account-creation staging

## Users
`users` is the account table. Columns after migration `V22__add_email_verification_and_otp.sql`:
- `id`
- `email` — unique, stored lowercase
- `password_hash` — BCrypt (`$2a$…`, 60 characters)
- `role` — `USER` or `ADMIN`
- `email_verified` — `boolean not null default false`
- `email_verified_at` — set when verification succeeds
- `token_version` — `integer not null default 0`; incremented on every password change so older JWTs are rejected
- `created_at`, `updated_at`

Existing rows were backfilled with `email_verified = true` and `email_verified_at = now()` so accounts created before email verification existed keep access. Only accounts created after V22 must verify.

## Pending registrations
`pending_registrations` (migration `V25`) implements OTP-before-account-creation. No `users` row is
created until the user submits a correct verification code. Columns:
- `id`
- `email` — unique
- `password_hash` — BCrypt hash of the registration password (never plaintext)
- `role` — `USER`
- `otp_hash` — HMAC-SHA-256 of the OTP (also bound to purpose)
- `attempts` — wrong-code counter (burned at 5)
- `expires_at` — 5 minutes after issue
- `consumed_at` — set when the code is accepted (repeat submissions become idempotent)
- `last_sent_at`, `created_at`
- `send_window_started_at`, `send_count` — actual sends per address in a one-hour window

On valid verification, `AuthService` creates the `users` row from this record (verified) and marks
the pending registration consumed in the same transaction. Expired/burned records are
retained for resend/re-registration and the per-address send budget. A resend clears
the consumption marker, resets attempts, and starts a fresh code expiry. A registration
for an email that already has a **verified** account is rejected with `409 CONFLICT`.

## Email OTPs
`email_otps` stores one row per issued code for password resets and legacy verification. Plaintext codes are never stored.
- `id`
- `user_id` → `users(id)` (`ON DELETE CASCADE`)
- `purpose` — `EMAIL_VERIFICATION` or `PASSWORD_RESET`
- `otp_hash` — 64-character HMAC-SHA-256 hex digest, bound to `user_id` + `purpose`
- `attempts` — incremented on each wrong submission
- `expires_at` — 5 minutes after issue by default
- `consumed_at` — set when used, expired, burned after too many attempts, or replaced
- `last_sent_at`, `created_at`

A partial unique index `uq_email_otps_active` on `(user_id, purpose) WHERE consumed_at IS NULL` guarantees at most one active code per user and purpose. Resend cooldown and the hourly cap are enforced in `OtpService`, not by the schema.

Issuance locks the user row and flushes retirement of the old code before inserting
the replacement. Verification locks the code row. Pending registration workflows
also take a transaction-scoped PostgreSQL advisory lock by normalized email, so
concurrent requests serialize even before a record exists. Wrong pending-code
attempts commit while returning controlled errors; account creation and consumption
commit together.

## Revoked tokens
`revoked_tokens` is the logout denylist:
- `jti` (UUID, primary key) — the JWT id
- `expires_at` — the token's own expiry; rows past this point are purged opportunistically
- `created_at`

Password reset does not insert here; it bumps `users.token_version`, which invalidates all tokens for that user.

## Password reset tokens
`password_reset_tokens` now stores the single-use ticket issued *after* a reset OTP is verified:
- `user_id`
- `token_hash` — SHA-256 of the opaque ticket; the ticket itself is only returned once
- `expires_at` — 10 minutes by default
- `used_at`

The OTP is consumed at `POST /auth/verify-reset-otp`; the ticket is consumed at `POST /auth/reset-password`.

V26 enforces one ticket row per user. Ticket replacement and password changes lock
the user before the ticket, avoiding conflicting lock order. `created_at` is mapped
by the entity and populated on insert.

## Plant master
The `plants` table represents application data for the shared plant identities.

Minimum identity fields:
- `id`
- `plant_code` / shared `plant_id`
- `common_name`
- `scientific_name`
- application metadata as required

The authoritative supported-class mapping is `shared/constants/plant-classes.json`.

## Identification
`identifications` stores application-level identification records, including:
- id
- user_id
- plant_id
- status
- confidence
- model_version
- created_at

`identification_images` stores the images belonging to an identification:
- `image_reference` — Backblaze B2 object key; nullable when storage is disabled
- `plant_part`
- `sequence_number` — 1-based; preserves upload order
- `created_at`

`identification_heatmaps` stores one row per heatmap and links it to both the identification and the image it explains:
- `identification_id` → `identifications(id)` (`ON DELETE CASCADE`)
- `identification_image_id` → `identification_images(id)` (`ON DELETE CASCADE`)
- `storage_key` — B2 object key once the heatmap has been archived
- `source_url` — original AI heatmap URL fallback when archiving failed
- `content_type`
- `sequence_number`
- `created_at`, `updated_at`, `version`

A check constraint requires at least one of `storage_key` or `source_url`. The database never stores presigned URLs: keys are persisted and signed GET URLs are generated on read with a TTL (60 minutes for display, 15 minutes for the AI service by default).

Person 1 performs multi-image evidence fusion. Person 2 stores the application record, image keys, and heatmap keys; Person 2 does not recalculate AI results.

## V26 integrity and upgrade

V26 adds normalized-email checks, valid pending role/attempt/send counters, nonempty
plant identities and required scientific names, and completed identification checks:
`KNOWN` requires a plant FK; `UNKNOWN` requires null. Confidence and model version
are required. Confidence uses unconstrained PostgreSQL `NUMERIC` so AI decimals are
not rounded to four places; valid original snapshots restore historical precision
where available. Data that was already rounded without a snapshot cannot be reconstructed.

Image positions are unique per identification. Heatmap positions are unique per
image, and a composite FK requires the heatmap and its image to belong to the same
identification. Empty heatmap references are rejected. Null image references remain
supported for storage-disabled development.

Before upgrading an existing V25 database, back it up and run
`docs/database/preflight-v26.sql`. Every returned count must be zero. V26 deliberately
rejects inconsistent historical data instead of deleting it or inventing missing
plants/model versions. Resolve reported records before running the migration.

## Chat and audit persistence

A chat turn saves the session, user message, assistant message and source metadata
in one transaction after the remote call. Existing sessions are locked during the
write; timestamp ties use IDs for deterministic history ordering. Identification
history also uses a timestamp/ID ordering, and heatmaps use image/heatmap order.

Audit writes go through a separate `REQUIRES_NEW` writer. During account transactions
they run after completion: failed attempts survive rollback, and successful events
are stored only after commit (so new user FKs exist). Database audit failures are
logged and do not undo an already completed account operation.

## Scope exclusion
The old SIH supply-chain tables such as batches, batch events, QR, collector/trader/wholesaler/manufacturer workflows and blockchain are not part of the current mini-project database unless the team explicitly reintroduces them later.
