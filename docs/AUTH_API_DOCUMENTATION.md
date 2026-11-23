# Authentication API — Person 4 integration guide

Backend owner: Person 2. Frontend/mobile owner: Person 4.

This document is the complete contract for registration, email verification, login, logout and
password reset. It is written so the web and mobile clients can be built without reading the
Java sources.

- Base URL: `http://localhost:8080/api/v1` (all paths below are relative to it)
- Content type: `application/json` for every request body
- Authentication: **stateless JWT bearer token** in the `Authorization` header.
  There are no cookies and no server-side HTTP session, so CSRF does not apply to these
  endpoints. Do not send credentials with `credentials: 'include'`.
- Sender identity: `Authorization: Bearer <token>`

---

## 1. Endpoint summary

Deployment note: Render Free uses `MAIL_PROVIDER=brevo` with `BREVO_API_KEY` and a
verified `MAIL_FROM` over HTTPS because its normal SMTP ports are blocked. Local
Mailpit and SMTP providers use `MAIL_PROVIDER=smtp`. Public auth endpoints and
error responses are identical with either transport. Setup and verification are
tracked in `docs/DEPLOYMENT_SETUP.md` and `docs/DEPLOYMENT_CHECKLIST.md`.

| # | Method | Path | Auth | Purpose |
|---|---|---|---|---|
| 1 | `POST` | `/auth/register` | none | Store pending registration (hashed password) and email a 6-digit code — **no user is created yet** |
| 2 | `POST` | `/auth/verify-email` | none | Confirm the code and **create the account** (verified) |
| 3 | `POST` | `/auth/resend-verification` | none | Send a new verification code |
| 4 | `POST` | `/auth/login` | none | Exchange credentials for a bearer token |
| 5 | `POST` | `/auth/logout` | Bearer | Revoke the token used for the request |
| 6 | `GET` | `/auth/me` | Bearer | Current identity, role and verification state |
| 7 | `POST` | `/auth/forgot-password` | none | Email a reset code (always `202`) |
| 8 | `POST` | `/auth/verify-reset-otp` | none | Exchange a reset code for a reset ticket |
| 9 | `POST` | `/auth/reset-password` | none | Set the new password with the ticket |

`/auth/me` and `/auth/logout` are the only auth endpoints that require a token. Everything under
`/identifications`, `/chat`, `/plants` (writes) and `/admin` also requires one; see section 9.

---

## 2. Response conventions

### 2.1 Success

Auth endpoints return either `204 No Content`, `202 Accepted`, or a JSON object. There is no
envelope: the body is the object itself.

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

> `tokenType` and `expiresIn` are camelCase because the original login contract used camelCase.
> Newer response objects (`/auth/me`, `verify-reset-otp`) use snake_case. Both are shown
> explicitly in this document, so follow the examples rather than assuming a convention.

### 2.2 Errors

Every error uses the project-wide envelope and never contains passwords, OTPs or tokens:

```json
{
  "error": {
    "code": "OTP_INVALID",
    "message": "Invalid code. 4 attempt(s) remaining.",
    "request_id": "4e8fc06b-bcba-4197-8744-75ef1ede07e8"
  }
}
```

| HTTP | `error.code` | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Validation failure, malformed JSON, password mismatch, expired/used reset ticket |
| 400 | `OTP_INVALID` | Wrong code, unknown address, or no active code |
| 400 | `OTP_EXPIRED` | The code existed but its 5 minutes elapsed |
| 400 | `OTP_ATTEMPTS_EXCEEDED` | Five wrong submissions; the code is now burned |
| 401 | `INVALID_CREDENTIALS` | Unknown address or wrong password (identical response) |
| 401 | `AUTH_REQUIRED` | Missing, expired, revoked or unusable bearer token |
| 403 | `EMAIL_NOT_VERIFIED` | Correct password, but the address was never verified |
| 403 | `AUTH_FORBIDDEN` | Authenticated but not allowed (for example a `USER` calling `/admin/**`) |
| 409 | `CONFLICT` | Email already registered |
| 429 | `RATE_LIMITED` | Per-IP limit hit; the response includes `Retry-After` in seconds |
| 503 | `EMAIL_DELIVERY_FAILED` | SMTP is down or not configured |

