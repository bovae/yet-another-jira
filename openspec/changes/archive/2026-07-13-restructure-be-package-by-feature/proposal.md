# Restructure Backend to Package-by-Feature

## Why

The backend mixes two package philosophies: `auth/` is fully feature-organized while every other feature (tickets, board, comments, epics, teams) is smeared across five layer packages (`<feature>/` service, `web/controller`, `web/dto`, `domain/model`, `domain/repository`). A single feature change touches five directories, and the layered split forces every class to be `public`. Consolidating now — while the codebase is 6 features small — keeps the move purely mechanical.

## What Changes

- Move each controller from `web/controller/` into its feature package (`tickets/`, `board/`, `comments/`, `epics/`, `teams/`; `AuthController` into `auth/`).
- Move each DTO from `web/dto/` into the feature package that owns it (e.g., `TicketCreateRequest` → `tickets/`, `LoginRequest` → `auth/login/`).
- Shrink `web/` to cross-cutting HTTP infrastructure only: `web/filter/` (correlation/MDC filters) and `web/error/` (global exception handling).
- Keep `domain/` (entities, enums, repositories) as the shared kernel — repositories are used across features (e.g., `TicketRepository` by 5 features), so they stay central.
- Keep cross-cutting packages unchanged: `config/`, `error/`, `security/`, `support/`.
- Reduce visibility to package-private where the move makes it possible (controller + service + DTOs now co-located).
- Update test packages to mirror the new structure.

No behavior, API, or persistence changes — pure structural refactor.

## Capabilities

### New Capabilities

- `be-package-structure`: convention for backend code organization — feature packages own their vertical web slice (controller, service, DTOs); `domain/` is the shared kernel; `web/` holds only cross-cutting HTTP infrastructure.

### Modified Capabilities

None — no spec-level requirements change; this is an internal code organization refactor.

## Impact

- **Affected code**: all classes under `be/src/main/java/com/bovae/yaj/web/controller` and `web/dto` (6 controllers, 23 DTOs) move packages; import statements update across services and tests (`src/test`, `src/it`, BDD steps).
- **APIs**: none — routes, request/response shapes, and status codes unchanged.
- **Dependencies/systems**: none.
- **Risk**: merge conflicts with any in-flight branches touching moved files; land as a single mechanical commit.
