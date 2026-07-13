# Design — Board read (E9)

## Context

E7 tickets and E6 epics are live; the only board endpoint is the hardcoded `GET /api/v1/mock/board` still consumed by the FE placeholder. The `tickets` table already has the `(team_id, state)` index, `modified_at` is maintained with the "advance only on actual change" semantics (E7), and comments do not touch it (E8) — so `modified_at DESC` is a stable, correct board ordering key. The codebase pattern is controller (HTTP only, `web/controller`) → service slice package (`@Transactional`, business rules) → `domain/repository`, errors via typed exceptions → `GlobalExceptionHandler` → RFC 9457.

## Goals / Non-Goals

**Goals:**
- Real `GET /api/v1/teams/{teamId}/board`: 5 columns in workflow order, cards `modified_at DESC`, server-side AND-combined filters (type, epic, title substring).
- One indexed query per request; usable at 100+ tickets.
- Contract complete enough for E15 to render cards (title, type, epic title) without follow-up fetches.

**Non-Goals:**
- Mock removal (`MockBoardController`, `BoardView`/`BoardColumn`/`BoardCard`) — FE still calls it; removed in E15.
- Pagination/limits — a single team board at required scale (100+) is a single cheap indexed query. Revisit only if boards grow to thousands.
- Column display names — presentation concern; FE maps state codes to labels.
- DnD state persistence — E10, reuses E7's `PATCH /api/v1/tickets/{id}`.

## Decisions

### 1. Route: `GET /api/v1/teams/{teamId}/board`
As specified by the catalog. Board is a per-team read model, so team scoping lives in the path; unknown team → `404` (unlike `GET /tickets?teamId=` which returns an empty list — the board of a nonexistent team is not an empty board, it's a missing resource the FE must distinguish).

### 2. Server-side filtering in one repository query
Requirements §8 allows client- or server-side; server-side is "preferred for 100+ tickets" (catalog). One JPQL query on `TicketRepository` with optional params:

```
team_id = :teamId
AND (:type IS NULL OR type = :type)
AND (:epicId IS NULL OR epic_id = :epicId)
AND (:q IS NULL OR LOWER(title) LIKE LOWER('%' || :q || '%') ESCAPE '\')
ORDER BY modified_at DESC
```

Alternative — filter in the service after `findByTeamId`: simpler but ships every ticket over the wire from the DB on every keystroke-driven search; the query is barely more code and uses the existing index. Grouping into columns happens in the service (`groupingBy` state over an already-ordered list) — no need for 5 queries.

### 3. LIKE wildcards escaped, search is literal substring
`q` is user input; `%`/`_`/`\` are escaped before binding so search is a literal case-insensitive substring match, not a pattern language. Small static helper in the service.

### 4. Filter validation
- `type` → `TicketType.parse()` (existing helper) → `400` on garbage, consistent with tickets-crud.
- `epicId` → no existence/team check: an unknown or foreign-team epic simply matches no tickets → empty columns. Validating would cost an extra query to protect nothing (read-only endpoint, no integrity at stake).
- blank `q` / absent params → filter not applied.

### 5. Response shape: new immutable DTOs, epic title joined in
`BoardResponse(List<BoardColumnResponse>)`, `BoardColumnResponse(String state, List<BoardCardResponse> cards)`, `BoardCardResponse(UUID id, String title, String type, @Nullable UUID epicId, @Nullable String epicTitle)` — records under `web/dto`, names distinct from the mock's `BoardView`/`BoardColumn`/`BoardCard` so both compile until E15 deletes the mock. Epic titles resolved with one `findByTeamId` epics query into a map (2 queries total, not N+1). All 5 columns always present, in workflow order, empty lists included — the FE never synthesizes columns.

### 6. New `board` service slice
`com.bovae.yaj.board.BoardService` (`@Transactional(readOnly = true)`) + `web/controller/BoardController`. Follows the existing slice layout (`tickets`, `epics`, `comments`). Not bolted onto `TicketService` — the board is a distinct read model with its own contract and E10/E15 will grow around it.

## Risks / Trade-offs

- [No pagination] → board payload grows linearly with team size. Acceptable at required scale (100+ cards ≈ a few tens of KB); the `(team_id, state)` index plus single query keeps the DB side flat. Revisit with a `limit` per column if teams reach thousands of tickets.
- [Search hits `LOWER(title) LIKE`] → no index use for the substring scan. It scans only the team's rows (already narrowed by the indexed `team_id` predicate), so bounded by team size — fine at required scale.
- [Two overlapping board endpoints until E15] → mock and real coexist briefly. Deliberate: removing the mock now breaks the FE placeholder page. Tracked as E15 cleanup.

## Open Questions

_None._