Show `message` when it is helpful, but branch on `code`. Do not display `request_id` as an error;
log it for support.

---

## 3. Registration + email verification

### 3.1 Flow

```
[Register form]
      |  POST /auth/register  (201)
      v
[Code screen]  <-- 6-digit code arrives by email, valid 5 minutes
      |  POST /auth/verify-email  (204)
      v
[Login]  ->  POST /auth/login  (200 token)
```

**No user account is created at registration.** The email, BCrypt-hashed password and OTP are held
in a secure `pending_registrations` record. The actual user account is created **only after** a
correct OTP is submitted to `/auth/verify-email`. Until then login fails with `401
INVALID_CREDENTIALS` (there is no account). Registering an address that already has a **verified**
account returns `409 CONFLICT`. If the user closed the tab, they resume at the code screen and call
`/auth/resend-verification`; no re-registration is needed. Pending registrations and their OTPs
have codes that expire after 5 minutes. The pending record is retained so resend
can issue a fresh code while preserving the per-address hourly send limit.

### 3.2 `POST /auth/register`

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Validation: email must be a syntactically valid address (max 255), password 8–72 characters with
at least one letter and one digit. These are server-side rules; mirroring them in the form is
convenience only.

Response: `201 Created`, empty body.

```json
// 409
{"error":{"code":"CONFLICT","message":"Email already registered. Verify it or request a new code.","request_id":"..."}}
```

The 6-digit code is emailed as a professional HTML message and expires after 5 minutes. If SMTP
fails, the request returns `503 EMAIL_DELIVERY_FAILED` and **the account is rolled back**, so
retrying registration is safe and no half-created account exists.

### 3.3 `POST /auth/verify-email`

```json
{
  "email": "user@example.com",
  "otp": "042917"
}
```

Response: `204 No Content`. On success the user account is created with `email_verified = true`.

Rules the backend enforces:

- A valid OTP creates the account from the pending registration (email + hashed password).
- The code is bound to this address and to the verification purpose; a reset code cannot be used.
- Expiry is 5 minutes; expired codes cannot verify. Resend can renew the pending registration.
- Five wrong submissions burn the pending registration; the user must re-register / request a new code.
- A correct code consumes the pending registration immediately; repeat submissions are idempotent.
- Only one code per purpose can be active; requesting a new one retires the previous one.
- The endpoint is **idempotent**: if the account is already verified it returns `204` even if the
  submitted code is old or wrong. There is nothing left for the code to authorize.

Frontend behaviour:

- Disable the submit button while the request is in flight.
- On `OTP_INVALID` with `attempt(s) remaining`, keep the user on the screen.
- On `OTP_EXPIRED` or `OTP_ATTEMPTS_EXCEEDED`, show the resend action.
- After `204`, route to login (do not auto-login; there is no token from this endpoint).

### 3.4 `POST /auth/resend-verification`

```json
{ "email": "user@example.com" }
```

Response: `202 Accepted`, always, including for unknown or already-verified addresses.

Per-account rules: a resend inside 60 seconds is silently ignored; at most 5 codes per hour per
account. Per-IP, the gateway limiter allows 5 resend requests per 15 minutes. Because the
response cannot change, the UI should start a 60-second cooldown timer itself and show
"Code sent if the address needs verification".

---

## 4. Login and token handling

### 4.1 `POST /auth/login`

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Success `200`:

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Failure mapping:

| Case | Response | UI action |
|---|---|---|
| Wrong password or unknown address | `401 INVALID_CREDENTIALS` | Generic "Invalid email or password" |
| Correct password, unverified address | `403 EMAIL_NOT_VERIFIED` | Go to the code screen with the email prefilled and offer resend |

