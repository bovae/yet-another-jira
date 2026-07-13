# yet-another-jira

Kanban ticket tracker: React SPA + Spring Boot API + PostgreSQL, Valkey as ephemeral store.
Everything builds and runs in Docker — no host Java/Node/Postgres needed (host Node/Maven
still work for local dev loops).

## Repo map

- `be/` — Spring Boot 3.5 / Java 21, Maven (`./mvnw`)
- `fe/` — React 19 + TypeScript + Vite + Tailwind v4 (see `fe/CLAUDE.md` — mandatory for UI work)
- `openspec/` — spec-driven change workflow (proposals in `changes/`, truth in `specs/`)
- `requirements/` — product requirements, `epics-catalog.md`
- `docker-compose.yml` — full stack: postgres, valkey, be, fe, mailpit

## Validate (mirrors CI)

Use Makefile targets from repo root:

- `make be-lint` — Spotless format check + Error Prone + unit & BDD tests + 90% coverage gate.
  Needs Docker running (BDD uses Testcontainers).
- `make be-test` — backend unit + BDD + integration tests (`-Pbdd,it`), also needs Docker.
- `make fe-lint` — ESLint + Prettier check + typecheck (src and e2e tsconfigs).
- `make fe-test` — Vitest. E2E separately: `cd fe && npm run test:e2e` (Playwright).
- `make fmt` — auto-format both (Spotless apply + Prettier write). Run before committing.

## Run

- `make up` — docker compose full stack, health-gated startup order.
- Frontend http://localhost:8081, backend direct http://localhost:8080,
  API via nginx proxy at `/api`. Mailpit catches outgoing email.
- All env-specific config via `YAJ_*` env vars; compose provides local defaults (see README).

## Workflow

- Features flow through OpenSpec changes (`/opsx:propose` → apply → archive); don't code
  epic-sized work without a change in `openspec/changes/`.
- Conventional commits enforced by commitlint (`feat(be): ...`, `fix(fe): ...`).
