# FE Board + Selector + Filters + DnD (E15)

## Why

The real board endpoint (E9, `GET /api/v1/teams/{teamId}/board`) is live, but the primary screen still renders the hardcoded mock board with no team selection, no filters, and inert drag-and-drop. E15 delivers the product's main screen and, together with the already-live `PATCH /api/v1/tickets/{id}` state endpoint, completes E10 (drag-persist with revert-on-failure). It also unlocks the E9 cleanup: the mock controller can finally be deleted.

## What Changes

- **Team selector** on the board page: pick one team, board loads via the real `GET /api/v1/teams/{teamId}/board`; no teams yet → prompt to create one. Selection survives refresh (URL search params).
- **Real board rendering**: 5 columns in workflow order with human-readable labels, cards showing title + type badge + epic title, server-provided most-recently-modified-first order.
- **Filters**: ticket type, epic (scoped to the selected team), and case-insensitive title search — passed as server-side query params (`type`, `epicId`, `q`), combined AND.
- **Drag-and-drop** via the already-installed `@dnd-kit/core`: dropping a card on another column optimistically moves it and persists the state via `PATCH /api/v1/tickets/{id}`; on failure the card reverts and an error shows.
- **Entry points**: create-ticket button (reuses `TicketFormDialog`, team preselected) and card click → `/tickets/{id}`.
- **Mock removal (E9 cleanup)**: delete BE `MockBoardController` + `BoardView`/`BoardColumn`/`BoardCard` mock DTOs and their tests; FE `getMockBoard` replaced by the real board API module.
- **Playwright** drag-persist-refresh smoke replacing the current mock-board smoke.

## Capabilities

### New Capabilities

- `fe-board`: the primary Kanban screen — team selector, 5 ordered columns with cards, type/epic/search filters (AND, server-side), drag-and-drop state persistence with optimistic move and revert-on-failure, create-ticket and open-ticket entry points, shared loading/empty/error states.

### Modified Capabilities

_None — `fe-routing`'s "board route renders" requirement is unchanged (same route, real screen), and the `board-read` / `tickets-crud` backend contracts are consumed as-is._

## Impact

- FE: rewrite `fe/src/api/board.ts` against the real endpoint; rework `BoardPage`, `Column`, `TicketCard` (selector, filters, DnD); add `patchTicketState` to `fe/src/api/tickets.ts`; Vitest for filters + optimistic move/revert; update `fe/e2e/board-smoke.spec.ts` to drag-persist-refresh.
- BE: deletion only — `MockBoardController`, mock board DTOs, related tests.
- No new dependencies — `@dnd-kit/core` (installed, inert) and existing shadcn Select/Dialog cover everything.