The unverified check happens **after** the password check, so an attacker who does not know the
password cannot learn whether an address exists. The two credential failures are deliberately
indistinguishable.

### 4.2 Sending the token

```
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
```

- Tokens are valid for **1 hour** (`expiresIn` seconds). There is no refresh token: when the token
  expires, call `/auth/login` again.
- Send the header on every protected request. On `401 AUTH_REQUIRED`, clear local state and return
  to login.
- Do not attempt to decode or trust claims client-side for authorization. Use `/auth/me` for the
  role and verification state, and still expect the server to enforce everything.
- Store the token according to your platform's threat model (in memory or `sessionStorage` for
  web). It is a bearer credential: XSS equals account takeover, so never interpolate it into HTML.
- There are no cookies, so `SameSite`, `HttpOnly` and CSRF tokens do not apply here. The API has
  `SameSite`-irrelevant stateless auth and CSRF disabled; keep it that way unless the architecture
  changes to cookie sessions.

### 4.3 `GET /auth/me`

```json
{
  "id": 1,
  "email": "user@example.com",
  "role": "USER",
  "email_verified": true,
  "created_at": "2026-11-18T15:12:56.167592Z"
}
```

Use this after login to decide between the user area and the admin area.

### 4.4 `POST /auth/logout`

No body. Send the bearer token. Response: `204 No Content`.

The presented token is added to a revocation list until its natural expiry, so it stops working
immediately. Other devices stay signed in. A password change or reset signs **everything** out.

---

## 5. Forgot password (three steps)

```
[Email form]
   | POST /auth/forgot-password (202)
   v
[Code screen]  <-- reset code by email, valid 5 minutes
   | POST /auth/verify-reset-otp (200 -> reset_token, expires in 10 minutes)
   v
[New password form]
   | POST /auth/reset-password (204)
   v
[Login]  (all previous sessions already invalidated)
```

### 5.1 Step 1 — `POST /auth/forgot-password`

```json
{ "email": "user@example.com" }
```

Response: `202 Accepted`, empty body, **always** — unknown address, verified address, cooling-down
address, all identical. This prevents account enumeration. Do not change the UI copy based on the
response; say "If an account exists, a code has been sent."

Delivery failures are swallowed on purpose: surfacing them would leak which addresses exist. If
the mail provider is down the code simply never arrives, and the user can retry after the
cooldown.

### 5.2 Step 2 — `POST /auth/verify-reset-otp`

```json
{
  "email": "user@example.com",
  "otp": "918273"
}
```

Success `200`:

```json
{
  "reset_token": "3f0_2Q8BNyFwJITuUlvG7nk_GA8x2To9Gq-O39y8I3E",
  "expires_at": "2026-11-18T20:52:56.808818266+05:30"
}
```

The reset code is consumed here. `reset_token` is a random, single-use ticket valid for 10
minutes; it replaces the code rather than extending its lifetime.

Same failure codes as email verification: `OTP_INVALID`, `OTP_EXPIRED`,
`OTP_ATTEMPTS_EXCEEDED`. An unknown address returns `OTP_INVALID` in comparable time, so step 2
does not reveal registration either.

### 5.3 Step 3 — `POST /auth/reset-password`

```json
{
  "reset_token": "3f0_2Q8BNyFwJITuUlvG7nk_GA8x2To9Gq-O39y8I3E",
  "password": "newpassword456",
  "confirm_password": "newpassword456"
}
```

Response: `204 No Content`.

Rules:

- Passwords must match (`400 INVALID_REQUEST`, "Passwords do not match").
- Same strength policy as registration.
- The existing user row is updated; **no new account is created**.
- The ticket is single use and expires after 10 minutes.
- The OTP used to obtain the ticket is already spent; it cannot mint a second ticket.
- `token_version` is incremented, so every token issued before the reset stops working instantly.
- A confirmation email is sent. A mail failure does not undo the reset.
- An unverified account that resets its password **remains unverified**; it still needs the
  registration code to log in. Do not skip the verification screen based on a reset success.

