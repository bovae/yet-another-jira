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

---

## Progress Checklist

### High

- [x] **F-01** (be-core) — `modified_at` never persists on update: `@Generated` mapping drops manual bumps; BDD masks it by asserting the response body
- [x] **F-02** (infra) — `be` service publishes no host port: README URLs dead, Vite dev proxy has no backend to reach
- [x] **F-03** (fe) — mock board endpoint now requires auth: stock `docker compose up` renders a permanent board error
- [x] **F-04** (be-test) — BDD verify-user step picks `findAll().get(size-1)` with no ordering + skeleton feature never cleans up → cross-feature flake
- [x] **F-05** (be-test) — login failure paths (401 bad password, 403 unverified, 429 rate limit) have no BDD coverage through the real filter chain

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
- [ ] **F-33** (fe) — unused declared dependencies: `@dnd-kit/core` (until the DnD epic), `@testing-library/user-event`
- [ ] **F-34** (infra) — CI re-downloads Playwright browsers every e2e run; cache `~/.cache/ms-playwright`
- [ ] **F-35** (fe) — Column card-count badge is `aria-hidden` with no screen-reader alternative
- [ ] **F-36** (fe) — BoardPage retry-in-flight state untested; no e2e coverage of the board error state

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
