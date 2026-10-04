# DravyaSetu Infrastructure

This directory contains infrastructure-as-code for deploying DravyaSetu.

## Structure

```
infrastructure/
├── docker/
│   └── docker-compose.yml    # Full stack: nginx + backend + postgres
└── nginx/
    └── nginx.conf            # Reverse proxy configuration
```

## Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Environment Variables
Create a `.env` file in `infrastructure/docker/`:
```bash
DB_PASSWORD=your-secure-password
JWT_SECRET=your-32-char-min-secret
AI_BASE_URL=http://ai-service:8001
KNOWLEDGE_BASE_URL=http://knowledge-service:8002
```

### Start Full Stack
```bash
cd infrastructure/docker
docker-compose up --build -d
```

### Services
| Service | Port | Description |
|---------|------|-------------|
| nginx | 80, 443 | Reverse proxy / SSL termination |
| backend | 8080 | Spring Boot API (proxied via nginx) |
| postgres | 5432 | PostgreSQL database |

### Health Checks
```bash
# Via nginx
curl http://localhost/health

# Direct backend (if exposed)
curl http://localhost:8080/api/v1/health
```

### Logs
```bash
docker-compose logs -f backend
docker-compose logs -f nginx
```

### Stop
```bash
docker-compose down
```

### Clean Volumes (⚠️ deletes DB data)
```bash
docker-compose down -v
```

## Development

For local development without nginx:
```bash
cd ../../backend
./mvnw spring-boot:run
```

## Production Notes

1. **SSL/TLS**: Add certificates to nginx for HTTPS
2. **Secrets**: Use Docker secrets or external secret manager
3. **Scaling**: Backend can be scaled behind nginx
4. **Backup**: Configure pg_dump cron for postgres volume