# yet-another-jira — Improvements Catalog

Findings from whole-codebase reviews: bugs, gaps, flaky risks, and improvements
that fall outside the epic scope in `requirements/epics-catalog.md`. Companion to
that file — epics track *new* behavior, this file tracks *fixing what exists*.

**How this file works (read before extending):**

- Findings get stable sequential IDs (`F-01`, `F-02`, …). **Never renumber or
  reuse an ID** — new findings continue the sequence, even in later rounds.
- Each review round appends: a row to the [Review Rounds](#review-rounds) log,
  new checklist entries under the matching severity, and detail entries under
  the matching area section (tagged with its round).
- Tick a finding `[ ]` → `[x]` when fixed. If a finding is rejected, keep the
  line, mark it `[x]`, and append `— wontfix: <reason>` instead of deleting it.
- Severity: **High** = broken behavior or a flake that will bite soon ·
  **Medium** = real defect/gap with a workaround or narrow blast radius ·
  **Low** = hardening, hygiene, decide-and-document items.
- Area tags: `be-core`, `be-auth`, `be-test`, `fe`, `infra` (compose/CI/Makefile/README).

---

## Review Rounds

| Round | Date | Scope | Baseline | Findings |
|-------|------|-------|----------|----------|
| R1 | 2026-07-11 | Full codebase (be main/test/bdd/it, fe src/e2e, compose, CI, Makefile, README) | `develop` @ `b94ef28` | F-01 – F-36 |
| R2 | 2026-07-12 | Full codebase incl. everything landed since R1 (tickets/epics/comments/board BE+FE, all FE screens), plus a live UI walkthrough (`docker compose up` + Playwright: signup→verify→login→teams→epics→tickets→board→drag→comments→delete guards→logout) and the in-flight openspec change | `develop` @ `fc9ee2c` | F-37 – F-87 |

**R2 notes on existing findings:**

- **F-21** blast radius grew: soft-deleted users' still-valid tokens now pass
  `CurrentUserProvider.requireCurrentUserId()` in `TicketService.create` / `CommentService.add`,
  i.e. deleted accounts can author tickets and comments. Same root cause — bump priority
  when picking it up.
- **F-33** resolved by usage (ticked): `@dnd-kit/core` and `@testing-library/user-event`
  are both imported now.
- The board DnD **drag-visibility bug** (dragged card clipped/invisible the moment the pointer
  leaves the source column) was reproduced live in R2 but is **not** cataloged here — it is
  already tracked with a full proposal in `openspec/changes/fix-dnd-overlay-add-seed-data/`.
  Drop/persist itself works (verified: card lands, PATCH persists, survives refresh).

---

## Progress Checklist

### High

- [x] **F-01** (be-core) — `modified_at` never persists on update: `@Generated` mapping drops manual bumps; BDD masks it by asserting the response body
- [x] **F-02** (infra) — `be` service publishes no host port: README URLs dead, Vite dev proxy has no backend to reach
- [x] **F-03** (fe) — mock board endpoint now requires auth: stock `docker compose up` renders a permanent board error
- [x] **F-04** (be-test) — BDD verify-user step picks `findAll().get(size-1)` with no ordering + skeleton feature never cleans up → cross-feature flake
- [x] **F-05** (be-test) — login failure paths (401 bad password, 403 unverified, 429 rate limit) have no BDD coverage through the real filter chain
- [ ] **F-37** (infra) — in-flight seed-data change plans `YAJ_LIQUIBASE_CONTEXTS=local` as the compose **default**: QA's clean-checkout `docker compose up --build` would start with preloaded data, violating §9 and a graded DoD item (proposal-stage catch)

### Medium

- [x] **F-06** (be-core) — TeamService uniqueness + delete-guard races surface as 500 instead of 409 (SignupService already has the catch pattern)
- [x] **F-07** (be-core) — team name length unbounded: >~2.7 KB name breaks the citext unique index → raw 500
- [x] **F-08** (be-auth) — resend-verification timing oracle: synchronous SMTP send defeats the uniform 202 response
- [x] **F-09** (be-auth) — Valkey outage during JWT denylist check escapes the filter chain as a container error / misleading 401
- [x] **F-10** (be-auth) — `FixedWindowRateLimiter` INCR/EXPIRE not atomic: crash between them leaves a TTL-less counter
- [x] **F-11** (infra) — default CORS origins missing `http://localhost:5173` (Vite dev server)
- [x] **F-12** (infra) — CI triggers on `pull_request` only: merge commits to `develop`/`main` are never built or tested (**not needed for now**)
- [x] **F-13** (fe) — e2e `auth.setup.ts` uses a fixed 2s sleep + unfiltered "newest Mailpit message" → flake source
- [x] **F-14** (fe) — `apiFetch` clobbers caller-supplied `AbortSignal`; TanStack Query cancellation never aborts in-flight requests
- [x] **F-15** (infra) — `make fe-lint` weaker than CI (no Prettier check, no typecheck): green locally can still fail CI
- [x] **F-16** (be-test) — JaCoCo excludes `**/config/**`, hiding logic-bearing classes (`ValkeyStartupValidator`, validating properties) from the 90/90 gate
- [x] **F-17** (be-test) — `TokenDenylistIntegrationTest` waits on real Redis TTL with `Thread.sleep(1500)` → slow + timing-sensitive
- [x] **F-18** (be-test) — `VerificationSteps` email-count checks use fixed sleeps and `<=` assertions: negative check can false-pass, missing emails undetected
- [x] **F-19** (be-test) — misleading/duplicated tests: lambda-testing `CurrentUserProviderTest`, rate-limiter wrapper re-tests, reflection tests of trivial constructors
- [x] **F-20** (be-test) — missing unit branch coverage: limiter null-count fail-open, `JwtService.parse` branches, `LogoutService` non-positive TTL, `RawTokenGenerator`
- [ ] **F-38** (be-core) — insert-side FK races in tickets/epics/comments surface as raw 500 (the F-06 race class — fixed for teams delete-side only)
- [ ] **F-39** (be-core) — row-vanished write races (`ObjectOptimisticLockingFailureException`) unmapped → 500 on concurrent update/delete
- [ ] **F-40** (be-core) — comment author / ticket `createdBy` exposed only as raw UUID; UI renders other users' authorship as a bare UUID (confirmed live) — DoD acceptance risk
- [ ] **F-41** (fe) — every Dialog/AlertDialog opens with **no modal scrim** and destructive buttons render ~2.4:1 contrast: `bg-black/50` / `text-white` are stock-palette classes deleted by the `--color-*: initial` reset (confirmed visually)
- [ ] **F-42** (fe) — ticket delete invalidates only `['tickets']`: board (30s staleTime) keeps showing the deleted card, clicking it lands on "Ticket not found" (confirmed live via SPA nav)
- [ ] **F-43** (fe) — board move mutation invalidates only the exact board key: sibling filter-combo caches, `['tickets']` and `['ticket', id]` serve the pre-move state for up to 30s
- [ ] **F-44** (be-test) — BDD login rate-limit Valkey keys cleaned only by `@After("@auth")`: board.feature is at 4/5 logins for its fixed email — next scenario added fails with an unexplained 429
- [ ] **F-45** (be-test) — no test in any layer pins that ticket UPDATE with an `epicId` keeps/sets the epic; a regression that always clears it passes the whole suite
- [ ] **F-46** (be-test) — malformed-UUID inputs (path + query params) untested at every layer; the 400 problem+json contract rests on unpinned framework behavior
- [ ] **F-47** (be-test) — ~60-line HTTP helper block copy-pasted into five BDD step classes, already diverged (only 2 of 5 are PATCH-capable)
- [ ] **F-48** (fe) — board-smoke e2e reloads before the persistence PATCH resolves: intermittent failure masked by CI retries, on the flagship drag-persist flow
- [ ] **F-49** (fe) — BoardPage error/retry unit coverage was dropped in the E15 rewrite (regression of the R1 baseline noted in F-36)
- [ ] **F-50** (infra) — half-committed openspec archive: 36 stale pre-archive paths staged as adds; a plain `git commit` resurrects all old change dirs next to their archive copies
- [ ] **F-51** (infra) — mailpit profile state inverted since R1: compose always starts it, but README still documents the `mail` profile and CI passes `--profile mail` four times (silent no-op)
- [ ] **F-52** (infra) — README drift: dead mock-board URL, stale CORS default, `YAJ_SMTP_TIMEOUT` missing from the env table

### Low

- [ ] **F-21** (be-auth) — soft-deleting a user does not revoke outstanding JWTs; `deletedAt` check lives only in `/me`
- [ ] **F-22** (be-auth) — successful logins consume the login rate-limit window; 6th legitimate login in 15 min is locked out
- [ ] **F-23** (be-auth) — email dispatcher catches only `MailException` (other RuntimeExceptions 500 after commit); logged `MailException` may embed recipient PII
- [ ] **F-24** (be-auth) — logout with an already-expired token returns 401 instead of idempotent 204
- [ ] **F-25** (be-auth) — signup 409 is a user-enumeration oracle while login/resend are deliberately uniform — decide and document
- [ ] **F-26** (be-auth) — Valkey `STARTUP_TIMEOUT` (5s) is actually the permanent Lettuce command timeout: brown-out pins servlet threads 5s per call
- [ ] **F-27** (be-test) — BDD teams cleanup deletes epics before tickets: latent FK failure once a scenario creates a ticket on an epic
- [ ] **F-28** (be-test) — BDD scenario gaps: teams duplicate-name 409 / blank-name 400, signup duplicate-email 409 (unit-only today)
- [ ] **F-29** (infra) — compose env values are hardcoded literals (no `${VAR:-default}`) though README presents them as overridable; mailpit is behind the `mail` profile but BE always points at it, so default-stack email fails at runtime
- [ ] **F-30** (infra) — README drift: `make help` target doesn't exist; `fe-lint` described as "lint and format checks" but runs ESLint only
- [ ] **F-31** (infra) — `YAJ_JWT_SECRET` in compose falls back to a public default: forgotten env var silently signs tokens with a known key
- [ ] **F-32** (fe) — JWT stored in `localStorage` (XSS-readable) — acceptable now; revisit pattern in the FE auth epics
- [x] **F-33** (fe) — unused declared dependencies: `@dnd-kit/core` (until the DnD epic), `@testing-library/user-event` — resolved by usage (R2): both imported now
- [ ] **F-34** (infra) — CI re-downloads Playwright browsers every e2e run; cache `~/.cache/ms-playwright`
- [ ] **F-35** (fe) — Column card-count badge is `aria-hidden` with no screen-reader alternative
- [ ] **F-36** (fe) — BoardPage retry-in-flight state untested; no e2e coverage of the board error state
- [ ] **F-53** (be-core) — DTO bean validation double-covers service checks pre-trim and yields Spring's generic "Invalid request content." instead of the services' meaningful messages
- [ ] **F-54** (be-core) — `created_at` (DB clock) and `modified_at` (JVM clock) come from two clock sources: fresh rows have `modified_at != created_at`, possibly earlier
- [ ] **F-55** (be-core) — ordering queries lack tie-breaks: comments `createdAt ASC` and board `modifiedAt DESC` can reshuffle on equal timestamps
- [ ] **F-56** (be-core) — teams/epics/tickets list endpoints have no ORDER BY: management screens reshuffle after edits (heap order)
- [ ] **F-57** (be-core) — board title search folds case two ways (SQL `LOWER()` vs Java `toLowerCase(ROOT)`): non-ASCII titles can silently fail to match
- [ ] **F-58** (be-core) — `Location` headers wrong behind the nginx proxy (no `forward-headers-strategy`, `Host` drops the port) — latent, nothing consumes them yet
- [ ] **F-59** (be-core) — dead production surface: `CommentRepository.existsByTicketId`, `TicketState.position` (tests are the only consumers)
- [ ] **F-60** (be-auth) — verification-email executor uses default `AbortPolicy`: SMTP brown-out + signup burst → `TaskRejectedException` after commit → 500 for a persisted signup
- [ ] **F-61** (be-auth) — rate-limiter `retryAfterSeconds` heal path re-arms a full window when TTL reads 0/-2: user due to unblock in <1s gets another 15-minute lockout
- [ ] **F-62** (be-auth) — Valkey outage posture inconsistent: JWT filter returns deliberate 503, rate limiters escape as generic 500 during the same outage
- [ ] **F-63** (fe) — board card not keyboard-openable: Enter/Space feeds the dnd-kit KeyboardSensor, `onClick` never fires; keyboard/SR users must detour via /tickets
- [ ] **F-64** (fe) — board search input seeds from URL once: back/forward or nav-link changes to `q` desync the input from results
- [ ] **F-65** (fe) — "Could not move the ticket" banner persists across team/filter switches until the next drag
- [ ] **F-66** (fe) — logout doesn't clear the TanStack Query cache: next account on the same browser is served the previous session's cached data
- [ ] **F-67** (fe) — `login()` leaves a valid token in localStorage when the follow-up `fetchMe` fails non-401: form shows error, refresh silently logs in
- [ ] **F-68** (fe) — boot hydration `fetchMe()` ignores abort: StrictMode double-mount fires two `/auth/me` calls
- [ ] **F-69** (fe) — ticket details shows "Unknown epic" while the epics query is still pending — misleading text presented as data
- [ ] **F-70** (fe) — authenticated user visiting /login or /signup gets the form instead of a redirect to /
- [ ] **F-71** (fe) — whitespace-only names pass the client guards on team/epic forms (no trim); TicketFormDialog trims — inconsistent
- [ ] **F-72** (fe) — `filter_shouldSendSearchTermAsQParam_afterDebounce` doesn't test debouncing: passes even if the debounce is deleted
- [ ] **F-73** (fe) — API test cleanup is a no-op for fetch stubs: `vi.restoreAllMocks()` doesn't undo `vi.stubGlobal` (needs `unstubGlobals: true`)
- [ ] **F-74** (fe) — `jsonResponse`/`problemResponse`, render wrappers and fixture builders copy-pasted across 6+ test files
- [ ] **F-75** (fe) — EpicsPage delete has no success-path or confirm-cancel test (TeamsPage covers the analogous paths; separate code)
- [ ] **F-76** (be-test) — `EpicSteps.soleUserId` uses `findAll().get(0)` — the F-04 pattern at a new site
- [ ] **F-77** (be-test) — identical 5-line register/verify/login preamble repeated per scenario (board.feature ×4) instead of `Background`
- [ ] **F-78** (be-test) — five structurally identical unauthenticated-401 scenarios across features; conventions say one Scenario Outline (slice test already parametrizes the matrix)
- [ ] **F-79** (be-test) — suite hygiene: `findByEmail` case-variant tests unparametrized, enum round-trip test duplicates `assertSame` case, `TestSecurityConfig` is dead code
- [ ] **F-80** (be-test) — comment immutability exists only by omission: nothing pins PUT/DELETE on comments → 405
- [ ] **F-81** (be-test) — LIKE-escape contract for `_`/`\` never reaches Postgres in any test; no statement-count guard on `findBoardTickets`
- [ ] **F-82** (infra) — `axllent/mailpit:latest` unpinned, no healthcheck, `be` doesn't depend on it
- [ ] **F-83** (infra) — release workflow `npm install -g semantic-release ...` unpinned — unreproducible releases in the one workflow with `contents: write`
- [ ] **F-84** (infra) — fe `.dockerignore` misses `*.md`/`*.iml` (doc edits bust the COPY layer); root `.dockerignore` is dead config
- [ ] **F-85** (infra) — CI e2e job rebuilds both images from scratch every run (no buildx/GHA layer cache)
- [ ] **F-86** (infra) — image hardening deferred: full JDK runtime (no jlink), fe nginx master runs as root
- [ ] **F-87** (infra) — no SMTP STARTTLS/SSL knob: if `relay1.dataart.com` requires STARTTLS it can't be configured, and SMTP credentials would go plaintext

---

## Findings Detail

### Backend — core (`be-core`)

#### F-01 — `modified_at` never persists on update *(High · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/domain/model/Team.java:38`, `User.java:50` (same mapping on `Epic`, `Ticket`), `be/src/main/java/com/bovae/yaj/teams/TeamService.java:68`, `be/src/bdd/java/com/bovae/yaj/bdd/TeamSteps.java:132`
**Issue:** `modifiedAt` is mapped `@Generated` (non-insertable **and non-updatable**), so the manual `setModifiedAt` in `TeamService.rename` is silently dropped from the UPDATE (Hibernate `HHH000502`): DB `modified_at` never advances, while the response returns the un-persisted in-memory value. Same root cause already live on `User` (`EmailVerificationService` sets `emailVerified`), latent on `Epic`/`Ticket`. The BDD step asserts the rename **response body**, so the suite passes despite the bug. `openspec/changes/add-teams-crud/design.md` decision 5 already prescribes `@UpdateTimestamp` — code never followed.
**Fix:** replace `@Generated`/`@ColumnDefault` with `@UpdateTimestamp` on all four entities; delete the manual bump + unused `Clock` in `TeamService`; change the BDD step to re-GET the team and assert the fresh read.

#### F-06 — TeamService race conditions surface as 500 *(Medium · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/teams/TeamService.java:48-77`
**Issue:** create/rename do `existsByName` then `saveAndFlush`, delete does `exists` checks then delete, with no `DataIntegrityViolationException` handling — concurrent duplicate create or epic/ticket insert during delete → raw 500. `design.md` decision 2 accepts this, but `SignupService.java:50-58` already translates the identical race to 409, so consistency is one small catch block. No test pins either behavior.
**Fix:** mirror the `SignupService` catch-and-translate to `ConflictException` in `create`/`rename`/`delete`; add a unit test mirroring `signup_shouldTranslateToConflict_whenUniqueConstraintRace`.

#### F-07 — team name length unbounded *(Medium · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/web/dto/TeamRequest.java:6`
**Issue:** only `@NotBlank`; `teams.name` is unbounded citext, and names over ~2.7 KB fail the unique btree index insert with a raw `DataIntegrityViolationException` → 500.
**Fix:** `@Size(max = 100)` on the DTO plus the authoritative length check in `TeamService.normalize`.

#### F-38 — insert-side FK races surface as 500 *(Medium · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/tickets/TicketService.java:71,95`, `epics/EpicService.java:60`, `comments/CommentService.java:47`
**Issue:** all four write paths do an exists/find check then `saveAndFlush` with no `DataIntegrityViolationException` translation. A concurrent team/epic/ticket delete between the check and the flush hits the FK → unhandled 500. This is exactly the F-06 race class: the catch-and-translate fix landed on the teams/epics **delete** side but was never mirrored to the **insert** side of the newer slices — one interleaving gives a clean 409, the mirror interleaving gives a 500.
**Fix:** catch `DataIntegrityViolationException` around these flushes and re-throw the `NotFoundException` the pre-check would have thrown.

#### F-39 — row-vanished write races unmapped *(Medium · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/tickets/TicketService.java:95-108`, `epics/EpicService.java:79`, `teams/TeamService.java:86`, `web/error/GlobalExceptionHandler.java`
**Issue:** `require(id)` loads the entity, a concurrent request deletes it, the flush's UPDATE/DELETE affects 0 rows → `ObjectOptimisticLockingFailureException`, which no handler maps → 500. Realistic on a multi-user board: a drag PATCH racing another user's ticket delete, or a double-fired delete confirmation.
**Fix:** one `@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` → 404 problem detail (the row is gone; 404 is the truthful answer everywhere).

#### F-40 — other users' authorship renders as a raw UUID *(Medium · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/web/dto/CommentResponse.java`, `TicketResponse.java`; `fe/src/components/tickets/CommentThread.tsx:66`
**Issue:** the API exposes `authorId`/`createdBy` only as UUIDs with no user-resolution endpoint (`/me` is self-only), so the UI shows a bare UUID for every author except the current user (confirmed live: second user's comment rendered as `687cc4e5-…`). Documented as decision D6, but DoD says "a user can add comments and **see their author**" — a QA reviewer will read this as broken.
**Fix:** embed `authorEmail` in `CommentResponse`/`TicketResponse` (one join), or add a minimal user lookup; if the UUID display stays, document it as accepted.

#### F-53 — bean validation shadows the service contract *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/web/dto/TicketCreateRequest.java:15-16` (same on TicketUpdate/EpicCreate/EpicUpdate/CommentCreate requests)
**Issue:** `@NotBlank`/`@Size` fire before the services, measure **pre-trim** length (a 195-char title with 10 leading spaces is spec-legal but 400s), and produce Spring's generic "Invalid request content." — the services' meaningful messages (`TITLE_REQUIRED_MSG` etc.) are unreachable over HTTP for those inputs, against §9.
**Fix:** drop the `@NotBlank`/`@Size` duplicates; keep `@NotNull` on UUIDs; let the services (which validate post-trim with good messages) be the single authority.

#### F-54 — two clock sources per row *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/domain/model/Ticket.java:54-61` (same on Epic, Team, User)
**Issue:** `created_at` is DB-generated (`now()`) while `modified_at` is `@UpdateTimestamp` (JVM clock): fresh rows have `modified_at != created_at` (with container clock skew possibly earlier), so "never modified" can't be detected and cross-source comparisons are unreliable.
**Fix:** one source for both — `@CurrentTimestamp(source = DB)` on both, or `@CreationTimestamp` + `@UpdateTimestamp`.

#### F-55 — ordering queries lack tie-breaks *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/domain/repository/CommentRepository.java:14`, `TicketRepository.java:39`
**Issue:** comments order by `createdAt ASC`, board by `modifiedAt DESC`, both without a secondary key. Postgres `now()` is per-transaction, so equal timestamps are possible — tied rows can flip order between reads (comment thread reorders on refresh; board ordering assertions in tests presume distinct timestamps).
**Fix:** append `, id` to both ORDER BYs.

#### F-56 — list endpoints return heap order *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/teams/TeamService.java:40`, `epics/EpicService.java:43`, `tickets/TicketService.java:47`
**Issue:** `findAll`/`findByTeamId` with no ORDER BY — row order changes when tuples move after updates, so management screens and the ticket list visibly reshuffle between refreshes.
**Fix:** pass `Sort.by("createdAt")` (or name/title) at the repository calls.

#### F-57 — search case-folding split across two engines *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/board/BoardService.java:73-76`, `domain/repository/TicketRepository.java:38`
**Issue:** the column is folded by Postgres `LOWER()` (collation-dependent), the pattern by Java `toLowerCase(Locale.ROOT)` (full Unicode). For non-ASCII titles under a C/POSIX collation the folds disagree and "case-insensitive substring search" silently fails.
**Fix:** fold both sides in the DB (`LIKE LOWER(...)` on the pattern) and drop the Java lower-casing.

#### F-58 — Location headers wrong behind the proxy *(Low · R2)*
**Where:** `be/src/main/resources/application.yml` (no `server.forward-headers-strategy`), `fe/nginx.conf:36`
**Issue:** nginx forwards `Host: $host` (port dropped) and the app ignores `X-Forwarded-*`, so the four POST handlers emit `Location: http://localhost/api/v1/...` through the proxy. Latent — nothing consumes Location today.
**Fix:** `server.forward-headers-strategy: framework` + `proxy_set_header Host $http_host`.

#### F-59 — dead production surface *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/domain/repository/CommentRepository.java:10`, `domain/enums/TicketState.java:22,30-32`
**Issue:** `existsByTicketId` is used only by its own tests; `TicketState.position()` only by a test asserting it equals `ordinal()+1` (BoardService uses declaration order). Unused surface that tests then "cover".
**Fix:** delete both; declaration order already encodes workflow order.

### Backend — auth (`be-auth`)

#### F-08 — resend-verification timing oracle *(Medium · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/verification/VerificationEmailDispatcher.java:17-24` (see comment in `MailConfig.java:26`)
**Issue:** the AFTER_COMMIT listener sends SMTP synchronously on the request thread, so resend returns only after the SMTP round-trip when an unverified account exists but instantly otherwise — a response-time oracle that defeats the deliberately uniform 202 body.
**Fix:** make the listener `@Async` with an executor so latency is independent of account existence.

#### F-09 — Valkey outage escapes the JWT filter *(Medium · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/jwt/JwtAuthenticationFilter.java:42`, `JwtService.java:53`
**Issue:** the denylist lookup is outside the filter's `catch (UnauthorizedException)`, so a Valkey outage throws `RedisConnectionFailureException` past `GlobalExceptionHandler`; fail-closed (good) but the client gets a container error that can surface as a misleading 401 (secured ERROR dispatch).
**Fix:** catch `DataAccessException` around the denylist check and write a deliberate 503 ProblemDetail, staying fail-closed.

#### F-10 — rate limiter INCR/EXPIRE not atomic *(Medium · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/support/FixedWindowRateLimiter.java:40-44`
**Issue:** a crash/timeout between `INCR` and `EXPIRE` leaves a TTL-less counter that only self-heals after the limit is exceeded — one extra full lockout window for the affected key.
**Fix:** single Lua script, or unconditional `EXPIRE ... NX` on every call.

#### F-21 — soft-delete doesn't revoke JWTs *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/me/CurrentUserService.java:24`
**Issue:** the `deletedAt` check lives only in `/me`; a soft-deleted user's token stays valid up to 1h, and future endpoints using `CurrentUserProvider.requireCurrentUserId()` directly will serve deleted accounts.
**Fix:** denylist the user's tokens on deletion or centralize an is-active check in the filter/provider.

#### F-22 — successful logins consume the rate-limit window *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/login/LoginService.java:56`
**Issue:** the 5-per-15m counter never resets on success, so a legitimate 6th login within the window is locked out (per-email keying also lets an attacker lock a victim out).
**Fix:** delete the rate-limit key on successful authentication.

#### F-23 — email dispatcher error handling too narrow + PII in logs *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/verification/VerificationEmailDispatcher.java:21-22`
**Issue:** only `MailException` is caught — e.g. `UriComponentsBuilder` failing on a malformed configured `link-base-url` propagates after commit, turning successful signup into a 500 (retry then hits 409). Also the logged `MailException` stack commonly embeds the recipient address, undermining the deliberately recipient-free warn message.
**Fix:** catch `RuntimeException`; validate `linkBaseUrl` parses at startup; log exception class + sanitized message.

#### F-24 — logout not idempotent for expired tokens *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/logout/LogoutService.java:23-24`
**Issue:** logout with an already-expired token throws 401 from `parseForRevocation`; the token is already unusable, so this should succeed.
**Fix:** treat expired-token parse failure as a 204 no-op.

#### F-25 — signup user-enumeration oracle *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/auth/signup/SignupService.java:35-37`
**Issue:** signup's 409 reveals account existence while login and resend were carefully made uniform — inconsistent posture.
**Fix:** decide: uniform 202 + "account exists" email, or document the 409 as a deliberate UX choice.

#### F-26 — Valkey command timeout misnamed and long *(Low · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/config/ValkeyConfig.java:17,27`
**Issue:** `STARTUP_TIMEOUT` (5s) is the permanent Lettuce command timeout for every runtime call; during a brown-out each auth request pins a servlet thread up to 5s.
**Fix:** rename, and set a shorter runtime command timeout (~1s).

#### F-60 — email executor rejects after commit *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/config/AsyncConfig.java:19-25`
**Issue:** the F-08 fix's executor uses the default `AbortPolicy` (max 2 threads, queue 100, each send blocking up to the 5s SMTP timeout). During an SMTP brown-out with a signup/resend burst, `@Async` submission throws `TaskRejectedException` on the request thread **after** the transaction committed → signup returns 500 though the account persisted (retry then hits 409), and resend's 202-vs-500 divergence re-opens a sliver of the F-08 oracle under saturation.
**Fix:** `CallerRunsPolicy` (degrades to sync only under overload) or discard-with-warn.

#### F-61 — rate-limiter heal path re-locks a healed user *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/support/FixedWindowRateLimiter.java:66-73`
**Issue:** `retryAfterSeconds` re-arms a full window on any non-positive TTL, but Redis `TTL` returns `0` in the last sub-second of a window and `-2` if the key just expired — both get a fresh full-window expiry/`Retry-After`, so a user due to unblock in under a second is locked out another 15 minutes.
**Fix:** re-arm only on `-1` (key without TTL — the actual heal case); map `0`/`-2` to 1s/0s.

#### F-62 — Valkey outage: 503 on one path, 500 on the other *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/web/error/GlobalExceptionHandler.java:70` vs `auth/jwt/JwtAuthenticationFilter.java:44`
**Issue:** the F-09 fix gives token-bearing requests a deliberate 503, but the same outage inside the login/resend rate limiters escapes to the catch-all as a generic 500 — one outage, two contradictory client answers, with the 500 miscategorizing transient infra failure.
**Fix:** `@ExceptionHandler(DataAccessResourceFailureException.class)` (+ `QueryTimeoutException`) returning the same 503 problem detail.

### Backend — tests (`be-test`)

#### F-04 — BDD cross-feature user leak *(High · R1)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/AuthSessionSteps.java:53-60`, `SkeletonSteps.java` (no `@After("@skeleton")`)
**Issue:** "the user's email is verified" picks `userRepository.findAll().get(size-1)` with no ORDER BY, and the skeleton feature leaks `skeleton@example.com` into later features — with 2+ rows the "last" row is undefined and teams.feature can verify the wrong user → 403 on login.
**Fix:** look up by email (as `SkeletonSteps.aRegisteredAndVerifiedUserWithEmailAndPassword` already does) and add a `@skeleton` cleanup hook.

#### F-05 — login failure paths lack BDD coverage *(High · R1)*
**Where:** `be/src/bdd/resources/features/auth-session.feature`
**Issue:** wrong password → uniform 401, unverified → 403, rate limit → 429 + `Retry-After` are unit-only; nothing exercises them through the real filter chain + exception handler.
**Fix:** one `Scenario Outline` for 401/403 plus one 429 scenario.

#### F-16 — JaCoCo exclusion hides logic-bearing config *(Medium · R1)*
**Where:** `be/pom.xml:566-571`
**Issue:** `**/config/**` is excluded from the 90/90 gate, hiding `ValkeyStartupValidator` (untested failure branch), `CorsConfig`, and the validating compact constructors in `config/properties/*`.
**Fix:** narrow the exclusion to pure `@Bean` wiring classes; keep `config/properties/**` + `ValkeyStartupValidator` measured; add a test for the validator's failure branch.

#### F-17 — real-TTL sleep in denylist IT *(Medium · R1)*
**Where:** `be/src/it/java/com/bovae/yaj/auth/jwt/TokenDenylistIntegrationTest.java:78-88`
**Issue:** `Thread.sleep(1_500)` waits for a real Redis 1s TTL — slow and timing-sensitive.
**Fix:** poll with a deadline (Awaitility-style), or drop the case — TTL expiry is Redis behavior, not ours.

#### F-18 — verification email assertions can false-pass *(Medium · R1)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/VerificationSteps.java:93-111`
**Issue:** the negative check sleeps 500 ms then asserts zero messages (slow async dispatch false-passes); the positive check asserts `count <= limit`, which can't detect legitimately missing emails.
**Fix:** GreenMail `waitForIncomingEmail(timeout, count)` for the exact positive count; keep a short bounded wait for the negative.

#### F-19 — misleading and duplicated tests *(Medium · R1)*
**Where:** `be/src/test/java/com/bovae/yaj/security/CurrentUserProviderTest.java`, `auth/login/LoginRateLimiterTest.java`, `auth/verification/ResendRateLimiterTest.java`, `error/DomainExceptionTest.java`, `web/error/ProblemDetailFactoryTest.java:121-127`
**Issue:** `CurrentUserProviderTest` exercises lambdas defined inside the test (pure misleading coverage); both rate-limiter wrapper tests re-test `FixedWindowRateLimiter` internals through Redis mocks; `DomainExceptionTest` reflection-instantiates trivial constructors (and misses three exception types anyway); the private-constructor guard test asserts an implementation detail.
**Fix:** delete `CurrentUserProviderTest`, `DomainExceptionTest`, the private-ctor test; shrink each wrapper test to one wiring assertion (key prefix, limit, message).

#### F-20 — missing unit branch coverage *(Medium · R1)*
**Where:** `FixedWindowRateLimiter.java:40-48`, `JwtService.java:76-87`, `LogoutService.java:26-28`, `RawTokenGenerator.java`
**Issue:** untested branches: limiter `count == null` (silently fails open — pin that decision); `JwtService.parse` non-UUID `sub`, missing-`iat` fallback, `exp == now` boundary; `LogoutService` non-positive TTL skips revoke; `RawTokenGenerator` (32 bytes, URL-safe base64, distinctness) is mocked everywhere and never tested.
**Fix:** add the cases to existing suites (`@MethodSource` extension for JwtService).

#### F-27 — BDD cleanup order violates FK *(Low · R1)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/TeamSteps.java:63-68`
**Issue:** cleanup deletes epics before tickets, but `tickets.epic_id` is `ON DELETE RESTRICT` — the hook fails as soon as a scenario creates a ticket on an epic.
**Fix:** delete tickets first.

#### F-28 — BDD scenario gaps on error contracts *(Low · R1)*
**Where:** `be/src/bdd/resources/features/teams.feature`, `signup.feature`
**Issue:** teams duplicate-name 409 (create/rename), blank-name 400, delete-guard tickets branch, and signup duplicate-email 409 are unit-only.
**Fix:** add the 409/400 steps when E5 closes; one extra When/Then on the signup scenario.

#### F-44 — login rate-limit keys leak across BDD features *(Medium · R2)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/SignupSteps.java:63-67`, `be/src/bdd/resources/features/board.feature` (4 logins as `board@example.com`)
**Issue:** Valkey `auth:login:rl:*` keys are purged only by the `@After("@auth")` hook; non-auth features log in repeatedly with one fixed email and never clean them. With F-22 open (successful logins consume the window, limit 5/15min), board.feature already sits at 4/5 — the next scenario added to that feature fails with an unexplained 429 at login. Classic shared-state time bomb.
**Fix:** move the Valkey key cleanup into a tag-independent global `@After` hook, or log in once per feature and share the token.

#### F-45 — update-keeps-epic is a regression blind spot *(Medium · R2)*
**Where:** `be/src/test/java/com/bovae/yaj/tickets/TicketServiceTest.java:200-251`; no BDD PUT carries an epic either
**Issue:** unit tests cover clear-on-null and reject-wrong-team, but nothing anywhere proves an UPDATE carrying an `epicId` keeps/sets it. A regression that unconditionally clears the epic on update passes the entire suite green.
**Fix:** add `update_shouldSetEpic_whenEpicBelongsToTeam` (mirror of the create-side captor test).

#### F-46 — malformed-UUID 400 contract unpinned *(Medium · R2)*
**Where:** `be/src/test/java/com/bovae/yaj/web/error/GlobalExceptionHandlerTest.java` (no type-mismatch case)
**Issue:** `GET /api/v1/tickets/not-a-uuid`, `?epicId=garbage`, `?teamId=garbage` are untested at every layer; the 400 problem+json answer rests on `ResponseEntityExceptionHandler`'s `TypeMismatchException` path plus the enrichment override — a regression to 500 lands silently.
**Fix:** one MockMvc case with a non-UUID path variable asserting 400 + problem+json members.

#### F-47 — five diverging copies of the BDD HTTP client *(Medium · R2)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/TicketSteps.java:251-299`, `BoardSteps.java:287-329`, `CommentSteps.java:158-206`, `EpicSteps.java:212-259`, `TeamSteps.java:161-209`
**Issue:** the `exchange`/`NoOpResponseErrorHandler`/`extractId`/`extractInstant` block (~60 lines) is copy-pasted five times and already diverging: only Ticket/Board steps build the PATCH-capable `JdkClientHttpRequestFactory` — a future PATCH in Epic/Team steps fails confusingly. Also rebuilds a `RestTemplate` per request.
**Fix:** one scenario-scoped `ApiClient` component next to `SharedScenarioState`; always the JDK factory.

#### F-76 — `soleUserId` repeats the F-04 pattern *(Low · R2)*
**Where:** `be/src/bdd/java/com/bovae/yaj/bdd/EpicSteps.java:194-198`
**Issue:** picks `userRepository.findAll().get(0)` with no ordering and asserts non-empty, not `size() == 1` — a user leaked from a prior feature silently makes the ticket author wrong.
**Fix:** `findByEmail("epics@example.com")` like `AuthSessionSteps` does.

#### F-77 — auth preamble duplicated instead of Background *(Low · R2)*
**Where:** `be/src/bdd/resources/features/board.feature:5-9,28-32,48-52,63-67` (also comments ×3, tickets ×2, epics ×2)
**Issue:** the identical 5-line register/verify/login/200/token preamble repeats in every authenticated scenario; test conventions mandate `Background` for shared Givens.
**Fix:** per-feature `Background`; the unauthenticated scenario is unaffected.

#### F-78 — five duplicate unauthenticated-401 scenarios *(Low · R2)*
**Where:** `tickets.feature:45-47`, `board.feature:74-76`, `comments.feature:39-41`, `teams.feature:29-31`, `epics.feature:40-42`
**Issue:** structurally identical scenarios testing the single `anyRequest().authenticated()` rule — a data-only variation, and `AuthEnforcementSliceTest.protectedEndpoints` already parametrizes the same matrix at slice level.
**Fix:** collapse to one Scenario Outline (or one representative scenario + the slice test).

#### F-79 — small suite-hygiene batch *(Low · R2)*
**Where:** `be/src/it/java/com/bovae/yaj/domain/repository/LookupQueryIntegrationTest.java:30-63`, `be/src/test/java/com/bovae/yaj/domain/enums/TicketStateTest.java:16-26` (+ `TicketTypeTest`), `be/src/test/java/com/bovae/yaj/web/controller/TestSecurityConfig.java`
**Issue:** three same-shape `findByEmail` case-variant tests unparametrized; `parse_shouldRoundTripCode...` fully implied by the `assertSame` test in both enum suites; `TestSecurityConfig` has zero usages.
**Fix:** `@ParameterizedTest` + `@ValueSource`; delete the round-trip tests and the dead config.

#### F-80 — comment immutability exists only by omission *(Low · R2)*
**Where:** `be/src/test/java/com/bovae/yaj/web/controller/CommentControllerTest.java`
**Issue:** the documented "comments are immutable" contract has no pin — someone adding an edit endpoint breaks it with a fully green suite.
**Fix:** two one-line MockMvc assertions: PUT/DELETE on a comment path → 405.

#### F-81 — LIKE-escape and N+1 guards incomplete on board *(Low · R2)*
**Where:** `be/src/bdd/resources/features/board.feature:47-60`, `be/src/test/java/com/bovae/yaj/board/BoardServiceTest.java:162-179`
**Issue:** the escape contract spans two layers (service builds the pattern, DB applies `ESCAPE '\'`) but only `%` is proven end-to-end — `_` and `\` literal searches never reach Postgres; and `findBoardTickets` has no `SqlStatementCount` guard though the pattern exists for other queries.
**Fix:** one `_`-containing ticket/search pair in the board search scenario; one `assertSingleStatement` case.

### Frontend (`fe`)

#### F-03 — mock board 401s behind the new auth wall *(High · R1)*
**Where:** `be/src/main/java/com/bovae/yaj/config/SecurityConfig.java:44-45`, `fe/src/pages/BoardPage.tsx:11-14`, `README.md`
**Issue:** `/api/v1/mock/board` is behind `anyRequest().authenticated()` and the SPA has no login/token yet, so a stock `docker compose up` renders a permanent "Could not load the board." and README's mock-board URL returns 401. (E2E unaffected — `auth.setup.ts` plants a real token.)
**Fix:** `permitAll()` the mock endpoint until FE auth (E12) lands, or note the auth requirement in README.

#### F-13 — flaky e2e mail polling *(Medium · R1)*
**Where:** `fe/e2e/auth.setup.ts:32-37`
**Issue:** fixed `waitForTimeout(2000)` plus "grab newest Mailpit message globally" (no recipient filter) — slow SMTP delivery or a pre-populated inbox picks the wrong/no message.
**Fix:** poll `GET /api/v1/search?query=to:${TEST_EMAIL}` with `expect.poll` instead of a fixed sleep.

#### F-14 — abort-signal handling in apiFetch *(Medium · R1)*
**Where:** `fe/src/api/client.ts:31-35`, `fe/src/api/board.ts`
**Issue:** a caller-supplied `init.signal` is silently clobbered by the timeout signal, and `getMockBoard` ignores TanStack Query's cancellation signal — unmount/cancel never aborts in-flight fetches.
**Fix:** `AbortSignal.any([timeoutSignal, init.signal])` (or `AbortSignal.timeout`), and pass the queryFn context signal through.

#### F-32 — JWT in localStorage *(Low · R1)*
**Where:** `fe/src/api/client.ts:13,25`, `fe/e2e/auth.setup.ts:68`
**Issue:** XSS-readable token storage; fine for the pet-project stage.
**Fix:** revisit (httpOnly cookie / refresh pattern) when designing E11/E12 auth context — decide and document.

#### F-33 — unused declared dependencies *(Low · R1)*
**Where:** `fe/package.json:19,30`
**Issue:** `@dnd-kit/core` has zero imports (planned for the DnD epic — E15); `@testing-library/user-event` unused (tests use `fireEvent`).
**Fix:** remove until needed, or knowingly keep `@dnd-kit/core` as pre-staged.

#### F-35 — card-count badge invisible to screen readers *(Low · R1)*
**Where:** `fe/src/components/Column.tsx:18-23`
**Issue:** the count badge is `aria-hidden` with no alternative.
**Fix:** fold the count into the section heading label, e.g. `aria-label={`${label}, ${cards.length} cards`}`.

#### F-36 — board error/retry states undertested *(Low · R1)*
**Where:** `fe/src/pages/BoardPage.test.tsx`, `fe/e2e/`
**Issue:** the retry button's disabled/"Retrying…" while-fetching state is untested; no e2e coverage of the error state. (Loading/success/error/recover, parser edges, timeout abort, auth header are covered.)
**Fix:** one Vitest case for retry-in-flight; optional e2e error-state check.

#### F-41 — dialogs have no scrim; destructive buttons fail contrast *(Medium · R2)*
**Where:** `fe/src/components/ui/dialog.tsx:40`, `alert-dialog.tsx:36`, `button.tsx:22`
**Issue:** `bg-black/50` and `text-white` are stock-palette utilities deleted by the `--color-*: initial` reset (verified: zero matching rules in the compiled CSS, and confirmed visually in the running app — the page behind an open dialog isn't dimmed at all). Destructive "Delete" buttons render near-black text on error-red, ~2.4:1 — fails WCAG and DESIGN.md. This is exactly the shadcn-adaptation step `fe/CLAUDE.md` mandates; these three classes were missed.
**Fix:** overlay → `bg-ink/50` (or a scrim token in `@theme`); destructive variant → `text-destructive-foreground` (already bridged).

#### F-42 — deleted ticket haunts the board *(Medium · R2)*
**Where:** `fe/src/pages/TicketDetailsPage.tsx:176`
**Issue:** `DeleteTicketDialog.onSuccess` invalidates only `['tickets']`; the board caches under `['board', …]` with 30s staleTime. Confirmed live: warm board → open card → delete → SPA-nav back to board → the deleted card still renders; clicking it lands on "Ticket not found". `TicketFormDialog` invalidates `['board']` on create/edit, so delete is the asymmetric gap.
**Fix:** also invalidate `['board']` and remove `['ticket', ticketId]` in `onSuccess`.

#### F-43 — move invalidates only the exact board key *(Medium · R2)*
**Where:** `fe/src/pages/BoardPage.tsx:113-115`
**Issue:** `onSettled` invalidates the current team+filter combo only. Other cached combos of the same team stay fresh 30s: drag to Done with `type=bug` active, clear the filter within 30s → the unfiltered cached board shows the card back in its old column. Same staleness hits `['tickets', …]` and `['ticket', id]` — a state PATCH changes both.
**Fix:** invalidate the prefixes `['board', teamId]`, `['tickets']`, `['ticket', id]` in `onSettled`.

#### F-48 — flagship drag e2e races its own PATCH *(Medium · R2)*
**Where:** `fe/e2e/board-smoke.spec.ts:60-67`
**Issue:** line 62 asserts the optimistic UI (passes before the PATCH resolves), then `page.goto` re-navigates, aborting an in-flight PATCH — if the backend hasn't committed, the reload shows the card back in "New". Intermittent failure currently masked by `retries: 2`; this is the only e2e guard on drag-persist.
**Fix:** `waitForResponse` on the tickets PATCH after `mouse.up()`, before the reload.

#### F-49 — board error-path unit coverage dropped in the E15 rewrite *(Medium · R2)*
**Where:** `fe/src/pages/BoardPage.test.tsx` vs `fe/src/pages/BoardPage.tsx:159-167,246-253`
**Issue:** the rewritten suite has zero tests for "Could not load the board." + retry recovery and "Could not load teams." — coverage that existed at the R1 baseline (F-36 noted only the retry-in-flight gap and explicitly recorded error/recover as covered).
**Fix:** one reject-then-resolve case asserting alert → Try again → board renders; optionally one for the teams error.

#### F-63 — board cards not keyboard-openable *(Low · R2)*
**Where:** `fe/src/components/TicketCard.tsx:45`
**Issue:** dnd-kit gives the card `role="button"` + `tabIndex=0`, but Enter/Space is consumed by `KeyboardSensor` to start a drag and `onClick` never fires from the keyboard — keyboard/SR users must detour via the /tickets list to open a ticket.
**Fix:** explicit inner open link/button on the card, or handle Enter-without-drag as navigate.

#### F-64 — search input desyncs from the URL *(Low · R2)*
**Where:** `fe/src/pages/BoardPage.tsx:43,81-93`
**Issue:** `search` state initializes from the URL once; back/forward or clicking the Board nav link changes `q` without updating the input — box shows "foo" while results are unfiltered.
**Fix:** sync the input when `qFilter` changes externally (or key the input on location).

#### F-65 — stale move-error banner *(Low · R2)*
**Where:** `fe/src/pages/BoardPage.tsx:45,244`
**Issue:** `moveError` clears only on the next drag; after a failed move, switching team or filters keeps "Could not move the ticket" over an unrelated fresh board.
**Fix:** clear on team/filter change (or key it to the board key).

#### F-66 — query cache survives logout *(Low · R2)*
**Where:** `fe/src/auth/AuthProvider.tsx:59-70`
**Issue:** logout clears token/state but not the TanStack Query cache; the next account on this browser is served the previous session's cached board/tickets/teams up to staleTime. Small blast radius while data is workspace-global — the pattern bites when scoping arrives.
**Fix:** `queryClient.clear()` on logout and after the unauthorized event.

#### F-67 — login can strand a valid token *(Low · R2)*
**Where:** `fe/src/auth/AuthProvider.tsx:51-57`
**Issue:** `login()` stores the token then calls `fetchMe()`; a non-401 failure (blip/500) rejects the promise and leaves status `unauthenticated` with the valid token in localStorage — the form shows an error while a refresh silently logs in.
**Fix:** remove the token in a catch before rethrowing (or derive the user without the second call).

#### F-68 — boot hydration ignores abort *(Low · R2)*
**Where:** `fe/src/auth/AuthProvider.tsx:24-38`
**Issue:** the boot `fetchMe()` passes no `AbortSignal` though the API supports one; StrictMode double-mount fires two `/auth/me` requests.
**Fix:** `AbortController` + abort in the effect cleanup.

#### F-69 — "Unknown epic" shown while epics still load *(Low · R2)*
**Where:** `fe/src/pages/TicketDetailsPage.tsx:79-81`
**Issue:** only `teamsQuery` gates loading; while `epicsQuery` is pending or failed, a ticket with an epic shows "Unknown epic" — misleading text presented as data.
**Fix:** show nothing/`…` while pending; reserve "Unknown epic" for a resolved miss.

#### F-70 — auth pages ignore an existing session *(Low · R2)*
**Where:** `fe/src/pages/LoginPage.tsx`, `SignupPage.tsx`
**Issue:** an authenticated user visiting /login or /signup gets the form (no nav, no session indication) instead of a redirect.
**Fix:** `if (status === 'authenticated') return <Navigate to="/" replace />`.

#### F-71 — trim guards inconsistent across forms *(Low · R2)*
**Where:** `fe/src/pages/TeamsPage.tsx:191-195`, `EpicsPage.tsx:236-243,308`
**Issue:** whitespace-only names/titles pass the client guards (`required`/`!title` don't trim) and round-trip to the server for a 400; `TicketFormDialog` trims — three forms, two behaviors.
**Fix:** the same `.trim()` guard everywhere.

#### F-72 — debounce test doesn't test debouncing *(Low · R2)*
**Where:** `fe/src/pages/BoardPage.test.tsx:201-215`
**Issue:** `filter_shouldSendSearchTermAsQParam_afterDebounce` still passes if the 300ms debounce is deleted and getBoard fires per keystroke — `waitFor` matches the eventual call.
**Fix:** assert no intermediate-prefix call (`'l'`, `'lo'`), or rename.

#### F-73 — fetch-stub cleanup is a no-op *(Low · R2)*
**Where:** `fe/src/api/*.test.ts` afterEach (e.g. `board.test.ts:34-37`)
**Issue:** `vi.restoreAllMocks()` doesn't undo `vi.stubGlobal('fetch', …)` — the last test's fetch mock persists within the file; harmless today only because every test re-stubs first.
**Fix:** `unstubGlobals: true` in the vitest config (one line, all seven files).

#### F-74 — test scaffolding copy-pasted across suites *(Low · R2)*
**Where:** `fe/src/api/*.test.ts` (6 files), `fe/src/pages/*.test.tsx` + component tests
**Issue:** `jsonResponse`/`problemResponse` duplicated verbatim in 6 API files; QueryClient render wrappers and `team()`/`epic()`/`ticket()` fixture builders duplicated across 6+ component suites.
**Fix:** shared `fe/src/test/helpers.tsx`; mechanical consolidation.

#### F-75 — epic delete success/cancel untested *(Low · R2)*
**Where:** `fe/src/pages/EpicsPage.test.tsx`
**Issue:** only disabled-guard and 409-conflict are covered — no row-removed-on-204 and no confirm-cancel case, though TeamsPage covers the analogous paths in separate code.
**Fix:** mirror TeamsPage's two delete tests.

### Infrastructure — compose / CI / Makefile / README (`infra`)

#### F-02 — backend unreachable from the host *(High · R1)*
**Where:** `docker-compose.yml:30-63`, `fe/vite.config.ts:8`, `README.md:41-44`
**Issue:** the `be` service publishes no host port, so README's `http://localhost:8080/actuator/health` is dead and the Vite dev proxy default (`http://localhost:8080`) has no backend — `npm run dev` has no working API path (postgres/valkey also unpublished, so running BE on the host isn't possible either).
**Fix:** add `ports: ["8080:8080"]` to `be` (or a dev override file) and correct the README.

#### F-11 — CORS default missing the Vite dev origin *(Medium · R1)*
**Where:** `docker-compose.yml:45`
**Issue:** default `YAJ_CORS_ALLOWED_ORIGINS` is only `http://localhost:8081`; POSTs from the dev SPA (`Origin: http://localhost:5173` via the proxy) will 403 — same-origin GETs just haven't tripped it yet.
**Fix:** include `http://localhost:5173` in the default.

#### F-12 — CI never runs on merge commits *(Medium · R1)*
**Where:** `.github/workflows/ci.yml:3-5`
**Issue:** all triggers are `pull_request` only — pushes to `develop`/`main` are never built or tested, yet `release.yml` releases from those branches.
**Fix:** add `push: branches: [develop, main]`.

#### F-15 — `make fe-lint` weaker than CI *(Medium · R1)*
**Where:** `Makefile:24-25` vs `.github/workflows/ci.yml:88-89,106-109`
**Issue:** `fe-lint` runs only ESLint while CI also gates Prettier and `tsc --noEmit` (via build) — green locally can still fail CI.
**Fix:** add `npm run format:check` (and optionally typecheck) to `fe-lint`.

#### F-29 — compose vs README env drift + mailpit profile trap *(Low · R1)*
**Where:** `docker-compose.yml:43-50,76-83`, `README.md:60-67`
**Issue:** `YAJ_VALKEY_*`/`YAJ_SMTP_*` are hardcoded literals (no `${VAR:-default}`) though README presents them as `.env`-overridable; and BE always points at `mailpit` while mailpit sits behind the opt-in `mail` profile — default `make up` stack fails email delivery at runtime (CI e2e uses `--profile mail`, so only local is affected).
**Fix:** add compose interpolation or fix the README; add a README caveat about the `mail` profile.

#### F-30 — README inaccuracies *(Low · R1)*
**Where:** `README.md:105`, `Makefile`
**Issue:** "Run `make help`" — no such target; `fe-lint` described as "lint and format checks" but runs ESLint only.
**Fix:** add a `help` target or drop the sentence; fix the description (or fix F-15 and keep it).

#### F-31 — public default JWT secret in compose *(Low · R1)*
**Where:** `docker-compose.yml:54`
**Issue:** `YAJ_JWT_SECRET` falls back to a committed default — a deployment that forgets the env var silently signs tokens with a known key.
**Fix:** drop the fallback outside local dev (fail fast).

#### F-34 — Playwright browsers re-downloaded every CI run *(Low · R1)*
**Where:** `.github/workflows/ci.yml:159-161`
**Issue:** no cache for `~/.cache/ms-playwright`.
**Fix:** cache keyed on the Playwright version.

#### F-37 — planned seed data would run on the default stack *(High · R2, proposal-stage)*
**Where:** `openspec/changes/fix-dnd-overlay-add-seed-data/design.md` (D3), `proposal.md` (Impact)
**Issue:** the in-flight change plans `docker-compose.yml` setting `YAJ_LIQUIBASE_CONTEXTS=local` on `be` **by default** — QA's mandated clean-checkout `docker compose up --build` (§2) would start with preloaded users/teams/epics/tickets/comments, directly violating §9 ("the default startup path must not load sample or seed data") and the graded DoD item "a fresh database starts with schema and migration metadata only". Not yet implemented — caught at proposal stage.
**Fix:** make seeding opt-in: `YAJ_LIQUIBASE_CONTEXTS=${YAJ_LIQUIBASE_CONTEXTS:-prod}` in compose with the `local` value supplied via `.env`/env override (documented in README), or a separate compose override file.

#### F-50 — half-committed openspec archive in the git index *(Medium · R2, transient)*
**Where:** git index — 36 files staged `AD` under `openspec/changes/add-*`
**Issue:** HEAD contains only `openspec/changes/archive/`, but the index still holds the pre-archive paths staged as adds (content byte-identical to the archived copies) while deleted from the working tree. A plain `git commit` right now resurrects all eight old change dirs next to their archive copies. Also riding along: an unstaged edit to `requirements/epics-catalog.md`.
**Fix:** `git restore --staged 'openspec/changes/add-*'` (or `git add -A openspec/changes`), then commit the epics-catalog edit deliberately.

#### F-51 — mailpit profile drift (inverted since R1) *(Medium · R2)*
**Where:** `README.md:81-95`, `.github/workflows/ci.yml:146,155,176,179`
**Issue:** the F-29 fix removed the `mail` profile — mailpit now always starts — but README still documents it as opt-in behind `--profile mail`, and CI passes `--profile mail` four times as a silent no-op.
**Fix:** rewrite the README section (mailpit is part of the default stack); drop `--profile mail` from ci.yml.

#### F-52 — README drift accumulated since R1 *(Medium · R2)*
**Where:** `README.md:41,62`
**Issue:** (a) access-URL table still lists `http://localhost:8081/api/v1/mock/board` — the mock controller no longer exists; (b) `YAJ_CORS_ALLOWED_ORIGINS` default documented as `http://localhost:8081` but compose now defaults to `…8081,http://localhost:5173`; (c) env table omits `YAJ_SMTP_TIMEOUT` (`application.yml:58`). §11 requires an accurate README.
**Fix:** replace/drop the mock row; sync the CORS default; add the timeout row.

#### F-82 — mailpit unpinned and unguarded *(Low · R2)*
**Where:** `docker-compose.yml:77-83`
**Issue:** `axllent/mailpit:latest` unpinned; no healthcheck and `be` doesn't depend on it, so a first signup email can race mailpit startup (narrow window — SMTP is used lazily).
**Fix:** pin a major tag (`axllent/mailpit:v1`); optionally `depends_on: service_started`.

#### F-83 — release toolchain installed unpinned *(Low · R2)*
**Where:** `.github/workflows/release.yml:43,70`
**Issue:** `npm install -g semantic-release @semantic-release/...` resolves latest at release time — unreproducible releases and supply-chain exposure in the one workflow with `contents: write`.
**Fix:** pin exact versions in the install command (or a committed dev-deps manifest).

#### F-84 — dockerignore gaps *(Low · R2)*
**Where:** `fe/.dockerignore`; root `.dockerignore`
**Issue:** fe build context includes `*.md` (44KB `DESIGN.md`, `CLAUDE.md`) and `fe.iml` — doc-only edits invalidate the `COPY . .` layer and force a full rebuild. The root `.dockerignore` is dead config: no build uses the repo root as context.
**Fix:** add `*.md`/`*.iml` to `fe/.dockerignore` (keep `e2e/` — the build's typecheck needs it); delete the root file or note why it exists.

#### F-85 — CI e2e rebuilds images from scratch *(Low · R2)*
**Where:** `.github/workflows/ci.yml:146` (fe-e2e)
**Issue:** `docker compose up --build` with no buildx/GHA layer cache — the full Maven dependency download + package dominates job time every run (distinct from F-34, the Playwright browser cache).
**Fix:** `docker/setup-buildx-action` + `cache-from/cache-to: type=gha` when e2e time starts to hurt.

#### F-86 — image hardening deferred *(Low · R2)*
**Where:** `be/Dockerfile:22`; `fe/Dockerfile:13`
**Issue:** runtime base is full `amazoncorretto:21-alpine-jdk` (a jlink'd runtime would drop ~150MB+); fe nginx master runs as root (stock image behavior; be already runs non-root).
**Fix:** jlink custom runtime / `nginxinc/nginx-unprivileged` — only if size or hardening ever matters for this local-first stack.

#### F-87 — no SMTP STARTTLS/SSL knob *(Low · R2)*
**Where:** `be/src/main/java/com/bovae/yaj/config/MailConfig.java:17-28`, `application.yml:54-60`
**Issue:** only host/port/auth/timeout are configurable. §3 requires supporting `relay1.dataart.com`; if that relay requires STARTTLS it can't be configured, and setting `YAJ_SMTP_USERNAME/PASSWORD` today sends credentials in plaintext.
**Fix:** `YAJ_SMTP_STARTTLS` → `mail.smtp.starttls.enable` JavaMail property.

---

## Verified-clean areas (R1 — don't re-audit without cause)

- **JWT validation:** HMAC pinned via `verifyWith(SecretKey)` (no alg-confusion), `sub`/`jti`/`exp` null-checked, denylist TTL matches remaining life, no secret default in `application.yml` (`@Size(min=32)`).
- **Login timing:** dummy-hash equalization correct; unverified-403 only after password match.
- **Verification consume:** pessimistic lock prevents double-consume; expiry boundary correct; raw token never persisted/logged.
- **Signup race:** citext UNIQUE + `DataIntegrityViolationException` → 409 correct; email dispatch correctly AFTER_COMMIT.
- **Error surface:** all domain exceptions mapped, no internals leaked, generic 500 catch-all.
- **Schema vs entities:** otherwise exact match; FK RESTRICT/CASCADE back the delete guards; FK columns indexed.
- **FE React/Query:** keys present, no state races, sane Query config; `dist`/`test-results`/`.auth` properly gitignored.
- **Test craft:** fixed `Clock` everywhere, strong parametrization, log/response-confidentiality tests, `SqlStatementCount` N+1 guards.

---

## Verified-clean areas (R2 — don't re-audit without cause)

**R1 fixes spot-checked — all landed correctly:** F-01 (`@UpdateTimestamp` on all four entities, manual bump + Clock removed, no-op saves stay clean), F-02 (be publishes 8080), F-04 (BDD lookup by email + `@skeleton` cleanup), F-05 (401/403 Scenario Outline + 429 with Retry-After), F-06/F-07 (teams race→409 + `@Size(max=100)`), F-08 (`@Async` dispatcher with MDC decorator — residual edge is F-60), F-09 (fail-closed 503 with correlation id — posture gap is F-62), F-10 (atomic Lua INCR+PEXPIRE — heal edge is F-61), F-11 (CORS includes 5173), F-13 (Mailpit polling via `expect.poll` filtered by recipient), F-14 (`AbortSignal.any` + signal threaded through every API module), F-15 (fe-lint runs ESLint+Prettier+typecheck), F-16–F-20 (JaCoCo exclusions narrowed, deadline-polling, false tests deleted, branch gaps filled).

- **Live UI walkthrough (full happy path works):** signup → Mailpit email → verify → login → team/epic create dialogs → ticket create → board (5 columns, workflow order, per-column recency) → filters (type+epic+search AND-combined, URL-synced) → drag persists and survives refresh → comments oldest-first → delete guards disable correctly (team with refs, epic with tickets) → team change clears epic in the edit dialog → logout redirects to login.
- **BE domain slices:** epic-team invariant airtight (immutable team, same-team rule on create/update, no TOCTOU); comments immutable and never touch ticket `modified_at`; board always 5 columns, 3 fixed queries, no N+1, LIKE wildcard escaping correct (`%`/`_`/`\`, backslash-first); enum parsing strict → 400; schema matches entities exactly; zero seed data in migrations 0001–0008; DTOs everywhere; `open-in-view: false`; correct `@Transactional` boundaries; mock board fully removed.
- **BE auth cross-cutting:** all new endpoints behind `anyRequest().authenticated()`; no new permitAll; exception mapping complete for new slices; no sensitive data in new logs (ids only); no config drift (no new `YAJ_*` since R1); principal contract consistent via `CurrentUserProvider`.
- **BE tests:** service suites use captor-based observable assertions with boundary parametrization; BDD asserts `modified_at` semantics via re-GET (not response echo); FK-safe cleanup order (comments→tickets→epics→teams→users); 90/90 gate on combined unit+BDD exec, not bypassable in CI.
- **FE:** DnD revert logic correct (snapshot in `onMutate` after `cancelQueries`, rollback in `onError`, same-column no-op); no XSS surface (no `dangerouslySetInnerHTML`, plain-text rendering); query keys consistent; loading/empty/error states on every data screen; 401 chain (client→event→provider→guard) correct incl. login-401 exclusion; no mock drift — every FE fixture matches the real BE DTOs; e2e uses role selectors, no fixed sleeps, per-run unique data; `tsc --noEmit` clean.
- **Infra:** compose health-gated startup correct; postgres/valkey unpublished; be Dockerfile layered/non-root/thin-jar; nginx SPA fallback + proxy headers correct; CI coverage gate intact; secrets handling clean (`GITHUB_TOKEN` only, `persist-credentials: false`); all 11 archived openspec changes synced into main specs.
