# fix-improvements-backlog

## Why

The R2 whole-codebase review (`requirements/improvements-catalog.md`) leaves 15 open Medium and ~50 open Low findings: write races surfacing as 500s, authorship rendered as raw UUIDs (a DoD acceptance risk), invisible dialog scrims and failing contrast, cache-coherence bugs that resurrect deleted tickets, flake time-bombs in BDD/e2e suites, and accumulated README/CI/compose drift. All High findings are closed; this change clears the remaining backlog before new feature work builds on these paths.

## What Changes

Addresses every open finding: F-21–F-32, F-34–F-36, F-38–F-87 (F-33 already resolved by usage; F-37/F-12 previously wontfixed). Decide-and-document items (F-25, F-32, F-86) are resolved as documented decisions rather than code. Grouped:

**Backend behavior (be-core)**
- F-38/F-39: insert-side FK races and vanished-row optimistic-lock failures mapped to 404/409 problem details instead of raw 500s
- F-40: `authorEmail` on `CommentResponse`, `createdByEmail` on `TicketResponse` — UI shows emails, not UUIDs
- F-53: DTO `@NotBlank`/`@Size` duplicates dropped; services stay the single validation authority with meaningful messages
- F-54: one clock source for `created_at`/`modified_at`
- F-55/F-56: deterministic ordering — `id` tie-breaks on comments/board queries, stable sort on teams/epics/tickets lists
- F-57: board title search folds case in the DB on both sides
- F-58: `forward-headers-strategy` + nginx `Host $http_host` so `Location` headers survive the proxy
- F-59: dead surface deleted (`existsByTicketId`, `TicketState.position`)

**Backend auth (be-auth)**
- F-21: soft-deleted users rejected at the JWT filter (not just `/me`)
- F-22: successful login clears the rate-limit window
- F-23: email dispatcher catches `RuntimeException`, validates link-base-url at startup, logs without PII
- F-24: logout with an expired token is an idempotent 204
- F-26: Valkey command timeout split from startup timeout (~1s runtime)
- F-60: async email executor degrades via `CallerRunsPolicy` instead of rejecting after commit
- F-61: rate-limiter heal path no longer re-locks a user due to unblock
- F-62: Valkey outage returns the same deliberate 503 on all paths (limiters included)
- F-25: signup 409 documented as a deliberate UX choice (login/resend stay uniform)

**Backend tests (be-test)**
- F-27: BDD cleanup deletes tickets before epics
- F-28: BDD scenarios for teams 409/400 and signup duplicate-email 409
- F-44: Valkey rate-limit key cleanup moved to a global `@After` hook
- F-45: unit test pins update-with-epicId keeps the epic
- F-46: MockMvc case pins malformed-UUID → 400 problem+json
- F-47: five copy-pasted BDD HTTP helper blocks consolidated into one scenario-scoped `ApiClient`
- F-76–F-81: `findByEmail` lookups, `Background` preambles, 401 Scenario Outline collapse, suite hygiene batch, comment-immutability 405 pins, `_`-search + statement-count guards

**Frontend behavior (fe)**
- F-41: dialog scrim restored and destructive buttons meet contrast (theme-token classes replace reset-deleted stock utilities)
- F-42/F-43: ticket delete and board move invalidate all affected query caches — no ghost cards or stale boards
- F-35/F-63: card count exposed to screen readers; board cards keyboard-openable
- F-64/F-65: board search input syncs with URL changes; move-error banner clears on team/filter change
- F-66/F-67/F-68: logout clears the query cache; failed login leaves no stranded token; boot hydration aborts on unmount
- F-69: "Unknown epic" only after epics resolve
- F-70: authenticated users are redirected away from /login and /signup
- F-71: trim guards consistent across team/epic/ticket forms
- F-32: JWT-in-localStorage documented as accepted for this stage

