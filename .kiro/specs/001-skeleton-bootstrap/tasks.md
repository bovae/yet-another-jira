# Implementation Plan: 001-skeleton-bootstrap (yet-another-jira Application Skeleton)

## Overview

This plan builds the runnable three-tier skeleton incrementally: repository
scaffolding first, then the backend build tooling and quality gates, then the
backend bootstrap/config beans, the cross-cutting web layer (correlation-id, MDC
hygiene, RFC 9457 problem details, mock board), the full Liquibase schema, the
Cucumber BDD suite, the React frontend, containerization for one-command boot,
and finally CI/release/dependency tooling plus the README.

Each task builds on the previous ones and ends with wiring everything together
through `docker compose up --build`. Property-based testing is excluded by stack
mandate; the design's Correctness Properties are validated through JUnit unit
tests and the Cucumber BDD suite. Test sub-tasks reference the property they
validate and are marked optional with `*`.

Implementation languages (from design): Java 21 / Spring Boot 3.5 (backend),
TypeScript / React 19 (frontend).

## Tasks

- [x] 1. Repository scaffolding and shared root tooling
  - [x] 1.1 Create root project skeleton files
    - Create `.gitignore`, root `.dockerignore`, `Makefile` (up, down, be-test, be-lint, fmt, fe-test, fe-lint targets), and a placeholder `README.md` to be completed later
    - Create the `be/` and `fe/` module directory structure per the repository layout
    - _Requirements: 1.2, 17.1_

- [x] 2. Backend build tooling, thin-jar profiles, and quality gates
  - [x] 2.1 Create the Maven project, wrapper, and profile structure
    - Add `be/pom.xml` with mandatory runtime deps (spring-boot-starter-web, data-jpa, actuator, validation, spring-data-redis, postgresql driver, liquibase-core) and `provided`/annotation-scope Lombok + MapStruct
    - Define profiles: `errorprone` (active by default), `dev` (opt-in devtools), `it`/`testcontainers` (test scope), `bdd` (binds `src/bdd/java` source set + Cucumber runner); add `lombok.config`, Maven wrapper (`mvnw`, `.mvn/`)
    - _Requirements: 11.1, 11.2, 11.3_
  - [x] 2.2 Enforce thin-jar packaging
    - Configure the build to fail if compiled Lombok, MapStruct, Testcontainers, Cucumber, or devtools classes land in the packaged jar, with an error naming the offending dependency
    - _Requirements: 11.4_
    - _Validates Property 6 (Req 11.4)_
  - [x] 2.3 Configure Spotless formatting gate
    - Add Spotless (palantir-java-format) over `.java`, `.feature`, and `pom.xml` with importOrder, removeUnusedImports, forbidWildcardImports, sortPom; wire `spotless:check` (CI) and `spotless:apply` (local)
    - _Requirements: 12.1, 12.2_
  - [x] 2.4 Configure Error Prone + NullAway static analysis
    - Wire Error Prone with NullAway as a bug-checker on `maven-compiler-plugin` with the required `jdk.compiler` `--add-exports/--add-opens` args and `-XepOpt:NullAway:AnnotatedPackages=com.bovae.yaj` (non-null by default); make it skippable via `-Derrorprone.skip`
    - _Requirements: 12.3, 12.4_
  - [x] 2.5 Configure JaCoCo merge and 90/90 coverage gate
    - Merge `jacoco-unit.exec` and `jacoco-bdd.exec`, then run `check` with 90% line and 90% branch thresholds over non-excluded packages; exclude `**/*Application.*`, `**/config/**`, generated mappers, and `**/model/**`
    - _Requirements: 12.5, 12.6_
    - _Validates Property 8 (Req 12.5, 12.6)_