After `204`, show "Password updated. Sign in with your new password." and route to login. Old
passwords fail with `401 INVALID_CREDENTIALS`; any tab still holding a pre-reset token receives
`401 AUTH_REQUIRED`.

---

## 6. Timing, limits and anti-abuse rules

| Rule | Value | Configurable via |
|---|---|---|
| Verification/reset code length | 6 digits, `SecureRandom` | `OTP_LENGTH` |
| Code lifetime | 5 minutes | `OTP_TTL_MINUTES` |
| Wrong attempts per code | 5, then burned | `OTP_MAX_ATTEMPTS` |
| Resend cooldown per account | 60 seconds | `OTP_RESEND_COOLDOWN_SECONDS` |
| Codes per account per hour | 5 | `OTP_MAX_PER_HOUR` |
| Reset ticket lifetime | 10 minutes | `PASSWORD_RESET_TICKET_TTL_MINUTES` |
| Access token lifetime | 1 hour | `JWT_EXPIRATION_MS` |

Per-IP gateway limits (`RATE_LIMIT_ENABLED`, default on):

| Endpoint | Limit |
|---|---|
| `/auth/register` | 5 / 15 min |
| `/auth/login` | 5 / 15 min |
| `/auth/forgot-password` | 5 / 15 min |
| `/auth/resend-verification` | 5 / 15 min |
| `/auth/verify-email`, `/auth/verify-reset-otp` | 20 / 15 min |
| `/auth/reset-password` | 10 / 15 min |
| `/auth/logout` | 30 / 15 min |

`429` responses include `Retry-After`. A single-node in-memory limiter is used; it resets on
restart and is not shared between instances. This is intentional for the current deployment and
documented as a production follow-up.

---

## 7. Protected routes and authorization

`SecurityConfig` is deny-by-default:

```text
permitAll: POST /auth/register, /auth/login, /auth/forgot-password, /auth/reset-password,
           /auth/verify-email, /auth/resend-verification, /auth/verify-reset-otp,
           GET /health, /languages, /actuator/health, /actuator/info,
           swagger-ui/**, v3/api-docs/**, webjars/**
everything else: authenticated
```

Semantics enforced in the JWT filter on every request:

1. Signature and expiry are valid.
2. `jti` is not in `revoked_tokens` (logout).
3. The account still exists.
4. `email_verified = true`.
5. The token's `tv` equals `users.token_version` (password changes bump it).

Roles: `USER` (default) and `ADMIN`. `GET /admin/test` requires `ROLE_ADMIN`; a `USER` receives
`403 AUTH_FORBIDDEN`. Ownership is checked per resource as well: identification records and chat
sessions are scoped to the authenticated user, and another user's id returns `404`.

Never rely on hidden buttons or client-side route guards. Every rule above is enforced by the
server; the UI mirror is only for a better experience.

---

## 8. Environment variables

The application starts with the existing DB/JWT variables. Mail is required for registration and
reset to work.

### Required

