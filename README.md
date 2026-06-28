# yet-another-jira

A Kanban-style ticket tracker delivered as a three-tier application: a React SPA
(frontend), a Spring Boot HTTP API (backend), and a PostgreSQL database, with
Valkey as a supporting ephemeral store.

## Prerequisites

- **Docker** (with Docker Compose v2 plugin)

No host-installed Java, Node.js, or PostgreSQL runtime is required. The entire
stack builds and runs inside containers.

## Quick Start

From the repository root:

```bash
docker compose up --build
```

This single command builds all images and starts the full topology with
health-gated ordering: PostgreSQL and Valkey must be healthy before the backend
starts, and the backend must be healthy before the frontend starts.

### Access URLs

| Service          | URL                                    |
|------------------|----------------------------------------|
| Frontend (SPA)   | http://localhost:8081                   |
| Backend API      | http://localhost:8081/api/v1/mock/board |
| Actuator Health  | http://localhost:8080/actuator/health   |

> The frontend nginx proxies `/api` requests to the backend internally. Direct
> backend access on port 8080 is available from the host for debugging.

## Configuration

Every environment-specific value is externalized via environment variables.
Docker Compose injects sensible local-development defaults so a clean checkout
boots without a `.env` file. Override any variable on the host or via a `.env`
file at the repository root.

### Backend Environment Variables

| Variable                   | Purpose                              | Default in Compose                                           |
|----------------------------|--------------------------------------|--------------------------------------------------------------|
| `YAJ_DATASOURCE_URL`       | JDBC connection URL for PostgreSQL   | `jdbc:postgresql://postgres:5432/yaj?stringtype=unspecified` |
| `YAJ_DATASOURCE_USERNAME`  | Database username                    | `yaj`                                                        |
| `YAJ_DATASOURCE_PASSWORD`  | Database password                    | `yaj`                                                        |
| `YAJ_VALKEY_HOST`          | Hostname of the Valkey instance      | `valkey`                                                     |
| `YAJ_VALKEY_PORT`          | Port of the Valkey instance          | `6379`                                                       |
| `YAJ_CORS_ALLOWED_ORIGINS` | Comma-separated CORS allowed origins | `http://localhost:8081`                                      |

### PostgreSQL Service Variables

| Variable            | Purpose                        | Default in Compose |
|---------------------|--------------------------------|--------------------|
| `POSTGRES_DB`       | Database name created on init  | `yaj`              |
| `POSTGRES_USER`     | Superuser name                 | `yaj`              |
| `POSTGRES_PASSWORD` | Superuser password             | `yaj`              |

## Optional Services

### Mailpit (SMTP sink)

An email testing UI is available behind the `mail` Compose profile and is **not**
started by default:

```bash
docker compose --profile mail up --build
```

- Web UI: http://localhost:8025
- SMTP: localhost:1025

## Repository Layout

```
be/   — Backend (Spring Boot 3.5, Java 21, Maven)
fe/   — Frontend (Vite + React 19 + TypeScript)
```

## Developer Shortcuts

The root `Makefile` provides common commands. Run `make help` for a full list:

```
  be-lint      Run backend static analysis and formatting checks
  be-test      Run backend unit tests
  down         Stop the stack and remove containers
  fe-lint      Run frontend lint and format checks
  fe-test      Run frontend tests
  fmt          Auto-format backend and frontend sources
  up           Build and start the full stack (postgres + valkey + be + fe)
```

## Stopping the Stack

```bash
docker compose down
```

Add `-v` to also remove the persistent PostgreSQL volume:

```bash
docker compose down -v
```
