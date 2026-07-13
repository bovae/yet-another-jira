# fix-improvements-backlog — Tasks

## 1. Backend core — error truthfulness & data (F-38, F-39, F-40, F-53)

- [x] 1.1 Catch `DataIntegrityViolationException` around `saveAndFlush` in `TicketService.create`/`update`, `EpicService.create`, `CommentService.add` → re-throw the pre-check's `NotFoundException`; unit tests per path (D1)
- [x] 1.2 Add `@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` → 404 problem detail in `GlobalExceptionHandler`; MockMvc test (D2)
- [x] 1.3 Add `createdByEmail` to `TicketResponse` and `authorEmail` to `CommentResponse`, resolved via batched `findAllById` over distinct author ids in the services; unit tests assert the email lands and lists resolve without per-row queries (D3)
- [x] 1.4 Verify each service 400s on null title/body/description, then drop `@NotBlank`/`@Size` duplicates from ticket/epic/comment create+update DTOs (keep `@NotNull` on UUIDs); update controller tests to expect the services' messages (D4)

## 2. Backend core — determinism & hygiene (F-54, F-55, F-56, F-57, F-58, F-59)

- [x] 2.1 Single clock source for `created_at`/`modified_at` on all four entities — confirm the Hibernate 6 DB-sourced annotation against docs, apply, and assert `modified_at == created_at` on fresh rows (D5)
- [x] 2.2 Append `, id` tie-breaks to the comments (`createdAt ASC`) and board (`modifiedAt DESC`) ORDER BYs (D6)
- [x] 2.3 Pass `Sort.by("createdAt").and(Sort.by("id"))` at the teams/epics/tickets list repository calls (D6)
- [x] 2.4 Fold both sides of the board title search in SQL (`LOWER(t.title) LIKE LOWER(:pattern)`); drop the Java `toLowerCase`; keep escaping (D7)
- [x] 2.5 Set `server.forward-headers-strategy: framework` in `application.yml`; `proxy_set_header Host $http_host` in `fe/nginx.conf` (D8)
- [x] 2.6 Delete `CommentRepository.existsByTicketId`, `TicketState.position()`, and their tests (D9)

## 3. Backend auth (F-21, F-22, F-23, F-24, F-26, F-60, F-61, F-62)

- [x] 3.1 Active-user check in `JwtAuthenticationFilter` (exists + `deletedAt == null`, else 401); simplify the now-redundant `/me` check; unit tests for deleted/missing user (D10)
- [x] 3.2 Delete the login rate-limit key on successful authentication in `LoginService`; unit test (D11)
- [x] 3.3 `VerificationEmailDispatcher` catches `RuntimeException`, logs class + sanitized message (no recipient); validate `link-base-url` parses in the properties compact constructor; tests (D12)
- [x] 3.4 `LogoutService`: expired-token parse failure → 204 no-op; unit test (D13)
- [x] 3.5 Split Valkey command timeout (new property, 1s default) from the 5s startup timeout in `ValkeyConfig`; rename constant (D14)
- [x] 3.6 Set `CallerRunsPolicy` on the async email executor in `AsyncConfig` (D15)
- [x] 3.7 Fix `FixedWindowRateLimiter.retryAfterSeconds`: re-arm only on TTL `-1`; map `0` → 1s, `-2` → 0s; unit tests for all three branches (D16)
- [x] 3.8 Add `@ExceptionHandler` for `DataAccessResourceFailureException`/`QueryTimeoutException` → same 503 problem detail as the JWT filter; test (D17)

## 4. Backend tests (F-27, F-28, F-44, F-45, F-46, F-47, F-76, F-77, F-78, F-79, F-80, F-81)

- [x] 4.1 Extract scenario-scoped BDD `ApiClient` (JDK request factory, shared `exchange`/`extractId`/`extractInstant`); migrate all five step classes (D28)
- [x] 4.2 Move Valkey rate-limit key purge to a tag-independent global `@After` hook (D29)
- [x] 4.3 Fix BDD teams cleanup order: tickets before epics (F-27)
- [x] 4.4 Replace `EpicSteps.soleUserId`'s `findAll().get(0)` with `findByEmail` (F-76)
- [x] 4.5 Add per-feature `Background` for the register/verify/login preamble (board, comments, tickets, epics); collapse the five unauthenticated-401 scenarios to one `Scenario Outline` (D30)
- [x] 4.6 Add BDD scenarios: teams duplicate-name 409 (create/rename), blank-name 400, signup duplicate-email 409 (F-28)
- [x] 4.7 Add `update_shouldSetEpic_whenEpicBelongsToTeam` captor test in `TicketServiceTest` (F-45)
- [x] 4.8 Add MockMvc malformed-UUID case (path variable + query param) asserting 400 problem+json (F-46)
- [x] 4.9 Add PUT/DELETE-on-comment → 405 pins in `CommentControllerTest` (F-80)
- [x] 4.10 Add a `_`-literal search pair to the board BDD scenario and an `assertSingleStatement` case for `findBoardTickets` (F-81)
- [x] 4.11 Suite hygiene: parametrize the `findByEmail` case-variant tests, delete the enum round-trip duplicates and dead `TestSecurityConfig` (F-79)
- [x] 4.12 Run `make be-lint` and `make be-test` green