| Variable | Example | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dravya_setu` | Existing |
| `DB_USERNAME` | `dravya_app` | Existing |
| `DB_PASSWORD` | *(secret)* | Existing |
| `JWT_SECRET` | 32+ random chars | Existing; used to sign tokens |
| `MAIL_HOST` | `smtp.eu.mailgun.org` | SMTP host |
| `MAIL_FROM` | `no-reply@dravyasetu.example` | Verified sender address |

### Mail

| Variable | Default | Notes |
|---|---|---|
| `MAIL_PORT` | `587` | 465 for implicit TLS |
| `MAIL_USERNAME` | *(empty)* | Enables SMTP AUTH when set |
| `MAIL_PASSWORD` | *(empty)* | App password / API key |
| `MAIL_FROM_NAME` | `DravyaSetu` | Display name |
| `MAIL_STARTTLS` | `true` | STARTTLS on 587 |
| `MAIL_SSL` | `false` | Implicit TLS on 465 |
| `MAIL_ENABLED` | `true` | Set false only to hard-fail sends deliberately |
| `MAIL_CONNECT_TIMEOUT_MS` | `10000` | |
| `MAIL_WRITE_TIMEOUT_MS` | `10000` | |

If `MAIL_HOST` is blank the application still boots, but registration returns
`503 EMAIL_DELIVERY_FAILED` rather than pretending to have sent a code.

### Auth tuning (optional)

| Variable | Default |
|---|---|
| `JWT_EXPIRATION_MS` | `3600000` |
| `OTP_HASH_SECRET` | falls back to `JWT_SECRET`; set a separate 32+ char value in production |
| `OTP_LENGTH` / `OTP_TTL_MINUTES` / `OTP_MAX_ATTEMPTS` | `6` / `5` / `5` |
| `OTP_RESEND_COOLDOWN_SECONDS` / `OTP_MAX_PER_HOUR` | `60` / `5` |
| `PASSWORD_RESET_TICKET_TTL_MINUTES` | `10` |
| `RATE_LIMIT_ENABLED` | `true` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:*,http://127.0.0.1:*` |

`PASSWORD_RESET_TOKEN_TTL_MINUTES` from the previous token-based flow is no longer read; delete it
from existing `.env` files.

### Provider examples

| Provider | Host / port | Notes |
|---|---|---|
| Mailpit (local, in `docker-compose.yml`) | `localhost` / `1025`, no auth | Web UI on `http://localhost:8025` |
| Mailtrap sandbox | `sandbox.smtp.mailtrap.io` / `2525` | Username + password from the inbox |
| Brevo | `smtp-relay.brevo.com` / `587` | SMTP key as password |
| Amazon SES | `email-smtp.<region>.amazonaws.com` / `587` | SMTP credentials, verify the domain |
| Gmail (dev only) | `smtp.gmail.com` / `587` | App password, not the account password |

For local development, start Mailpit and set `MAIL_HOST=localhost`, `MAIL_PORT=1025`,
`MAIL_USERNAME=`, `MAIL_PASSWORD=`, `MAIL_STARTTLS=false`, `MAIL_FROM=no-reply@dravyasetu.local`.
Every message is visible at `http://localhost:8025`; no real inbox is needed.

---

## 9. cURL walkthrough

Set these first:

```bash
BASE=http://localhost:8080/api/v1
EMAIL=you@example.com
PASSWORD=password123
```

Register:

```bash
curl -i -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
# 201, then read the code from your inbox / Mailpit
```

Verify:

```bash
curl -i -X POST "$BASE/auth/verify-email" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"042917\"}"
# 204
```

Login and capture the token:

```bash
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN"
```

Protected resource, then logout:

```bash
curl -i "$BASE/identifications" -H "Authorization: Bearer $TOKEN"
curl -i -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOKEN"
curl -i "$BASE/auth/me" -H "Authorization: Bearer $TOKEN"   # 401 AUTH_REQUIRED
```

Password reset:

```bash
curl -i -X POST "$BASE/auth/forgot-password" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}"

curl -s -X POST "$BASE/auth/verify-reset-otp" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"918273\"}"
# {"reset_token":"...","expires_at":"..."}

curl -i -X POST "$BASE/auth/reset-password" \
  -H 'Content-Type: application/json' \
  -d '{"reset_token":"...","password":"newpassword456","confirm_password":"newpassword456"}'
```

Negative checks worth trying by hand: register twice (`409`), log in before verifying (`403`),
submit a wrong code (`400`), submit it six times (`OTP_ATTEMPTS_EXCEEDED`), reuse a reset ticket
(`400`), call `/auth/me` with the pre-reset token (`401`).

### Postman

