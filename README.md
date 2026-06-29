# yet-another-jira

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4?logo=tailwindcss&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Valkey](https://img.shields.io/badge/Valkey-Redis--compatible-FF4438?logo=redis&logoColor=white)

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

| Service         | URL                                     |
|-----------------|-----------------------------------------|
| Frontend (SPA)  | http://localhost:8081                   |
| Backend API     | http://localhost:8081/api/v1/mock/board |
| Actuator Health | http://localhost:8080/actuator/health   |

> The frontend nginx proxies `/api` requests to the backend internally. Direct
> backend access on port 8080 is available from the host for debugging.

## Configuration

Every environment-specific value is externalized via environment variables.
Docker Compose injects sensible local-development defaults so a clean checkout
boots without a `.env` file. Override any variable on the host or via a `.env`
file at the repository root.

### Backend Environment Variables

| Variable                              | Purpose                                       | Default in Compose                                           |
|---------------------------------------|-----------------------------------------------|--------------------------------------------------------------|
| `YAJ_DATASOURCE_URL`                  | JDBC connection URL for PostgreSQL            | `jdbc:postgresql://postgres:5432/yaj?stringtype=unspecified` |
| `YAJ_DATASOURCE_USERNAME`             | Database username                             | `yaj`                                                        |
| `YAJ_DATASOURCE_PASSWORD`             | Database password                             | `yaj`                                                        |
| `YAJ_VALKEY_HOST`                     | Hostname of the Valkey instance               | `valkey`                                                     |
| `YAJ_VALKEY_PORT`                     | Port of the Valkey instance                   | `6379`                                                       |
| `YAJ_CORS_ALLOWED_ORIGINS`            | Comma-separated CORS allowed origins          | `http://localhost:8081`                                      |
| `YAJ_SMTP_HOST`                       | SMTP server hostname                          | `mailpit`                                                    |
| `YAJ_SMTP_PORT`                       | SMTP server port                              | `1025`                                                       |
| `YAJ_SMTP_FROM`                       | Sender address for outgoing emails            | `no-reply@yet-another-jira.local`                            |
| `YAJ_SMTP_USERNAME`                   | SMTP auth username (empty for local dev)      | *(empty)*                                                    |
| `YAJ_SMTP_PASSWORD`                   | SMTP auth password (empty for local dev)      | *(empty)*                                                    |
| `YAJ_VERIFICATION_LINK_BASE_URL`      | Base URL for email verification links         | `http://localhost:8081/verify`                               |
| `YAJ_VERIFICATION_REDIRECT_URL`       | Redirect target after successful verification | `http://localhost:8081/login`                                |
| `YAJ_VERIFICATION_ERROR_REDIRECT_URL` | Redirect target after failed verification     | `http://localhost:8081/verify-error`                         |
| `YAJ_JWT_SECRET`                      | HMAC-SHA256 signing key for JWTs (≥ 32 chars) | `change-me-in-production-this-is-only-for-local-dev`         |

### PostgreSQL Service Variables

| Variable            | Purpose                       | Default in Compose |
|---------------------|-------------------------------|--------------------|
| `POSTGRES_DB`       | Database name created on init | `yaj`              |
| `POSTGRES_USER`     | Superuser name                | `yaj`              |
| `POSTGRES_PASSWORD` | Superuser password            | `yaj`              |

## Optional Services

### Mailpit (SMTP sink)

An email testing UI is available behind the `mail` Compose profile and is **not**
started by default. The backend is pre-wired to use mailpit for email delivery
(verification emails, etc.) when the `mail` profile is active.

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
