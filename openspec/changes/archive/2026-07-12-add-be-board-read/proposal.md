# Board read (E9)

## Why

The primary Kanban screen (E15) needs a real board contract: today the only board endpoint is the hardcoded `GET /api/v1/mock/board`. E7 (tickets) and E6 (epics) are done, so the backend can now serve the real per-team board — 5 state columns, most-recently-modified-first ordering, and server-side type/epic/title filters (requirements §8).

## What Changes

- New endpoint `GET /api/v1/teams/{teamId}/board` returning exactly 5 columns in workflow order (`new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`), one per ticket state, always present even when empty.
- Cards within a column ordered most-recently-modified first (`modified_at DESC`).
- Each card carries id, title, type, epic id + epic title (epic display recommended by §8), so the FE renders without extra fetches.
- Server-side filters combined with AND: `type` (validated enum), `epicId`, and `q` — case-insensitive substring search over title with LIKE wildcards treated literally.
- `404` for an unknown team; `400` for an invalid type code.
- Single indexed query per request — stays usable at 100+ tickets.

Out of scope: removing `MockBoardController` + mock `BoardView`/`BoardColumn`/`BoardCard` DTOs — the FE board placeholder still calls `/api/v1/mock/board`; removal happens in E15 when the FE switches to the real endpoint (per the catalog's "once the real endpoint + FE are wired"). Pagination is also skipped: one team's board at the required 100+ scale fits a single query on the existing `(team_id, state)` index.

## Capabilities

### New Capabilities

- `board-read`: per-team Kanban board read model — 5 ordered state columns, card ordering by recency of modification, and AND-combined type/epic/title-search filters.

### Modified Capabilities

_None — tickets-crud, epics-crud, and teams-crud contracts are unchanged._

## Impact

- **Backend only.** New `com.bovae.yaj.board` service slice, new `BoardController` under `web/controller`, new response DTOs under `web/dto`, one new filtered query on `TicketRepository`.
- No schema change — `(team_id, state)` index already exists.
- FE untouched (E15 consumes this contract later).