## 5. Frontend behavior (F-41, F-42, F-43, F-35, F-63, F-64, F-65, F-66, F-67, F-68, F-69, F-70, F-71)

- [x] 5.1 `button.tsx` destructive variant: `text-white` → `text-destructive-foreground`; verify alert-dialog overlay matches dialog's fixed scrim (D19)
- [x] 5.2 `DeleteTicketDialog.onSuccess`: invalidate `['tickets']` + `['board']`, remove `['ticket', id]` (D20)
- [x] 5.3 Board move `onSettled`: invalidate `['board', teamId]` prefix, `['tickets']`, `['ticket', id]` (D20)
- [x] 5.4 Make the card title an inner link to `/tickets/{id}` (keyboard-openable, drag unchanged); fold the card count into the column's accessible name, drop the bare `aria-hidden` (D21, F-35)
- [x] 5.5 Sync the board search input when the URL `q` changes externally; clear `moveError` on board-key change (D22)
- [x] 5.6 `queryClient.clear()` on logout and on the unauthorized event (D23, F-66)
- [x] 5.7 `login()`: clear stored token in a catch before rethrowing when `fetchMe` fails (D23, F-67)
- [x] 5.8 Boot hydration `fetchMe` gets an `AbortController`, aborted in effect cleanup (D23, F-68)
- [x] 5.9 `TicketDetailsPage`: neutral placeholder while epics pending; "Unknown epic" only on resolved miss (D24)
- [x] 5.10 `/login` and `/signup` render `<Navigate to="/" replace />` when authenticated (D25)
- [x] 5.11 Trim guards on team/epic form submits, matching `TicketFormDialog` (D26)
- [x] 5.12 Render `authorEmail`/`createdByEmail` in `CommentThread` and `TicketDetailsPage`; drop the current-user-only special case; update API types (D3)

## 6. Frontend tests (F-36, F-48, F-49, F-72, F-73, F-74, F-75)

- [x] 6.1 Create `fe/src/test/helpers.tsx` (`jsonResponse`/`problemResponse`, QueryClient render wrapper, `team()`/`epic()`/`ticket()` builders); migrate the duplicated copies (D31)
- [x] 6.2 Set `unstubGlobals: true` in the vitest config (F-73)
- [x] 6.3 Restore BoardPage error-path tests: board error → retry → recover, teams error, retry-in-flight disabled state (F-36, F-49)
- [x] 6.4 Fix the debounce test to assert no intermediate-prefix calls (F-72)
- [x] 6.5 Add EpicsPage delete success (row removed on 204) and confirm-cancel tests (F-75)
- [x] 6.6 e2e `board-smoke`: `waitForResponse` on the tickets PATCH after `mouse.up()` before reloading (F-48)
- [x] 6.7 Unit tests for the new FE behaviors: cache invalidation on delete/move, auth-screen redirect, trim guards, login token cleanup, logout cache clear
- [x] 6.8 Run `make fe-lint`, `make fe-test`, and the e2e suite green

## 7. Infra & docs (F-29, F-30, F-31, F-34, F-50, F-51, F-52, F-82, F-83, F-84, F-85, F-87)

- [x] 7.1 `git restore --staged 'openspec/changes/add-*'` — drop the 36 stale pre-archive index entries; commit the `epics-catalog.md` edit deliberately (D38)
- [x] 7.2 Compose: `${VAR:-default}` interpolation for `YAJ_*` values; pin `axllent/mailpit:v1` + healthcheck + `be` `depends_on` (D34, D36)
- [x] 7.3 Add `YAJ_SMTP_STARTTLS` knob → `mail.smtp.starttls.enable` in `MailConfig` + properties + compose/README rows (D37)
- [x] 7.4 README sync: mailpit always-on (no `mail` profile), drop dead mock-board URL, current CORS default, `YAJ_SMTP_TIMEOUT` row, remove `make help` claim or add the target, JWT-secret local-only warning (D32, D36)
- [x] 7.5 CI: drop the four `--profile mail` flags; cache `~/.cache/ms-playwright` keyed on Playwright version; buildx + GHA layer cache for the e2e image builds (D35, D36)
- [x] 7.6 Pin semantic-release toolchain versions in `release.yml` (D34)
- [x] 7.7 `fe/.dockerignore`: add `*.md`/`*.iml` (keep `e2e/`); delete the dead root `.dockerignore` (D39)
- [x] 7.8 Verify clean `docker compose up --build` end-to-end still works

## 8. Wrap-up

- [x] 8.1 Tick all addressed findings in `requirements/improvements-catalog.md`; mark F-25, F-32, F-86 `[x] — wontfix:` with the D18/D27/D33 reasoning
- [x] 8.2 Full gate: `make be-lint be-test fe-lint fe-test` + e2e green
