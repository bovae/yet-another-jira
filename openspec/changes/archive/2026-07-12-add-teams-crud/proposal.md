# Add Teams CRUD + Delete Guard (E5)

## Why

Teams are the root grouping for all business data (epics, tickets, board). The auth spine (E0–E4) is complete and every business endpoint is now authenticated — E5 is the first true business feature and unblocks the widest fan-out (E6 epics, E7 tickets, E13 FE management screens).

## What Changes

- New REST endpoints: `GET/POST /api/v1/teams`, `GET/PUT/DELETE /api/v1/teams/{id}` (all require auth — already enforced by E4's catch-all rule).
- Create/rename validation: name trimmed, non-empty (`400`), unique case-insensitively (`409` — DB `citext` column backs this).
- Delete guard: `409 Conflict` when the team has any epics or tickets; never cascade. Clean delete → `204`.
- Timestamps server-set UTC, ISO-8601 in responses (`created_at` DB-defaulted; `modified_at` bumped on rename).
- No membership/ownership — all verified users manage all teams (§4).

## Capabilities

### New Capabilities

- `teams-crud`: team lifecycle — list, create, get, rename, delete with referential delete guard and case-insensitive name uniqueness.

### Modified Capabilities

_None — no existing specs in `openspec/specs/`._

## Impact

- **Backend**: new `com.bovae.yaj.teams` slice (controller + service + DTOs); `TeamRepository` gains a case-insensitive existence query; reuses existing `NotFoundException`/`ConflictException`/`ValidationException` → `GlobalExceptionHandler` mapping and `EpicRepository.existsByTeamId` / `TicketRepository.existsByTeamId` guards from E0.
- **API**: additive only — no changes to existing endpoints.
- **Tests**: service unit tests (trim, empty→400, dup→409, delete-with-refs→409, clean delete→204); new `teams.feature` BDD (create→rename→delete-guard); JaCoCo 90/90 stays green.
- **DB/FE**: none — schema already exists; FE screens land in E13.
