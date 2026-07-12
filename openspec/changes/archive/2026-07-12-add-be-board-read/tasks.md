# Tasks — Board read (E9)

## 1. Repository

- [x] 1.1 Add the filtered board query to `TicketRepository`: JPQL selecting a team's tickets with optional `type`, `epicId`, and escaped case-insensitive title-substring params, ordered by `modified_at DESC` (design decision 2/3)

## 2. Service

- [x] 2.1 Create `com.bovae.yaj.board.BoardService` (`@Transactional(readOnly = true)`): require team (`404` via `NotFoundException`), parse `type` via `TicketType.parse` (`400`), normalize blank filters to null, escape LIKE wildcards in `q`, run the query, resolve epic titles via `EpicRepository.findByTeamId` map, group cards into the 5 workflow-ordered columns (all present, empty included)
- [x] 2.2 Add response records under `web/dto`: `BoardResponse`, `BoardColumnResponse(state, cards)`, `BoardCardResponse(id, title, type, epicId, epicTitle)`

## 3. Controller

- [x] 3.1 Create `web/controller/BoardController`: `GET /api/v1/teams/{teamId}/board` with optional `type`, `epicId`, `q` query params, delegating to `BoardService` and returning `ResponseEntity<BoardResponse>`

## 4. Tests

- [x] 4.1 `BoardServiceTest` unit tests: 5 columns always present in workflow order, grouping by state, epic title resolution (with/without epic), unknown team → `NotFoundException`, invalid type → validation error, blank-filter normalization, wildcard escaping
- [x] 4.2 Cucumber BDD feature `board.feature`: seed tickets via API across states/types/epics → board has 5 workflow-ordered columns with cards most-recently-modified first → each filter narrows correctly → combined filters AND → unknown team `404` → unauthenticated `401`

## 5. Gate

- [x] 5.1 `make fmt` then `make be-lint` green (Spotless, Error Prone, unit + BDD, JaCoCo 90/90)