- [x] 3. Backend application bootstrap and configuration beans
  - [x] 3.1 Create the application entrypoint and externalized configuration
    - Add `YetAnotherJiraApplication` (`@SpringBootApplication`) and `application.yml` resolving datasource URL/username/password, Valkey host/port, and CORS origins from environment variables with no literal secrets; set the log pattern to include `[%X{correlationId}]`; enable Actuator health/readiness/liveness groups and `/actuator/info`
    - Fail startup with a clear error when a required environment-specific value is absent or empty (no hard-coded fallback)
    - _Requirements: 3.7, 10.4, 16.1, 16.2, 16.4, 17.4_
  - [x] 3.2 Add typed configuration properties
    - Create `config/properties/CorsProperties` (`yaj.cors.*`) and `config/properties/ValkeyProperties` (`yaj.valkey.*`) as `@ConfigurationProperties` records
    - _Requirements: 6.4, 10.1, 16.1_
  - [x] 3.3 Implement CORS configuration source
    - Add `CorsConfig` building a `CorsConfigurationSource` from `CorsProperties`, exposing `X-Correlation-Id` as a response header; fail startup naming the missing property if origins/methods/headers are absent or empty
    - _Requirements: 6.4, 6.5, 6.6_
  - [x] 3.4 Implement permit-all SecurityConfig seam
    - Add `SecurityConfig` wiring CORS from the source bean, disabling CSRF, setting stateless session management, and permitting all requests (`// SKELETON ONLY`); emit a startup WARN that permit-all is skeleton-only and must be replaced
    - _Requirements: 6.1, 6.2, 6.3, 6.7_
  - [x] 3.5 Wire Valkey connection and fail-fast readiness
    - Add `ValkeyConfig` (`LettuceConnectionFactory` + `StringRedisTemplate`) from `ValkeyProperties`; perform a startup readiness ping that terminates startup with a non-zero exit and a clear error when host/port is missing/malformed or Valkey is unreachable within 5 seconds; surface Valkey connectivity in `/actuator/health`
    - _Requirements: 10.1, 10.2, 10.3, 10.5, 10.6, 2.5, 2.6_

- [x] 4. Cross-cutting web layer (correlation-id, MDC hygiene, problem details, mock board)
  - [x] 4.1 Add correlation-id support constants
    - Create `support/CorrelationId` (or `web/filter` constants) for the `X-Correlation-Id` header name and `correlationId` MDC key
    - _Requirements: 3.1_
  - [x] 4.2 Implement MdcCleanupFilter
    - Add `MdcCleanupFilter` (`OncePerRequestFilter`, `HIGHEST_PRECEDENCE`) that calls `MDC.clear()` in a `finally` so all keys are wiped after the chain returns, success or exception
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [x] 4.3 Write unit tests for MdcCleanupFilter
    - Assert MDC is empty after the chain completes even when a downstream filter set a key, and on the exception path; assert ordering is `HIGHEST_PRECEDENCE`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
    - _Validates Property 2 (Req 4.1, 4.2, 4.3, 4.4)_
  - [x] 4.4 Implement CorrelationIdFilter
    - Add `CorrelationIdFilter` (`HIGHEST_PRECEDENCE + 1`): use a non-blank trimmed header of 1–128 chars, else generate a UUID; discard and regenerate when the trimmed value exceeds 128 chars; put it in MDC and echo `X-Correlation-Id` on the response including error paths
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_
  - [x] 4.5 Write unit tests for CorrelationIdFilter
    - Cover header passthrough, generation on absent/blank, regeneration when over 128 chars, MDC populated, and response header present; use `@ParameterizedTest` for same-shape header cases
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_
    - _Validates Property 1 (Req 3.1, 3.2, 3.3, 3.6)_
  - [x] 4.6 Implement RFC 9457 problem details handler
    - Add `ProblemDetailFactory` (sets `status`, `title`, `correlationId` from MDC, UTC ISO-8601 `timestamp`) and `GlobalProblemHandler` (`@RestControllerAdvice`) returning `application/problem+json` for 4xx/5xx including a generic 500 with no leaked internals, a 404 for unknown routes, and a 400 validation title
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 2.3_
  - [x] 4.7 Write unit tests for ProblemDetailFactory and GlobalProblemHandler
    - Assert `correlationId` and UTC `timestamp` populated, generic 500 leaks no internals, content type is `application/problem+json`, and required members present
    - _Requirements: 5.1, 5.2, 5.3_
    - _Validates Property 3 (Req 5.1, 5.2, 5.3)_
  - [x] 4.8 Implement the mock board endpoint
    - Add `BoardView`/`BoardColumn`/`BoardCard` and `MockBoardController` (`GET /api/v1/mock/board`) returning exactly five columns ordered `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`, with hardcoded cards (each title 1–200 chars, type one of `bug`/`feature`/`fix`), reading no Application_Table
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 2.2_
  - [x] 4.9 Write unit tests for MockBoardController
    - Assert exactly five columns in canonical workflow order, at least one card present, and card title/type constraints
    - _Requirements: 8.1, 8.2, 8.3, 8.5_
    - _Validates Property 7 (Req 8.1, 8.2)_

