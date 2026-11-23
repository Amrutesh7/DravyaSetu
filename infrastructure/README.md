# Optional self-hosted infrastructure

Render deployment uses the root [Blueprint](../render.yaml), managed HTTPS,
Neon and Brevo. Follow the [deployment guide](../docs/DEPLOYMENT_SETUP.md) and
[open checklist](../docs/DEPLOYMENT_CHECKLIST.md); the Nginx stack is optional.

For a self-hosted Docker setup, `docker/docker-compose.yml` runs Nginx,
the Spring Boot backend and PostgreSQL. Supply private environment values from
your ignored root `.env`; required settings are described in the
[environment template](../.env.example).

```sh
docker compose --env-file .env -f infrastructure/docker/docker-compose.yml up --build -d
docker compose --env-file .env -f infrastructure/docker/docker-compose.yml logs -f backend
docker compose --env-file .env -f infrastructure/docker/docker-compose.yml down
```

Run these commands from the repository root. The public API is
`http://localhost/api/v1`; `/health` proxies the backend's basic health route.
PostgreSQL and the backend stay on the Compose network. The bundled Nginx
configuration listens on HTTP; HTTPS certificates/listeners need separate setup
for self-hosting. Publishing port 443 alone does not configure TLS.

Nginx allows a 50 MiB total request and a 360-second upstream wait. It discards
incoming forwarding claims and sets the client's socket address. Compose fixes
Nginx at `172.30.77.2`, and the backend trusts only that `/32` for forwarded IPs.
The network uses `172.30.77.0/24`, with dynamic allocations in `172.30.77.128/25`.
Change these together if the subnet conflicts with another local network.

For development without Nginx, use the root Compose file or
[run Maven directly](../backend/README.md). Preserve database volumes and recovery
dumps when stopping containers.
