# Tasks — fix-dnd-overlay-add-seed-data

## 1. FE: DragOverlay fix

- [x] 1.1 Split `TicketCard` into presentational `TicketCardContent` (markup only) + draggable wrapper; remove the inline `translate3d` transform; keep `opacity-60` placeholder styling while `isDragging`; carry the full `card` in `useDraggable` data (D2)
- [x] 1.2 In `BoardPage`, add active-card state set on `onDragStart` and cleared on `onDragEnd`/`onDragCancel`; render `<DragOverlay>` inside `DndContext` with `TicketCardContent` for the active card (D1)
- [x] 1.3 Update/add Vitest coverage: dragged card renders in overlay outside source column; cancelled drag clears overlay and issues no request; existing drop/revert/no-op tests still pass
- [x] 1.4 Run `make fe-lint` and `make fe-test`; verify manually in the running app that a card stays visible when dragged across columns (also confirmed via the `board-smoke` Playwright e2e: real cross-column drag + persisted drop)

## 2. BE: local seed migration

- [x] 2.1 Generate the Argon2 hash for the documented demo password with `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (one-off snippet) (D5)
- [x] 2.2 Author `db/changelog/sql/0009-local-seed-data.sql`: 1 verified demo user, 3 teams, 2 epics per team, tickets per team covering all five states with mixed types (some epic-linked, some not), ≥1 comment per ticket; fixed UUIDs and timestamps (D4)
- [x] 2.3 Register the changeset in `db.changelog-master.yaml` with `context: local` (used `contextFilter: local` — Liquibase 4.31 renamed the attribute; `context` is deprecated)
- [x] 2.4 Add `spring.liquibase.contexts: ${YAJ_LIQUIBASE_CONTEXTS:prod}` to `application.yml`; set `YAJ_LIQUIBASE_CONTEXTS=local` on the `be` service in `docker-compose.yml`; document the variable in README (D3)
- [x] 2.5 Verify gating: `make be-test` (Testcontainers run without the local context — no seed rows, all schema migrations apply); confirm tests unaffected

## 3. End-to-end verification

- [x] 3.1 Reset the compose DB volume, `make up`, confirm: login with seed credentials works, board shows 3 teams, every column has a ticket, ticket details show comments
- [x] 3.2 Restart `be` against the seeded DB — no duplicate rows, no changeset re-execution
- [x] 3.3 Run `make fmt`, full `make be-lint` / `make fe-lint`
