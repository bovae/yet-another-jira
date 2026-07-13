# Design — Epics CRUD (team-scoped) + Delete Guard

## Context

E5 (teams) established the pattern this slice copies: controller → `@Transactional` service → Spring Data repository, typed exceptions (`ValidationException`/`NotFoundException`/`ConflictException`) mapped centrally to RFC 9457 problems, and race-safe uniqueness/guard handling via `DataIntegrityViolationException` translation. The `Epic` entity, `EpicRepository`, the `epics` table (with `idx_epics_team_id`), and `tickets.epic_id … ON DELETE RESTRICT` already exist from E0. Auth is enforced by E4's catch-all rule — no security change needed.

## Goals / Non-Goals

**Goals:**
- Epic lifecycle endpoints under `/api/v1/epics`, mirroring the teams slice one-for-one.
- Team fixed at creation; delete guarded by ticket references, race-safe.

**Non-Goals:**
- Moving epics between teams (§5 explicitly out of scope).
- Title uniqueness (no requirement), pagination (epic counts are small), FE screens (E13), ticket-side same-team enforcement (E7).

## Decisions

### 1. Mirror the E5 slice layout
`EpicService` in `com.bovae.yaj.epics`, `EpicController` in `web.controller`, record DTOs in `web.dto`. No new patterns — consistency over invention.

### 2. Team immutability by contract shape, not runtime checks
Two request records: `EpicCreateRequest(teamId, title, description)` and `EpicUpdateRequest(title, description)`. The update DTO simply has no team field, so a smuggled `teamId` in the PUT body is ignored by Jackson binding and the entity's `teamId` is never touched. Alternative — one shared DTO plus a service check rejecting team changes — adds a validation branch and an error contract for something the API shouldn't accept at all.

### 3. Unknown team on create → `404`
Reuses `NotFoundException` ("Team '{id}' was not found") and the existing handler mapping. Alternative `400` was rejected: the codebase already answers "referenced entity missing" with `404`, and inventing a second convention for body-carried references isn't worth it.

### 4. Validation split, same as teams
Bean Validation on the DTOs for cheap structural rules (`@NotBlank`, `@Size(max = 200)` title, `@Size(max = 10000)` description, `@NotNull` teamId), service-level `normalize` for trim semantics (whitespace-only title must fail *after* trimming; blank description collapses to null). Caps are server-side because the columns are uncapped `text`.

### 5. Delete guard: pre-check + FK race translation
`ticketRepository.existsByEpicId(id)` pre-check → `ConflictException`; then `delete` + `flush` inside a try translating `DataIntegrityViolationException` to the same `409`. The flush stays inside the try so a concurrently inserted referencing ticket surfaces as a catchable exception, not a commit-time failure — identical to `TeamService.delete`.

### 6. List filter is a plain where-clause
`findByTeamId(UUID)` when `teamId` is present, `findAll()` otherwise. A non-existent team id yields an empty array — no existence check on a read filter; the FE only passes ids it got from the team list.

### 7. `modified_at` via existing `@UpdateTimestamp`
The entity already carries it; a dirty update advances the timestamp and Hibernate persists it. No manual timestamp code.

## Risks / Trade-offs

- [No-op PUT (same title/description) leaves `modified_at` unchanged — Hibernate only updates dirty entities] → Acceptable; the spec doesn't require advancing on no-op saves, and E7 makes the same choice deliberately for tickets.
- [Unfiltered `GET /api/v1/epics` returns everything] → Fine at this product's scale; the board/FE always scope by team. Revisit only if a global epic list ever ships.
- [Guard pre-check + FK translation duplicates the teams pattern rather than extracting a helper] → Two occurrences is not yet duplication worth an abstraction; extract if E7 makes it three.

## Migration Plan

Additive endpoints only; no schema change, no data migration. Deploy normally; rollback = revert the commit.

## Open Questions

None.
