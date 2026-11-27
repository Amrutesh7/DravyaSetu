# Render and Neon deployment setup

Progress is tracked in `docs/DEPLOYMENT_CHECKLIST.md`. Keep unfinished tasks open
until their acceptance checks pass. Never place passwords, application keys or
connection strings containing passwords in this document.

## 1 Neon and existing local PostgreSQL data

1. Sign in to Neon and create a Free project named DravyaSetu. Choose PostgreSQL 16
   to match the existing database, and a region close to the Render backend.
2. Create an EMPTY database for the import, for example `dravya_setu`. Select a
   database role in Connect and copy its connection settings. Prefer the direct
   endpoint (connection pooling OFF) for this initial deployment, imports and Flyway.
3. The three backend credentials come from Neon, not from the local database:

   ```properties
   DB_URL=jdbc:postgresql://NEON_DIRECT_HOST/dravya_setu?sslmode=require
   DB_USERNAME=NEON_ROLE
   DB_PASSWORD=NEON_ROLE_PASSWORD
   ```

   Use the exact database name and TLS options shown by Neon. Passwords stay in
   `DB_PASSWORD`; don't embed them in the JDBC URL. Save target values in the
   ignored `.env.neon` file until migration is verified; preserve local `.env`
   database settings so the source remains available. Render gets the target
   values in its Environment form.
4. Stop local application writes during the final export. Check the source's
   Flyway version. If V25, run `docs/database/preflight-v26.sql`; all counts must be
   zero. If already V26, don't downgrade or replay old migrations.
5. Export with PostgreSQL 16 `pg_dump --format=custom --no-owner --no-acl` into
   an ignored `backups/` directory. Include all application data, sequences and
   `flyway_schema_history`. Do not copy PostgreSQL server users/roles.
6. Restore with `pg_restore --no-owner --no-acl --exit-on-error` into the EMPTY
   destination via Neon's direct endpoint with TLS. Never run this against the
   source. Do not start Spring against Neon before restoring, because Flyway would
   create tables that conflict with the import.
7. Compare table counts and identity sequences, then start Spring against Neon.
   Flyway should validate existing history and apply V26 if needed. Test sign-in,
   plant browsing, history and new inserts. Keep the local source and dump until
   the hosted copy is verified. A database copy does not copy B2 objects; retain
   the same bucket or separately migrate those objects and keys.

I can execute the export/restore after the Neon connection settings are saved
securely and the destination is confirmed empty. The source data is preserved.