- [x] 5. Database schema migrations
  - [x] 5.1 Author the full Liquibase domain schema (zero seed data)
    - Create `db.changelog-master.yaml` referencing external `.sql` files via `sqlFile`: `0001-extensions` (citext, pgcrypto), `0002-enums` (`ticket_type` = bug/feature/fix, `ticket_state` = the five canonical states), `0003-users`, `0004-verification-tokens`, `0005-teams`, `0006-epics`, `0007-tickets`, `0008-comments`
    - Enforce case-insensitive uniqueness on `users.email` and `teams.name`; cascade delete `tickets → comments`; restricting FKs `tickets → teams` and `tickets → epics`; author `bovae`; configure `spring.liquibase.change-log`; halt startup preserving prior schema state on changelog failure
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 2.4_

- [x] 6. Checkpoint - backend unit suite and migrations green
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Backend BDD suite (Cucumber + Testcontainers)
  - [x] 7.1 Create the Cucumber runner and Testcontainers wiring
    - Add the `src/bdd/java` runner under the `bdd` profile with Testcontainers-provisioned Postgres and Valkey so the full boot path (migrations, filters, problem handler) is exercised
    - _Requirements: 13.2_
  - [x] 7.2 Author the skeleton feature and step glue
    - Write `@skeleton` scenarios and glue asserting: mock board returns five columns in workflow order; correlation-id passthrough vs generation; unknown route returns `application/problem+json` with `status`/`title`/`correlationId`/`timestamp`; freshly migrated DB has the five tables existing with zero rows
    - _Requirements: 13.3, 13.4, 13.5, 13.6_
    - _Validates Property 1 (Req 13.4), Property 3 (Req 13.5), Property 4 (Req 13.6), Property 7 (Req 13.3)_

- [x] 8. Frontend module (Vite + React 19 + TypeScript)
  - [x] 8.1 Scaffold the frontend project and tooling
    - Initialize Vite + React 19 + TS with `package.json`, `tsconfig.json`, `vite.config.ts` (dev proxy `/api` → backend), ESLint flat config, Prettier, TanStack Query, @dnd-kit (inert), and the `getdesign/vercel` design system
    - _Requirements: 12.7, 12.8_
  - [x] 8.2 Implement the typed API client and board fetch
    - Add `api/client.ts` injecting a non-empty `X-Correlation-Id` (≤128 chars) and enforcing a 10-second request timeout, and `api/board.ts` `getMockBoard()` calling `GET /api/v1/mock/board`
    - _Requirements: 9.1, 9.6, 2.7_
  - [x] 8.3 Implement the BoardPage with loading/success/error states
    - Add `pages/BoardPage.tsx` plus `components/Column.tsx` and `TicketCard.tsx`: show a loading indicator while in flight (no columns/error), render exactly five columns in response order on success replacing the loader, and show an error indicator (no columns) on network error, non-success, or timeout
    - _Requirements: 9.2, 9.3, 9.4, 9.5_
  - [x] 8.4 Write the BoardPage component test
    - Vitest + Testing Library: assert loading indicator in flight, five rendered columns on success, error indicator on rejected fetch
    - _Requirements: 13.7_
    - _Validates Property 7 (Req 13.7)_
  - [x] 8.5 Write the Playwright smoke test
    - Load the SPA against the composed stack and assert five board columns are visible
    - _Requirements: 13.8_
    - _Validates Property 5 (Req 13.8)_