**Frontend tests (fe)**
- F-36/F-49: board error/retry unit coverage restored (incl. retry-in-flight)
- F-48: drag e2e waits for the PATCH response before reloading
- F-72: debounce test asserts no intermediate calls
- F-73: `unstubGlobals: true` in vitest config
- F-74: shared test helpers (`jsonResponse`/`problemResponse`, render wrappers, fixture builders)
- F-75: EpicsPage delete success/cancel tests

**Infra**
- F-29/F-30/F-51/F-52: compose env interpolation, README sync (mailpit, mock-board, CORS, SMTP timeout, make targets)
- F-31: compose JWT default documented as local-only
- F-34/F-85: CI caches Playwright browsers and Docker build layers
- F-50: stale pre-archive openspec paths unstaged from the git index
- F-82/F-83: mailpit image pinned + healthcheck; release toolchain pinned
- F-84: fe `.dockerignore` excludes `*.md`/`*.iml`; dead root `.dockerignore` removed
- F-87: `YAJ_SMTP_STARTTLS` knob for the required relay support
- F-86: image hardening (jlink, non-root nginx) documented as wontfix for this local-first stack

## Capabilities

### New Capabilities

None — every behavior change lands in an existing capability.

### Modified Capabilities

- `tickets-crud`: write races return 404/409 not 500; responses carry `createdByEmail`; list ordering deterministic
- `epics-crud`: write races return 404/409 not 500; list ordering deterministic
- `teams-crud`: vanished-row races return 404 not 500; list ordering deterministic
- `comments`: insert races return 404 not 500; responses carry `authorEmail`; chronological order gets an `id` tie-break
- `board-read`: per-column ordering gets an `id` tie-break; title search case-folds consistently for non-ASCII
- `auth-resilience`: soft-deleted users rejected at the filter; successful login resets the rate window; logout idempotent for expired tokens; uniform 503 outage posture; heal-path and overload edges fixed
- `fe-design-primitives`: modal dialogs dim the page behind a scrim; destructive buttons meet WCAG contrast
- `fe-board`: move invalidates all affected caches; cards keyboard-openable; SR-visible column counts; search input follows URL; move errors clear on context switch
- `fe-ticket-management`: delete invalidates board caches; epic label shows pending state, `createdByEmail` displayed
- `fe-comments`: comment author displayed as email
- `fe-auth-session`: logout clears cached data; failed login leaves no token; boot hydration abortable
- `fe-auth-screens`: authenticated users redirected off /login and /signup
- `fe-team-management`: whitespace-only names rejected client-side
- `fe-epic-management`: whitespace-only titles rejected client-side

## Impact

- **be main:** `TicketService`, `EpicService`, `TeamService`, `CommentService`, `GlobalExceptionHandler`, DTOs (`CommentResponse`, `TicketResponse`, create/update requests), entities (timestamp mapping), repositories (ordering), `BoardService`/`TicketRepository` (search), `JwtAuthenticationFilter`, `LoginService`, `LogoutService`, `VerificationEmailDispatcher`, `AsyncConfig`, `ValkeyConfig`, `FixedWindowRateLimiter`, `MailConfig` (+ properties), `application.yml`, `nginx.conf`
- **be test/bdd/it:** step classes (shared `ApiClient`, cleanup hooks, `findByEmail`), feature files (`Background`, outlines, new scenarios), controller/service unit tests, statement-count guards
- **fe:** `dialog.tsx`, `alert-dialog.tsx`, `button.tsx`, `BoardPage`, `TicketDetailsPage`, `TicketCard`, `Column`, `AuthProvider`, `LoginPage`, `SignupPage`, `TeamsPage`, `EpicsPage`, `CommentThread`, vitest config, shared test helpers, e2e specs
- **infra:** `docker-compose.yml`, `README.md`, `Makefile`, `.github/workflows/ci.yml`, `release.yml`, `fe/.dockerignore`, git index cleanup
- **API compat:** additive only — new response fields (`authorEmail`, `createdByEmail`) and new 404/409/503 responses replacing raw 500s on already-failing paths
