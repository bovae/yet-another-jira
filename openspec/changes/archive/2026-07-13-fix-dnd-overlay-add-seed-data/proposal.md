# Fix board DnD drag visibility + local seed data

## Why

Two usability gaps: (1) dragging a board card is broken visually — the card is rendered in-place with an inline transform inside the column's `overflow-y-auto` container, so the moment the pointer leaves the source column the card is clipped and invisible, making cross-column drops blind; (2) a fresh local stack starts with an empty database, so every manual test session begins with tedious hand-creation of teams, boards, epics, tickets, and comments.

## What Changes

- Board drag-and-drop renders the dragged card in a `@dnd-kit/core` `DragOverlay` (portal above all columns), so the card stays visible across the whole board while dragging; the original card stays in place as a dimmed placeholder. Drop/persist behavior is unchanged.
- New Liquibase seed changeset, gated to local runs via Liquibase's native `context` mechanism (`spring.liquibase.contexts` supplied only by docker-compose). Seeds a usable dataset: multiple teams, epics per team, tickets covering every board column for each team, and a comment on each ticket. Never runs in non-local environments (context unset → changeset skipped).

## Capabilities

### New Capabilities
- `local-seed-data`: local-only database seed migration that populates teams, epics, tickets (all board states), and comments so the app is usable immediately after `make up`.

### Modified Capabilities
- `fe-board`: drag-and-drop requirement gains overlay behavior — the dragged card SHALL remain visible when leaving its source column (rendered via `DragOverlay`), with the source card shown as a placeholder.

## Impact

- `fe/src/pages/BoardPage.tsx` — track active drag, render `DragOverlay`.
- `fe/src/components/TicketCard.tsx` — split presentational card content from the draggable wrapper; drop inline transform.
- `be/src/main/resources/db/changelog/` — new seed SQL changeset + master changelog entry with `context: local`.
- `be/src/main/resources/application.yml` — `spring.liquibase.contexts: ${YAJ_LIQUIBASE_CONTEXTS:}`.
- `docker-compose.yml` — set `YAJ_LIQUIBASE_CONTEXTS=local` for the `be` service.
- No API, schema, or dependency changes; `@dnd-kit/core` already ships `DragOverlay`.