- [x] 9. Containerization and one-command boot
  - [x] 9.1 Create the backend Dockerfile
    - Multi-stage `be/Dockerfile` (`amazoncorretto:21` build → thin jar via `./mvnw -Pdev,errorprone clean package`, runtime stage runs the jar); add `be/.dockerignore`
    - _Requirements: 1.2_
  - [x] 9.2 Create the frontend Dockerfile and nginx config
    - Multi-stage `fe/Dockerfile` (`node:22` build → `nginx:alpine` serving `dist/`) with `nginx.conf` providing SPA fallback and `/api` → backend proxy; add `fe/.dockerignore`; serve the SPA entry document at the published port with status 200
    - _Requirements: 1.2, 1.3_
  - [x] 9.3 Author docker-compose for full-topology boot
    - Wire `postgres`, `valkey`, `be`, `fe` with healthchecks and `depends_on: condition: service_healthy` (backend blocks on Postgres+Valkey, frontend blocks on backend), inject externalized env (datasource, `YAJ_VALKEY_HOST`, `YAJ_CORS_ALLOWED_ORIGINS`), enforce three-tier access (FE → BE only, BE sole DB accessor), keep email out of the default boot, and add `mailpit` behind the `mail` profile
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 16.3_

- [x] 10. Checkpoint - one-command boot renders the board
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. CI, release, and dependency tooling
  - [x] 11.1 Author the CI workflow
    - Add `.github/workflows/ci.yml` with parallel, non-duplicating jobs (be-build, be-lint, be-test with JaCoCo merge + 90/90, fe-build, fe-lint, fe-test) on push/PR to `develop`/`main`; publish backend and frontend test reports and upload the coverage report as an artifact retained ≥30 days; fail the run if any job fails
    - _Requirements: 14.1, 14.2, 14.5, 14.6, 14.7_
  - [x] 11.2 Configure semantic-release for both modules
    - Add `.github/workflows/release.yml` (push to `develop`/`main` plus manual `workflow_dispatch`), `be/.releaserc.json` (namespaced tag, `mvn versions:set`, git assets `pom.xml`+`CHANGELOG.md`) and `fe/.releaserc.json` (npm equivalents) so each module versions and tags independently without colliding; configure `main` as the stable release branch and `develop` as a `dev`-channel prerelease branch for both modules
    - _Requirements: 15.1, 15.2, 15.6, 15.7_
  - [x] 11.3 Configure Dependabot
    - Add `.github/dependabot.yml` for maven `/be`, npm `/fe`, and github-actions `/`, each weekly on a fixed day targeting `develop` with ≤5 open PRs
    - _Requirements: 15.3_
  - [x] 11.4 Configure pre-commit and commit-message linting
    - Add `.pre-commit-config.yaml` (check-merge-conflict, mixed-line-ending lf, trailing-whitespace, local spotless-apply, fe eslint --fix, fe prettier --write) that aborts the commit and preserves staged changes on unfixable violations; add commitlint config and a CI commitlint job validating Conventional Commits
    - _Requirements: 15.4, 15.5, 14.3, 14.4_

- [x] 12. Finalize project documentation
  - [x] 12.1 Complete the README
    - Document host prerequisites (Docker Compose only, no host FE/BE/DB runtime), the single `docker compose up --build` command, and every externalized configuration value (name, purpose, default); ensure following only the README starts the full solution
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

- [x] 13. Final checkpoint - full stack and pipeline green
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP.
- Property-based testing is excluded by stack mandate; the design's Correctness Properties are validated through JUnit unit tests and the Cucumber BDD suite, annotated as `_Validates Property N_` on the relevant test tasks.
- Each task references specific requirement clauses for traceability.
- Checkpoints (tasks 6, 10, 13) provide incremental validation at natural breaks.
- Backend `pom.xml` is mutated across tasks 2.1–2.5; the dependency graph schedules them in distinct waves to avoid write conflicts.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["2.2", "3.2", "4.1", "5.1", "8.1", "9.1"] },
    { "id": 2, "tasks": ["2.3", "3.1", "3.3", "4.2", "4.4", "8.2", "9.2"] },
    { "id": 3, "tasks": ["2.4", "3.4", "3.5", "4.3", "4.5", "4.6", "4.8", "8.3", "9.3"] },
    { "id": 4, "tasks": ["2.5", "4.7", "4.9", "8.4", "8.5"] },
    { "id": 5, "tasks": ["7.1", "11.1", "11.2", "11.3", "11.4"] },
    { "id": 6, "tasks": ["7.2"] },
    { "id": 7, "tasks": ["12.1"] }
  ]
}
```
