# Tasks — FE Board + Selector + Filters + DnD (E15)

## 1. API modules

- [x] 1.1 Rewrite `fe/src/api/board.ts` against `GET /api/v1/teams/{teamId}/board` (D1): `getBoard(teamId, {type?, epicId?, q?}, {signal})`, non-blank params only, types mirroring the BE DTOs (`epicId`/`epicTitle` on cards, no column label), runtime parse, `problemError`/`ApiError`; update `fe/src/api/board.test.ts`
- [x] 1.2 Add `patchTicketState(id, state)` to `fe/src/api/tickets.ts` (D5) + tests in `tickets.test.ts`

## 2. Board page — selector, columns, filters

- [x] 2.1 Rework `BoardPage`: `useSearchParams`-backed `teamId`/`type`/`epicId`/`q` (D2), teams query + team Select (effective team = param ?? first team), no-teams prompt, board query keyed on all params, shared loading/error states
- [x] 2.2 Rework `Column`/`TicketCard` for the real contract: label via `ticketStateLabel(state)`, card epic from `epicTitle`, scrollable column body (`overflow-y-auto`, D8)
- [x] 2.3 Filter controls: type Select (All + `TICKET_TYPES`), epic Select (All + `listEpics(teamId)`, reset on team change), debounced title search into `q` (D3)
- [x] 2.4 Vitest: board renders 5 labelled ordered columns; filters set/omit query params and narrow the board; team change resets epic filter; no-teams prompt

## 3. Drag-and-drop

- [x] 3.1 Wire `DndContext` + `useDraggable` cards + `useDroppable` columns; `PointerSensor` 8px activation + default `KeyboardSensor` (D4)
- [x] 3.2 State-change mutation: optimistic move to top of target column with cache snapshot, rollback + error message on failure, invalidate on settle; same-column drop no-op (D4)
- [x] 3.3 Vitest: optimistic move renders card in target column; rejected PATCH reverts card and shows error; same-column drop issues no request

## 4. Entry points

- [x] 4.1 "New ticket" button → `TicketFormDialog` with `defaultTeamId` = selected team, invalidate board on success (D6)
- [x] 4.2 Card click (not drag) navigates to `/tickets/{id}` (D6)
- [x] 4.3 Vitest: create action opens dialog preset to selected team; card click navigates

## 5. Mock board removal (E9 cleanup, D7)

- [x] 5.1 FE: delete `getMockBoard`/`MOCK_BOARD_PATH` and mock parse remnants (covered by 1.1); drop the mock-board Vitest cases
- [x] 5.2 BE: delete `MockBoardController`, `MockBoardControllerTest`, mock `BoardView`/`BoardColumn`/`BoardCard` records
- [x] 5.3 BE: remove the `/api/v1/mock/board` `permitAll` carve-out from `SecurityConfig`; repoint `AuthEnforcementSliceTest` and the skeleton BDD steps/feature at real endpoints

## 6. E2E + gates

- [x] 6.1 Replace `fe/e2e/board-smoke.spec.ts` with the drag-persist-refresh smoke: create team + ticket via API, drag card to another column, reload, assert the card stayed
- [x] 6.2 `make fe-lint` + `make fe-test` green; `make be-lint` green (coverage holds after deletions); run the Playwright smoke against the compose stack