Reference: [Neon PostgreSQL migration](https://neon.com/docs/import/migrate-from-postgres).

## 2 Random application secrets

Independent 48-byte random secrets were generated into `.env` and `backend/.env`;
both files are ignored and have owner-only permissions. They are not shown here.
Rotating them invalidates existing JWTs and previously issued OTPs after restart.

`render.yaml` uses `generateValue: true` for both secrets. Creating the Blueprint
therefore generates separate production secrets automatically. For a manually
created service, copy the local generated values using a local editor into Render
Environment, or use Render's generator; don't commit or paste them into chat.
Do not regenerate production secrets on each deployment.

The local rotation script is `scripts/configure-local-deployment-secrets.pl`.
It intentionally rotates keys when run; don't rerun it as a normal startup step.

## 3 Email on Render Free

Render Free blocks outbound ports 25, 465 and 587. Use Brevo's HTTPS API through
the new `MAIL_PROVIDER=brevo` sender. Local Mailpit/SMTP still uses `MAIL_PROVIDER=smtp`.

1. Create/sign in to a Brevo account and complete its account/transactional-sending
   activation requirements.
2. Go to Settings > Senders, Domains, IPs > Senders > Add a sender. Enter sender
   name `DravyaSetu` and an address you control. Verify the code sent to that inbox.
   With no domain yet, use an address Brevo approves; do not invent an address on
   the backend's onrender.com hostname. A custom authenticated domain improves
   deliverability later; inbox verification does not grant DNS control over Gmail.
3. Open SMTP & API > API Keys and create an API key for the backend. This provider
   requires an API key, NOT an SMTP key or your Brevo login password.
4. Set in Render Environment:

   ```properties
   MAIL_PROVIDER=brevo
   MAIL_ENABLED=true
   BREVO_API_KEY=YOUR_BREVO_API_KEY
   MAIL_FROM=YOUR_VERIFIED_SENDER_ADDRESS
   MAIL_FROM_NAME=DravyaSetu
   ```

   SMTP host/login/port/password are not used by the HTTPS sender. Existing HTML
   templates are sent directly; no Brevo template creation is needed.
5. Deploy and register a disposable test user using a real inbox. Confirm the code
   arrives, completes verification, and enables login. Repeat the password-reset
   flow. Check Brevo's Transactional Logs if delivery is rejected, queued or bounced.
   An HTTP acceptance alone does not prove arrival in the inbox.

References: [Render Free restrictions](https://render.com/docs/free),
[Brevo sender setup](https://help.brevo.com/hc/en-us/articles/208836149-Create-a-new-sender-From-name-and-From-email),
[Brevo send-email API](https://developers.brevo.com/docs/send-a-transactional-email).

## 4 Real AI and Knowledge services

Both ignored local files and the deployment configurations use real clients:

```properties
SPRING_PROFILES_ACTIVE=real-ai,real-knowledge,production
AI_BASE_URL=https://a760-st--dravyasetu-ai-fastapi-app.us-east.modal.run
AI_IDENTIFY_PATH=/ai/analyze
AI_IMAGE_TRANSPORT=multipart
KNOWLEDGE_BASE_URL=https://dravyasetu-knowledge-service.onrender.com
KNOWLEDGE_ASK_PATH=/knowledge/chat
```

The AI service now runs on Modal. On the existing Render **backend** service,
replace its saved `AI_BASE_URL` with the Modal base host above (without `/docs`),
keep `AI_IDENTIFY_PATH=/ai/analyze` and `AI_IMAGE_TRANSPORT=multipart`, and deploy
the updated backend code. Existing Render environment values override repository
defaults; changing `render.yaml` alone does not update a manually created service.
The Modal endpoint expects internal `images` file parts; the backend continues
accepting frontend `image` parts. No AI credential is declared by the public schema.

`production` adds deployment settings; the two real-client profiles select the
actual integrations. They don't silently fall back to mocks during an outage.
Identification returns a controlled 503 while AI is unavailable. Keep that
end-to-end acceptance task open until the AI service works. Voice input remains
deferred; do not build fake speech processing into the backend.

## 5 Render HTTPS and React or Flutter access

1. Make the repository available to your Render account. In Render choose New >
   Blueprint, select the repository/branch and its root `render.yaml`. The blueprint
   uses the backend Dockerfile, real services, Neon credentials, Brevo and B2.
   All changes must be in the branch Render actually builds; configuring files
   locally does not update the remote repository or create the service.
2. Supply every `sync: false` environment value in Render's secure form. Before
   Blueprint import, set its `region` to match your chosen Neon region; the file
   is now set to Singapore, matching the supplied Neon region.
   The PostgreSQL service in local Compose is not deployed on
   Render: Neon owns the hosted database.
3. Let Render build and deploy. The application binds to Render's `PORT`. The
   health check is `/api/v1/actuator/health` and includes database connectivity.
4. Copy the assigned `https://SERVICE.onrender.com` URL. Render provides managed
   TLS and HTTP-to-HTTPS redirection. No domain purchase, private TLS key or separate
   Nginx certificate is needed for this address. Do not run the VPS Nginx Compose
   stack inside a Render web service.
5. API base: `https://SERVICE.onrender.com/api/v1`; Swagger:
   `https://SERVICE.onrender.com/api/v1/swagger-ui/index.html`.
   Swagger declares the relative server `/api/v1`, so browser requests keep the
   documentation page's HTTPS origin. Render's automatically supplied
   `RENDER_EXTERNAL_URL` is also allowed as an exact CORS origin for Swagger's
   requests through the TLS-terminating proxy; no additional environment setting
   is needed. Custom backend domains must be listed in the CORS setting too.
6. The blueprint currently permits exactly `http://localhost:5173` and
   `http://localhost:3000`. Confirm the teammate's actual ports; add the eventual
   React/Flutter Web HTTPS origin to `CORS_ALLOWED_ORIGIN_PATTERNS`, with no path
   and no wildcard. Native Flutter has no browser CORS enforcement.
7. Keep automatic trust of forwarding headers disabled. Determine the real Render
   proxy peer addresses before setting `TRUSTED_PROXY_CIDRS`; don't trust
   `0.0.0.0/0`. Until then the limiter conservatively groups by socket peer, so
   multiple users behind Render's proxy can share a limit. Proxy attribution is
   an open deployment verification item, not a reason to accept spoofed headers.

References: [Render web services and TLS](https://render.com/docs/web-services),
[Render Blueprint reference](https://render.com/docs/blueprint-spec).

## 6 Backblaze B2 dashboard

Local B2 fields are already populated; confirm they refer to the intended bucket
before replacing anything. There is no need to create a second bucket just to
turn on the backend's storage feature.

1. Sign in to Backblaze > B2 Cloud Storage > Buckets. Choose the existing bucket
   or Create a Bucket. Keep it Private. Use a unique name for a new bucket.
2. Copy the S3 Endpoint from bucket details. Use `https://` plus the displayed
   endpoint as `B2_ENDPOINT`. `B2_REGION` is its region token (for example,
   `us-west-004` from `s3.us-west-004.backblazeb2.com`). Use your bucket's actual
   endpoint and region, not that example.
3. Go to Application Keys > Add a New Application Key. Name it for the backend,
   restrict it to this bucket, and choose Read and Write access with read/write/
   delete-file capabilities. Cleanup needs deletion. Leave filename-prefix empty
   unless it matches the application's object keys. Do not enable Object Lock or
   retention that prevents deleting temporary failed uploads unless you have
   deliberately designed that retention policy.
4. Save `keyID` and `applicationKey` securely when created; the application key is
   shown only once. Use a standard scoped application key for the S3 API, not
   the account master key. "Allow List All Bucket Names" is needed only by tools
   that call List Buckets; this application uses a configured bucket directly.
5. Set on Render:

   ```properties
   B2_STORAGE_ENABLED=true
   B2_BUCKET=YOUR_BUCKET_NAME
   B2_ENDPOINT=https://YOUR_S3_ENDPOINT
   B2_REGION=YOUR_BUCKET_REGION
   B2_APPLICATION_KEY_ID=YOUR_KEY_ID
   B2_APPLICATION_KEY=YOUR_APPLICATION_KEY
   B2_PATH_STYLE=true
   ```

6. For browser fetch/canvas access, open the bucket's CORS settings. Add exact web
   origins (initially the teammate's localhost origins) and allow S3 GET/HEAD
   operations (`s3_get`, `s3_head` in Native API rule format). Backend uploads do
   not require browser PUT permissions. Keep the bucket private. Add the deployed
   frontend origin when known; CORS does not replace signed-URL authorization.
7. Verify upload, signed-URL download and cleanup using a disposable object, then
   a real identification with stored images/heatmaps. AI unavailability currently
   prevents the final identification check even if standalone B2 access works.

References: [Buckets](https://www.backblaze.com/docs/cloud-storage-create-and-manage-buckets),
[Application keys](https://www.backblaze.com/docs/cloud-storage-application-keys),
[CORS](https://www.backblaze.com/docs/cloud-storage-cross-origin-resource-sharing-rules).
