# Design — fix-dnd-overlay-add-seed-data

## Context

**DnD bug.** `TicketCard` (`fe/src/components/TicketCard.tsx:32-34`) applies the drag `transform` inline on the in-flow card element. The card lives inside `Column`'s `overflow-y-auto` container (`fe/src/components/Column.tsx:39`) within a bounded-height section, so the moment the pointer leaves the source column the card is clipped. `@dnd-kit/core@6.3.1` (already installed) ships `DragOverlay`, which portals the drag preview to `document.body`, escaping any overflow context.

**Seed data.** Backend uses Liquibase (`db.changelog-master.yaml` + numbered SQL files `0001`–`0008`). Schema: `users`, `teams`, `epics` (FK team), `tickets` (FK team, optional epic, FK `ticket_types`/`ticket_states` lookup rows, NOT NULL `created_by` → users), `comments` (FK ticket, NOT NULL `author_id` → users). There is no boards table — a "board" is a team's tickets grouped by the five `ticket_states`. No Spring profiles exist; all config is `YAJ_*` env vars with docker-compose supplying local values.

## Goals / Non-Goals

**Goals:**
- Dragged card visible across the whole board; existing drop/persist/revert behavior untouched.
- One idempotent, deterministic seed changeset producing a demo-ready dataset, impossible to run outside local by default.

**Non-Goals:**
- In-column reordering, drag animations, touch-specific polish.
- Seed data for non-local environments, seed CLI/tooling, re-seeding or reset commands (drop the DB volume instead).
- Introducing Spring profiles.

## Decisions

### D1: `DragOverlay` from `@dnd-kit/core`, no new dependency
Render `<DragOverlay>` inside the existing `DndContext` in `BoardPage`. Track the active card in component state: `onDragStart` stores the `BoardCard` (carried via `useDraggable`'s `data`, alongside the existing `fromState`), `onDragEnd`/`onDragCancel` clear it. Alternative — restructuring column CSS to avoid clipping (removing `overflow-y-auto` or making columns unbounded) — rejected: it breaks D8 (bounded columns with scroll) and any ancestor `transform`/`overflow` would re-break it; `DragOverlay` is the library's canonical answer.

### D2: Split `TicketCard` into draggable wrapper + presentational content
The overlay must render the card's visuals without registering a second draggable (duplicate `useDraggable` id). Extract the card markup into a presentational `TicketCardContent` (same file), used by both the draggable `TicketCard` and the overlay. The inline `translate3d` transform is removed from `TicketCard` — the overlay handles movement; the source card keeps `opacity-60` (existing dimmed style) while `isDragging`.

### D3: Liquibase `context` gate, fail-safe default
Seed lives in `0009-local-seed-data.sql`, registered in the master changelog with `context: local`. Gate: `application.yml` sets `spring.liquibase.contexts: ${YAJ_LIQUIBASE_CONTEXTS:prod}` and docker-compose sets `YAJ_LIQUIBASE_CONTEXTS=local` on the `be` service. The `prod` fallback matters: Liquibase runs *every* changeset (contexted or not) when no runtime context is declared, so an empty default would leak the seed into real environments. With `prod` as default, forgetting the env var skips the seed; schema changesets stay context-free and always run. Alternatives rejected: Spring `@Profile("local")` `CommandLineRunner` (introduces profile infra + Java code + manual idempotency for what Liquibase does natively); `data.sql` (no gating, no run-once tracking).

### D4: Deterministic SQL seed with fixed UUIDs
Plain SQL inserts with hardcoded UUIDs (no `gen_random_uuid()`), so reruns against a wiped DB produce identical data and FK references are plain literals. Liquibase's DATABASECHANGELOG gives run-once semantics — no `ON CONFLICT` needed. Dataset: 1 verified user (`demo@yaj.local`), 3 teams, 2 epics per team, ≥5 tickets per team (one per board state, mixed types, mix of epic-linked and epic-less), 1+ comment per ticket. Timestamps: fixed literals or `now()` — fixed preferred for full determinism.

### D5: Seed user password — precomputed Argon2 literal
`LoginService` verifies with `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`; pgcrypto cannot produce Argon2, so the seed embeds a hash literal generated once with that exact encoder (one-off snippet during implementation) for a documented password (e.g. `Demo1234!`), and `email_verified = true` so login works without the mailpit flow. Argon2 encodes its salt in the hash string, so a fixed literal verifies fine. Alternative — FK-only user without a usable login — rejected: "usable app" includes signing in.

## Risks / Trade-offs

- [Seed leaks to a real environment if someone sets `YAJ_LIQUIBASE_CONTEXTS=local` there] → the fail-safe default covers omission (the likely mistake); explicit misconfiguration is out of scope, and README documents the variable's meaning.
- [Demo credentials are public in the repo] → acceptable: row exists only in local DBs; documented as local-only. Hash literal, not plaintext, in SQL.
- [Overlay card renders without drop animation / slight visual jump on drop] → acceptable; optimistic cache move re-renders the card in the target column immediately.
- [Existing Vitest board tests may assert on the old single-element drag rendering] → update tests alongside (spec scenarios map to them).

## Migration Plan

Pure additive. Deploy: normal image rebuild; non-local environments see one new (skipped) changeset row-filter, no data change. Rollback: revert commit; seeded local DBs can be reset by dropping the compose volume.

## Open Questions

None.