Create an environment with `baseUrl = http://localhost:8080/api/v1`, `email`, `password`, `token`.

1. `Register` → `POST {{baseUrl}}/auth/register`
2. `Verify email` → `POST {{baseUrl}}/auth/verify-email` (paste the code from Mailpit)
3. `Login` → `POST {{baseUrl}}/auth/login`; in **Tests**:
   `pm.environment.set("token", pm.response.json().token);`
4. `Me` → `GET {{baseUrl}}/auth/me` with `Authorization: Bearer {{token}}`
5. `Forgot password` → `POST {{baseUrl}}/auth/forgot-password`
6. `Verify reset OTP` → `POST {{baseUrl}}/auth/verify-reset-otp`; store `reset_token`
7. `Reset password` → `POST {{baseUrl}}/auth/reset-password`
8. `Logout` → `POST {{baseUrl}}/auth/logout`

---

## 10. UI checklist

- Registration: inline email/password validation, disabled submit + spinner while in flight,
  server error text mapped from `code`, never from a guessed status.
- Verification screen: six-digit numeric input, paste support, "Resend code" with a 60-second
  countdown, clear copy that the code expires in 5 minutes.
- Login: generic credential error, and a distinct branch for `403 EMAIL_NOT_VERIFIED` that routes
  to the verification screen with the email prefilled.
- Forgot password: identical success message for every address.
- Reset: password + confirmation, client-side mismatch hint **and** server rejection handling.
- Session: keep `expiresIn`, treat any `401 AUTH_REQUIRED` as "signed out", and clear the token on
  logout even if the request fails.
- Loading states must be driven by the request, not by timers; repeated submissions must be
  blocked because the backend rate-limits aggressively.

---

## 11. How this was tested

```bash
cd backend
mvn test                                  # unit + controller tests
mvn test -Ddocker.api.version=1.44        # also runs Testcontainers integration tests on Docker 29+
```

- `AuthFlowIntegrationTest` drives the entire sequence against a real PostgreSQL schema
  (Flyway V1–V22) and the real security filter chain, with the SMTP sender replaced by a capturing
  fake. It covers: register, duplicate, unverified login, wrong code, expiry semantics, verification,
  login, `/auth/me`, anonymous 401, RBAC 403, forgot-password (known and unknown), reset-code
  misuse and reuse, password mismatch, successful reset, old token rejection, old password
  rejection, new password login and logout revocation.
- `OtpServiceTest` proves single use, expiry, the five-attempt burn, resend cooldown, hourly cap
  and one-active-code enforcement.
- `SmtpEmailSender` construction and failure handling, and the HTML templates (code + expiry
  present, no unrendered placeholders) are unit-tested.
- Real SMTP delivery was additionally verified against a local SMTP sink: the message arrives as
  `text/html` from the configured sender, and the code from the delivered body completes
  registration. No production provider credentials were available, so a real inbox delivery is the
  one step that must be confirmed in your environment once `MAIL_*` is set.

---

## 12. Known limitations and follow-ups

1. **No refresh token.** A 1-hour access token is re-acquired by logging in. Acceptable for this
   project; refresh rotation is the next step if silent renewal is required.
2. **In-memory rate limiter.** Per-IP counters are per instance and reset on restart. Move to
   Redis or the gateway for multi-instance deployments.
3. **Registration reveals existing emails** with `409`, because the brief requires an explicit
   duplicate error. Login and both reset steps do not reveal existence.
4. **Reset does not verify an account.** A user who never verified still cannot log in after a
   reset; they must complete the registration code. This is deliberate and documented in section 5.
5. **Email templates are HTML-only.** A `text/plain` alternative part would improve deliverability
   with strict providers.
6. The stored OTP digest uses HMAC-SHA-256 keyed by `OTP_HASH_SECRET` (or `JWT_SECRET`). Set a
   separate value in production so rotating the JWT secret does not invalidate outstanding codes
   (it would only force a resend, never bypass verification).
