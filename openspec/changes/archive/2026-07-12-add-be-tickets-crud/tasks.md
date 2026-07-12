# Tasks — Tickets CRUD + validation + `modified_at`

## 1. Repository + DTOs

- [x] 1.1 Add `findByTeamId(UUID)` to `TicketRepository`
- [x] 1.2 Add record DTOs in `web.dto`: `TicketCreateRequest(teamId, type, state, epicId, title, body)`, `TicketUpdateRequest` (same shape), `TicketStateChangeRequest(state)`, `TicketResponse(id, teamId, epicId, type, state, title, body, createdBy, createdAt, modifiedAt)` with `from(Ticket)` — Bean Validation: `@NotNull` teamId, `@NotBlank` type/state/title/body, `@Size` title 200 / body 10000

## 2. Service

- [x] 2.1 `TicketService` in `com.bovae.yaj.tickets`: `list(@Nullable teamId)`, `get(id)` (readonly), mirroring `EpicService`
- [x] 2.2 `create(...)`: team exists (`404`), enum `parse` for type/state (`400`), epic exists (`404`) + same-team rule (`400`), trim/normalize title+body, `created_by` from `CurrentUserProvider`, save + flush
- [x] 2.3 `update(id, ...)`: full replacement incl. team; re-run all create validations against the incoming team (stale old-team epic → `400`); rely on Hibernate dirty checking so a no-op save doesn't advance `modified_at`
- [x] 2.4 `changeState(id, state)`: parse (`400`), set, save + flush — same-state patch is a clean no-op
- [x] 2.5 `delete(id)`: `404` unknown, else delete (comments cascade in DB)

## 3. Controller

- [x] 3.1 `TicketController` in `web.controller`: `GET /api/v1/tickets(?teamId=)`, `POST` → `201`, `GET /{id}`, `PUT /{id}` → `200`, `PATCH /{id}` → `200`, `DELETE /{id}` → `204`

## 4. Tests

- [x] 4.1 `TicketServiceTest`: `@ParameterizedTest` enum validation (bad type/state codes), same-team rule create + update (incl. team change keeping old epic), unknown team/epic → not-found, trim + blank/over-length title/body, no-op update doesn't bump `modified_at`, state change persists, same-state no-op, delete unknown → not-found, `created_by` from current user
- [x] 4.2 Controller slice or `TicketControllerTest` for status codes / request binding (mirror `EpicControllerTest` pattern)
- [x] 4.3 Cucumber BDD `tickets.feature`: create → edit → `modified_at` advances → no-op save doesn't advance → PATCH state → same-state PATCH unchanged → epic-from-other-team rejected → delete cascades comments (seed a comment via repository if E8 API absent) → unauthenticated `401`

## 5. Gate

- [x] 5.1 `make fmt && make be-lint` green (JaCoCo 90/90), `make be-test` green
