# Design — Tickets CRUD + validation + `modified_at`

## Context

E5 (teams) and E6 (epics) established the slice pattern this change copies: controller → `@Transactional` service → Spring Data repository, typed exceptions (`ValidationException`/`NotFoundException`/`ConflictException`) mapped centrally to RFC 9457 problems. Everything ticket-shaped already exists from E0: the `Ticket` entity (with `@UpdateTimestamp` on `modified_at`), `TicketRepository`, the `tickets` table with FKs (`epic_id … ON DELETE RESTRICT` is on the epic side; `comments.ticket_id … ON DELETE CASCADE` handles comment cleanup), `TicketType`/`TicketState` enums with `parse` helpers that throw `ValidationException`, and `CurrentUserProvider` for `created_by`. Auth is E4's catch-all rule — no security change.

Two things distinguish tickets from the epics slice: the team **is** mutable on edit (with the same-team epic rule enforced against the new team), and `modified_at` semantics are an explicit requirement (advance only on actual change).

## Goals / Non-Goals

**Goals:**
- Ticket lifecycle endpoints under `/api/v1/tickets` mirroring the epics slice.
- Server-authoritative validation of enums and references, including the same-team epic rule.
- `modified_at` advances only on actual field/state change; no-op saves leave it alone.
- A state-change endpoint (`PATCH`) that is idempotent — the E10 drag-and-drop contract.

**Non-Goals:**
- Board read/ordering/filters (E9), comments (E8), FE screens (E14).
- Pagination on the list endpoint (board is the 100+-ticket surface, and it gets its own endpoint in E9).
- Concurrent-edit conflict detection (§9: last write wins).
- Delete guard — nothing references tickets except comments, which cascade by design.

## Decisions

### 1. Mirror the E5/E6 slice layout
`TicketService` in `com.bovae.yaj.tickets`, `TicketController` in `web.controller`, record DTOs in `web.dto`. No new patterns.

### 2. `modified_at` via Hibernate dirty checking — no manual comparison
The entity already carries `@UpdateTimestamp`. Hibernate's dirty check compares against the loaded snapshot, not setter calls: writing the same values back leaves the entity clean, produces no `UPDATE`, and the timestamp stands. An actual change makes the entity dirty and `@UpdateTimestamp` advances the column in the same statement. That is exactly the required semantics — "compare before persist" is what dirty checking *is*. Alternative — hand-rolled field-by-field comparison in the service — duplicates ORM machinery and adds a drift risk for every future field.

### 3. Same-team epic rule as one service check, run on create and update
When `epicId` is present, load the epic (`404` if missing) and require `epic.teamId == ticket.teamId` — the *incoming* team on both create and update, so changing the team while keeping an old-team epic fails the same check. Violation → `ValidationException` (`400`): it rejects the submitted combination of references, same category as a bad enum code. (`409` was considered; the codebase reserves it for uniqueness/delete-guard conflicts.)

### 4. Unknown referenced team/epic → `404`
Same convention as epics: `NotFoundException` for a missing referenced entity, reusing the existing handler mapping.

### 5. PUT is full replacement; team is editable; PATCH is state-only
- `TicketCreateRequest(teamId, type, state, epicId, title, body)` — state is a required field per §6, so create accepts any of the 5 canonical codes (no forced `new`).
- `TicketUpdateRequest` has the same shape: §6 explicitly allows editing team, so unlike epics there is no immutability-by-contract-shape. Omitted `epicId` clears the reference (full replacement).
- `TicketStateChangeRequest(state)` for `PATCH /{id}` — the drag-and-drop contract. Only state, nothing else: E10 needs a minimal, idempotent call. Idempotency falls out of decision 2 — a same-state PATCH is a no-op save, returns `200`, and doesn't advance `modified_at` (so a DnD retry doesn't reorder the board).

### 6. Validation split, same as teams/epics
Bean Validation on DTOs for structure (`@NotNull` teamId, `@NotBlank` title/body/type/state, `@Size` caps), service-level normalization for trim semantics, enum `parse` helpers for the codes. Caps mirror the epics slice — title ≤ 200, body ≤ 10000 after trimming — because the columns are uncapped `text` and §9 requires rejecting oversized payloads server-side.

### 7. List filter is a plain where-clause
`GET /api/v1/tickets?teamId=` → `findByTeamId(UUID)`, otherwise `findAll()`. Same as epics: no existence check on a read filter, empty array for an unknown team.

### 8. Delete is unguarded
Existence check (`404` unknown), then `delete`. Comments disappear via the existing `ON DELETE CASCADE` — no application code. Confirmation is a FE concern (E14).

## Risks / Trade-offs

- [Dirty-checking gives no-op detection for free, but a future listener/interceptor that touches the entity would silently break the no-op guarantee] → The BDD scenario "save unchanged values does not advance `modified_at`" pins the behavior; any regression fails CI.
- [Unfiltered `GET /api/v1/tickets` returns everything, unpaginated] → Same trade-off accepted for epics; the 100+-ticket surface is the E9 board endpoint. Revisit there.
- [Guard pre-check + FK translation pattern from teams/epics now has no third occurrence to extract (tickets need no delete guard)] → The extract-if-three note from E6 stays moot.

## Migration Plan

Additive endpoints only; no schema change, no data migration, no new dependencies. Deploy normally; rollback = revert the commit.

## Open Questions

None.
