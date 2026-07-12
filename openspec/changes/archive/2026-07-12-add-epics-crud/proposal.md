# Add Epics CRUD (team-scoped) + Delete Guard (E6)

## Why

Teams (E5) landed and unblocked the epic layer. Epics are the second business slice on the critical path — E7 tickets need the same-team epic rule and the epic drop-down, and E13 FE management screens need the epic contract. Batch 5 in the epics catalog.

## What Changes

- New REST endpoints: `GET/POST /api/v1/epics` (list supports optional `teamId` filter), `GET/PUT/DELETE /api/v1/epics/{id}` (all require auth — already enforced by E4's catch-all rule).
- Create: epic belongs to exactly one **existing** team (`404` if unknown); title trimmed, non-empty (`400`), capped; description optional.
- **Team is fixed at creation** — the update contract carries only title/description, so the team cannot change (§5: moving an epic between teams is out of scope).
- Delete guard: `409 Conflict` when any ticket references the epic; never cascade. Clean delete → `204`.
- Timestamps server-set UTC, ISO-8601 in responses (`created_at` DB-defaulted; `modified_at` advanced on edit).
- No title uniqueness — the requirements impose none.

## Capabilities

### New Capabilities

- `epics-crud`: epic lifecycle — list (filterable by team), create under a fixed team, get, edit title/description, delete with ticket-reference guard.

### Modified Capabilities

_None — teams-crud, auth, and FE specs are untouched._

## Impact

- **Backend**: new `com.bovae.yaj.epics` slice (`EpicService`) + `EpicController` + request/response DTOs; `EpicRepository` gains `findByTeamId`; reuses `TicketRepository.existsByEpicId`, `TeamRepository`, and the existing `NotFoundException`/`ConflictException`/`ValidationException` → `GlobalExceptionHandler` mapping from E0/E5.
- **API**: additive only — no changes to existing endpoints.
- **Tests**: service unit tests (trim, empty title→400, unknown team→404, team immutable on edit, delete-with-tickets→409, clean delete→204); new `epics.feature` BDD (create under team → edit → delete guard); JaCoCo 90/90 stays green.
- **DB/FE**: none — `epics` table and FK indexes already exist; FE screens land in E13.
