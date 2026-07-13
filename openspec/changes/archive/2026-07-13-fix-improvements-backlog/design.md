# fix-improvements-backlog — Design

## Context

R2 review left ~65 open findings across be-core, be-auth, be-test, fe, and infra (`requirements/improvements-catalog.md`, F-21–F-87). All Highs are closed; this change clears every remaining Medium and Low. No new features — only truthful error contracts, cache coherence, accessibility, test de-flaking/de-duplication, and infra drift. Three findings are decide-and-document (F-25, F-32, F-86) and are resolved here as recorded decisions, not code.

Current-state notes discovered during proposal research:
- F-41's scrim half is already fixed (`bg-primary/50` landed with fix-board-ux-polish); only the destructive-button `text-white` remains.
- No DB schema changes are needed anywhere in this change — zero new Liquibase changesets.

## Goals / Non-Goals

**Goals:**
- Every open catalog finding ticked (fixed or wontfix-documented) in one change.
- Additive API evolution only: new fields (`authorEmail`, `createdByEmail`) and new 404/409/503 responses that replace raw 500s on already-failing paths.
- Test-suite changes leave the 90/90 gate and all suites green; flake sources removed, not suppressed.

**Non-Goals:**
- New product behavior, endpoints, or screens.
- Schema migrations, seed data, or auth-model changes (httpOnly cookies etc. — F-32 explicitly deferred).
- Image hardening (F-86 — wontfix for this local-first stack).

## Decisions

### Backend core

- **D1 (F-38) Insert-side race translation.** Wrap the `saveAndFlush` in `TicketService.create/update`, `EpicService.create`, `CommentService.add` in `catch (DataIntegrityViolationException)` → re-throw the same `NotFoundException` the pre-check would have thrown (the referenced row is gone). Mirrors the already-landed delete-side pattern (F-06). Alternative (pessimistic locks) rejected: heavier, and 404 is the truthful answer.
- **D2 (F-39) Vanished-row translation.** One `@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` in `GlobalExceptionHandler` → 404 problem detail. Covers every update/delete race in one place.
- **D3 (F-40) Author email embedding.** Add `authorEmail` to `CommentResponse` and `createdByEmail` to `TicketResponse`, resolved server-side from the users table. For lists, resolve with one batched `findAllById` over the distinct author ids (no N+1). Alternative (a `/users/{id}` lookup endpoint) rejected: more surface, and the FE would need request fan-out. FE then always renders the email — the current-user special case in `CommentThread`/`TicketDetailsPage` goes away.
- **D4 (F-53) Service-authoritative validation.** Drop `@NotBlank`/`@Size` from ticket/epic/comment create+update DTOs; keep `@NotNull` only where the service assumes presence of a reference (UUIDs). Services already validate post-trim with meaningful messages; verify each handles a null title/body/description with its own 400 before removing the annotation.
- **D5 (F-54) Single clock source.** Both timestamps come from the DB clock. Exact Hibernate 6 annotation (`@CurrentTimestamp(source = ...)` vs `@CreationTimestamp/@UpdateTimestamp(source = DB)`) to be confirmed against Hibernate docs at apply time — the decision here is the invariant: fresh rows have `modified_at == created_at`.
- **D6 (F-55/F-56) Deterministic ordering.** Append `, id` to the comments and board ORDER BYs. List endpoints (teams, epics, tickets) pass `Sort.by("createdAt").and(Sort.by("id"))` at the repository call. Alternative (name/title sort) rejected: creation order is stable under renames.
- **D7 (F-57) One case-folding engine.** Fold both sides in SQL: `LOWER(t.title) LIKE LOWER(:pattern)`; drop the Java `toLowerCase`. Escaping logic unchanged.
- **D8 (F-58) Proxy-correct Location headers.** `server.forward-headers-strategy: framework` in `application.yml` + `proxy_set_header Host $http_host` in `nginx.conf`.
- **D9 (F-59) Delete dead surface.** Remove `CommentRepository.existsByTicketId`, `TicketState.position()` and their tests.

