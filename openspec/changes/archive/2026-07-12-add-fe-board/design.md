# Design — FE Board + Selector + Filters + DnD (E15)

## Context

The backend board contract (E9, `openspec/specs/board-read`) is live: `GET /api/v1/teams/{teamId}/board` returns `{columns: [{state, cards: [{id, title, type, epicId?, epicTitle?}]}]}` — 5 columns in workflow order, cards most-recently-modified first, server-side `type`/`epicId`/`q` filters combined AND. Note: columns carry **no label** (unlike the mock). `PATCH /api/v1/tickets/{id}` (E7) changes state idempotently. The FE has `@dnd-kit/core` installed but unused, `TicketFormDialog` with a `defaultTeamId` prop, `TICKET_STATES`/`TICKET_TYPES` label maps in `api/tickets.ts`, cached teams/epics queries, and the shared state components. `BoardPage`/`Column`/`TicketCard` currently render the mock endpoint.

## Goals / Non-Goals

**Goals:**
- Primary Kanban screen: team selector, real board, filters, drag-persist with revert-on-failure, create/open entry points.
- Vitest per the E15 DoD (filters narrow; optimistic move + revert on rejected request); Playwright drag-persist-refresh smoke.
- Delete the mock board (BE controller + DTOs + FE client) — the E9 leftover.

**Non-Goals:**
- No backend behavior changes (deletion only). No manual card ordering (order is `modified_at` desc). No virtualized rendering (stretch, §14). No concurrent-edit handling (last write wins, §9).

## Decisions

**D1 — rewrite `api/board.ts` against the real endpoint.** `getBoard(teamId, {type?, epicId?, q?}, {signal})` builds `/api/v1/teams/{teamId}/board` with only non-blank params (blank must not filter, per board-read). Response types mirror the BE DTOs (`BoardCardResponse` gains `epicId`/`epicTitle`; column loses `label`); keep the existing runtime-parse style and switch error handling to `problemError`/`ApiError` like every post-skeleton module. Column labels come from `ticketStateLabel(state)` — one source of truth, already in workflow order.

**D2 — team + filters live in URL search params.** `useSearchParams` holds `teamId`, `type`, `epicId`, `q`. Refresh-safe (the Playwright drag-persist-**refresh** smoke needs the same team after reload), shareable, and TanStack Query keys derive directly from them — no extra state store. Effective team = `teamId` param ?? first team from the cached teams list; no teams → prompt to create one (mirrors `TicketFormDialog`'s no-teams path). Changing team resets `epicId` (epics are team-scoped; a stale cross-team epic filter would silently empty the board).

**D3 — filters are server-side, search debounced.** Type Select (All + 3 codes), Epic Select (All + `listEpics(selectedTeamId)`), search input debouncing ~300ms into the `q` param. The board query key includes all four params, so TanStack refetches on any change — no client-side filtering logic to test or drift from the server's AND semantics.

**D4 — DnD via `@dnd-kit/core` with optimistic move + snapshot rollback.** One `DndContext` on the page; cards `useDraggable`, columns `useDroppable`. `PointerSensor` with an 8px activation distance so a plain click still opens the ticket (D6); default `KeyboardSensor` stays for accessibility. Drop on a different column runs a mutation: `onMutate` snapshots the cached board and moves the card to the **top** of the target column (a state change bumps `modified_at`, so the server will order it first — optimistic state matches eventual truth); `onError` restores the snapshot and shows an error; `onSettled` invalidates the board query. Same-column drop is a no-op (no request — reordering isn't persisted).

**D5 — `patchTicketState(id, state)` added to `api/tickets.ts`.** `PATCH` with `{state}`, same conventions as siblings. Deferred from E14 (D7 there) because DnD is its only consumer.

**D6 — entry points reuse E14 components.** "New ticket" button opens `TicketFormDialog` with `defaultTeamId` = selected team; on success invalidate the board query. Card click navigates to `/tickets/{id}` — the details view already handles edit/delete/comments.

**D7 — mock board deleted end-to-end.** BE: `MockBoardController` + `MockBoardControllerTest`, mock `BoardView`/`BoardColumn`/`BoardCard` records, the `permitAll` carve-out for `/api/v1/mock/board` in `SecurityConfig`, and the mock-board probes in `AuthEnforcementSliceTest`/skeleton BDD (repointed at real endpoints). FE: `getMockBoard` and its parse helpers replaced by D1.

**D8 — 100+ tickets: plain rendering, scrollable columns.** Columns get a max-height with `overflow-y-auto`; ~100 simple cards render fine without virtualization (explicitly a stretch goal). `useDroppable` covers the whole column so drops land anywhere in it.

## Risks / Trade-offs

- [Optimistic card position can mismatch server order under concurrent edits] → `onSettled` invalidation reconciles within one refetch; last-write-wins is the accepted concurrency model (§9).
- [Click vs drag on the same card] → 8px pointer activation distance is the standard dnd-kit fix; Vitest can't simulate real pointer physics, so the click-through path is covered by the Playwright smoke.
- [Debounced search fires a request per pause] → payloads are small and server-filtered; fine at QA scale.
- [Auth-enforcement tests lose their mock-board probe] → they assert 401-by-default, which any `/api/v1/**` path exercises; repoint at the real board path.
