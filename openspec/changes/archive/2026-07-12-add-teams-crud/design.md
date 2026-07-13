# Design — add-teams-crud

## Context

E0–E4 done: `Team` entity maps the existing `teams` table (`name citext NOT NULL UNIQUE`, DB-defaulted `id`/`created_at`/`modified_at`), `EpicRepository.existsByTeamId` / `TicketRepository.existsByTeamId` exist, typed exceptions map to RFC 9457 via `GlobalExceptionHandler`, and `SecurityConfig` already authenticates all of `/api/v1/**` by default — new endpoints are protected with zero security changes. Established slice pattern: service in a feature package (`com.bovae.yaj.<feature>`), controller in `web/controller`, request/response records in `web/dto`, `Clock` bean injected for time.

## Goals / Non-Goals

**Goals:**
- Team CRUD endpoints per spec `teams-crud` with trim/non-empty/unique-name validation and the epic/ticket delete guard.
- Keep JaCoCo 90/90 and add BDD coverage for the primary flow.

**Non-Goals:**
- Membership/ownership/roles (§4 excludes), pagination (team count is small), FE screens (E13), epics/tickets endpoints (E6/E7), soft delete, optimistic locking (§9: last write wins).

## Decisions

1. **Follow the existing slice layout** — `TeamService` in `com.bovae.yaj.teams`, `TeamController` in `web.controller`, records `TeamRequest` (single `name` field serves create and rename) and `TeamResponse` in `web.dto`. Alternative: self-contained module package — rejected, inconsistent with the auth slices.
2. **Uniqueness: service pre-check + DB constraint as backstop.** `TeamRepository.existsByName(String)` — the column is `citext`, so DB equality is already case-insensitive; no `IgnoreCase` JPQL needed (it would `lower()` a citext and defeat the index). Rename excludes self with `existsByNameAndIdNot`. The `UNIQUE` constraint catches the race window; that residual `DataIntegrityViolationException` → generic 500 is acceptable at this scale, no per-slice catch.
3. **Delete guard = two existence checks in the service** (`epicRepository.existsByTeamId(id) || ticketRepository.existsByTeamId(id)`) throwing `ConflictException` with a message naming the reason. The FKs (`ON DELETE RESTRICT`) are the backstop. Alternative: rely on FK violation alone — rejected, spec requires a clear 409 message.
4. **Validation in two layers, per convention:** `@NotBlank` on the request record (Jakarta, 400 via existing handler) + service-side trim-then-check as the authoritative rule (§9: server-side validation is authoritative; `" "` passes `@NotBlank`? no — but `"a "` trims server-side).
5. **`modified_at` bumped by Hibernate `@UpdateTimestamp` on the entity, not by the service.** The original plan (service sets `modified_at` from the injected `Clock`) is unworkable: the column was mapped `@Generated`, which Hibernate treats as *immutable* (`insertable=false, updatable=false`) — a service-side `setModifiedAt(...)` on rename is silently dropped (`HHH000502`), so the spec's "rename advances `modified_at`" would never hold. Switching `modified_at` to `@UpdateTimestamp` makes Hibernate write it in-memory on insert **and** every update, satisfying the requirement without a DB trigger and without the service touching the field (the `Clock` dependency drops out). `created_at`/`id` stay `@Generated` (DB-defaulted, read back on insert). The rename-bump is verified in BDD (create → rename, assert timestamp advanced) rather than the mocked unit test, since it is now a persistence-layer behavior.
6. **Mapping by hand** — `TeamResponse.from(Team)` static factory. MapStruct for a 4-field record is overhead; introduce it when a slice has real derived fields.

## Risks / Trade-offs

- [Check-then-act race on name uniqueness] → DB `UNIQUE` constraint guarantees integrity; the rare race returns 500 instead of 409. Acceptable; revisit only if it shows up in practice.
- [Guard check-then-delete race (epic created mid-delete)] → FK `ON DELETE RESTRICT` makes the delete fail rather than orphan data.