### Backend auth

- **D10 (F-21) Active-user check in the filter.** After token validation, `JwtAuthenticationFilter` verifies the user exists and `deletedAt == null` (indexed PK lookup, one read per authenticated request — acceptable here); otherwise 401. Kills the whole class (deleted accounts authoring tickets/comments). Alternative (denylist tokens on delete) rejected: no delete endpoint exists to hook, and the check must hold for future ones.
- **D11 (F-22) Success resets the window.** `LoginService` deletes the rate-limit key after successful authentication.
- **D12 (F-23) Dispatcher robustness.** Catch `RuntimeException` (not just `MailException`); log exception class + sanitized message, never the recipient; validate `link-base-url` parses in the properties record's compact constructor (fail at startup, not after commit).
- **D13 (F-24) Idempotent logout.** Expired-token parse failure in `parseForRevocation` → treat as already-revoked, return 204.
- **D14 (F-26) Split timeouts.** New Valkey command-timeout property (default 1s) separate from the 5s startup-validation timeout; rename the constant to match its use.
- **D15 (F-60) Overload degradation.** Async email executor gets `CallerRunsPolicy` — under saturation the send degrades to synchronous instead of throwing after commit.
- **D16 (F-61) Heal path.** Re-arm a full window only when TTL is `-1` (key genuinely lost its TTL). Map `0` → 1s and `-2` → 0s `Retry-After`.
- **D17 (F-62) Uniform outage posture.** `@ExceptionHandler` for `DataAccessResourceFailureException`/`QueryTimeoutException` in `GlobalExceptionHandler` returning the same 503 problem detail the JWT filter emits.
- **D18 (F-25) Signup 409 stays.** Documented as a deliberate UX choice: signup benefits from an immediate "account exists" message; login/resend remain uniform because they are the attack-relevant oracles. Recorded via catalog wontfix note.

### Frontend

- **D19 (F-41) Destructive contrast.** `text-white` → `text-destructive-foreground` (already bridged at `index.css:85`). Scrim already fixed — verify alert-dialog matches dialog.
- **D20 (F-42/F-43) Cache invalidation sets.** Ticket delete `onSuccess`: invalidate `['tickets']` + `['board']`, remove `['ticket', id]`. Board move `onSettled`: invalidate prefixes `['board', teamId]`, `['tickets']`, `['ticket', id]`. Prefix invalidation covers all filter-combo cache entries.
- **D21 (F-63) Keyboard-openable cards.** The card title becomes an explicit inner link to `/tickets/{id}` (focusable, Enter activates it, not the dnd-kit sensor). Pointer drag behavior unchanged.
- **D22 (F-64/F-65) Board UI state sync.** Sync the search input when the URL `q` changes externally; clear `moveError` whenever the board query key (team/filters) changes.
- **D23 (F-66/F-67/F-68) Session hygiene.** `queryClient.clear()` on logout and on the unauthorized event; `login()` clears the stored token in a catch before rethrowing when `fetchMe` fails; boot hydration uses an `AbortController` aborted in the effect cleanup.
- **D24 (F-69) Epic label truthfulness.** While `epicsQuery` is pending show a neutral placeholder; "Unknown epic" only after the query resolves without a match.
- **D25 (F-70) Auth-screen redirect.** `/login` and `/signup` render `<Navigate to="/" replace />` when already authenticated.
- **D26 (F-71) Uniform trim guards.** Team/epic forms trim before the empty-check, matching `TicketFormDialog`.
- **D27 (F-32) Token storage stays localStorage.** XSS-readable but acceptable at this stage; revisit only if an auth epic introduces refresh tokens. Recorded via catalog wontfix note.

### Tests

