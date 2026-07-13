# Tasks — Comments

## 1. Repository + DTOs

- [x] 1.1 Add `findByTicketIdOrderByCreatedAtAsc(UUID)` to `CommentRepository`
- [x] 1.2 Add record DTOs in `web.dto`: `CommentCreateRequest(body)` with `@NotBlank` + `@Size(max = 10000)`, `CommentResponse(id, ticketId, authorId, body, createdAt)` with `from(Comment)`

## 2. Service

- [x] 2.1 `CommentService` in `com.bovae.yaj.comments`: `list(ticketId)` (readonly) — ticket exists (`404`), else `findByTicketIdOrderByCreatedAtAsc`
- [x] 2.2 `add(ticketId, body)`: ticket exists (`404`), trim/normalize body (blank → `400`, > 10000 → `400`, mirror `TicketService.normalizeBody`), author from `CurrentUserProvider`, save + flush — never touch the `Ticket` entity

## 3. Controller

- [x] 3.1 `CommentController` in `web.controller` at `/api/v1/tickets/{ticketId}/comments`: `GET` → `200`, `POST` → `201`

## 4. Tests

- [x] 4.1 `CommentServiceTest`: unknown ticket → not-found on list and add, blank/over-length body → validation, trim persisted, author from current user, list delegates to the ordered query
- [x] 4.2 `CommentControllerTest` for status codes / request binding (mirror `TicketControllerTest` pattern)
- [x] 4.3 Cucumber BDD `comments.feature`: create ticket → add two comments → list returns oldest-first → ticket `modified_at` unchanged → blank body `400` → unknown ticket `404` → unauthenticated `401`; switch the `tickets.feature` cascade step to seed its comment via the new API if it currently uses the repository
- [x] 4.4 Keep JaCoCo 90/90 green

## 5. Gate

- [x] 5.1 `make fmt && make be-lint` green, `make be-test` green