- **D28 (F-47) One BDD ApiClient.** Scenario-scoped `ApiClient` component (always `JdkClientHttpRequestFactory`, PATCH-capable, shared `exchange`/`extractId`/`extractInstant`) next to `SharedScenarioState`; the five step classes delegate to it.
- **D29 (F-44) Global Valkey cleanup.** Move rate-limit key purge to a tag-independent `@After` hook so no feature inherits another's counters.
- **D30 (F-77/F-78) Feature-file dedup.** Per-feature `Background` for the register/verify/login preamble; the five unauthenticated-401 scenarios collapse to one `Scenario Outline` (slice test already covers the matrix).
- **D31 (F-74) Shared fe test helpers.** `fe/src/test/helpers.tsx` exporting `jsonResponse`/`problemResponse`, the QueryClient render wrapper, and `team()`/`epic()`/`ticket()` builders; suites migrate mechanically.
- Remaining test findings (F-27, F-28, F-36, F-45, F-46, F-48, F-49, F-72, F-73, F-75, F-76, F-79, F-80, F-81) are point fixes listed in tasks; no design needed.

### Infra

- **D32 (F-31) Compose JWT default stays, documented.** The compose file is the local-dev path; the app already enforces a 32+ char secret. README gets an explicit "override outside local dev" warning. Fail-fast rejected: it would break the mandated clean-checkout `docker compose up --build`.
- **D33 (F-86) Image hardening wontfix.** jlink runtime and non-root nginx bring no value to a local-first grading stack; recorded in the catalog.
- **D34 (F-82/F-83) Pinning.** `axllent/mailpit:v1` + healthcheck + `be` `depends_on: service_started`; semantic-release toolchain pinned to exact versions in `release.yml`.
- **D35 (F-34/F-85) CI caching.** Cache `~/.cache/ms-playwright` keyed on the Playwright version; `docker/setup-buildx-action` + `cache-from/cache-to: type=gha` for the e2e image builds.
- **D36 (F-29/F-30/F-51/F-52) Drift sync.** Compose env values become `${VAR:-default}` interpolations; README rewritten to match reality (mailpit always on, no mock-board URL, current CORS default, `YAJ_SMTP_TIMEOUT` row, no `make help` claim); `--profile mail` dropped from CI.
- **D37 (F-87) STARTTLS knob.** `YAJ_SMTP_STARTTLS` → `mail.smtp.starttls.enable` JavaMail property in `MailConfig`, default off.
- **D38 (F-50) Index cleanup.** `git restore --staged 'openspec/changes/add-*'` before the implementation commit; commit the riding-along `epics-catalog.md` edit deliberately.
- **D39 (F-84) Build-context hygiene.** Add `*.md`/`*.iml` to `fe/.dockerignore` (keep `e2e/` — typecheck needs it); delete the dead root `.dockerignore`.

## Risks / Trade-offs

- [D4 removes bean validation] → a service missing a null guard turns 400 into 500. Mitigation: verify/add null handling per service first; F-46's new malformed-input tests plus existing blank-input tests pin the 400 contract.
- [D5 changes the `modified_at` clock source] → tests comparing timestamps across sources may shift. Mitigation: run the full suite; the invariant `modified_at == created_at` on fresh rows is itself a new assertion.
- [D10 adds a DB read per authenticated request] → measurable but tiny (indexed PK). Accepted for correctness; no caching until it shows up in profiles.
- [D28 rewrites shared BDD plumbing] → wide blast radius across five step classes. Mitigation: pure refactor, no scenario changes; full BDD suite gates it.
- [F-48 fix awaits the PATCH before reload] → e2e gets slightly slower but stops racing; retries stay as safety net.
- [Large single change] → apply in waves (be-core → be-auth → be-tests → fe → fe-tests → infra) with suite runs between waves; tasks are ordered accordingly.

## Migration Plan

No deploy/rollback complexity: no schema changes, additive API only. Implementation order = tasks order; each wave ends with the relevant `make` gate (`be-lint`/`be-test`, `fe-lint`/`fe-test`, e2e). Catalog checkboxes ticked in the final wrap-up task.

## Open Questions

None — decide-and-document items resolved above (D18, D27, D32, D33).
