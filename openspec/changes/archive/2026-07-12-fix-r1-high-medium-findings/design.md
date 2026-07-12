# fix-r1-high-medium-findings — design

## Context

R1 review findings, High + Medium, spanning be main/test/bdd/it, fe, and infra. Detailed evidence and file:line pointers live in `requirements/improvements-catalog.md` — this document only records the implementation decisions, not the findings themselves. Current-state note: `Team.modifiedAt` already carries `@UpdateTimestamp` (F-01 was fixed for Team during the teams-crud change); `User`, `Epic`, `Ticket` still use the broken `@Generated` mapping.

## Goals / Non-Goals

**Goals:**
- Close F-01–F-11, F-13–F-20 with the smallest diffs that fix root causes
- No new runtime dependencies; no API contract breaks (only raw 500s/container errors become deliberate 400/409/503)

**Non-Goals:**
- Low findings (F-21+) — separate round
- F-12 (CI push triggers) — wontfixed in the catalog
- FE auth (E12); F-03 gets a temporary `permitAll` bridge only

## Decisions

### D1 — F-01: `@UpdateTimestamp` on the remaining three entities
Replace `@Generated`/`@ColumnDefault("now()")` on `modifiedAt` with `@UpdateTimestamp` in `User`, `Epic`, `Ticket`, matching the already-fixed `Team`. JVM-clock timestamps (not DB `now()`) are the accepted trade-off already made for `Team`. The BDD rename-timestamp step re-GETs the team and asserts the fresh read, so the suite can no longer pass on an un-persisted in-memory value.

### D2 — F-06: translate constraint races in `TeamService`
Mirror `SignupService`: wrap the write in `try/catch (DataIntegrityViolationException)` → `ConflictException`, in `create`, `rename`, and `delete`. `create`/`rename` already `saveAndFlush`, so the violation surfaces inside the method. `delete` must add an explicit `flush()` after `teamRepository.delete(team)` — without it the FK violation fires at commit, outside the catch. Unit tests mirror `signup_shouldTranslateToConflict_whenUniqueConstraintRace`.

### D3 — F-07: name length bound at 100
`@Size(max = 100)` on `TeamRequest.name` for the fast 400, plus the authoritative check in `TeamService.normalize` after trimming (the DTO check sees the untrimmed value; service check is what the spec guarantees). 100 chars is a UX-sane bound far below the ~2.7 KB index limit.

### D4 — F-08: async verification email dispatch
Add `@Async` to `VerificationEmailDispatcher.onVerificationEmailRequested` with `@EnableAsync` and a dedicated small `ThreadPoolTaskExecutor` bean (named executor, 1–2 threads — email volume is trivial). Set Spring 6.1's `ContextPropagatingTaskDecorator` so MDC/log context follows the task. `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` compose fine — the listener fires after commit, on the executor thread. Existing `MailException` catch stays (widening it is F-23, Low, out of scope). Test impact: `VerificationSteps` already must wait for async mail (D9), which also covers this.

### D5 — F-09: 503 from the JWT filter on denylist-store outage
Catch `DataAccessException` (covers `RedisConnectionFailureException` and query timeouts) around `jwtService.validateAccessToken` in `JwtAuthenticationFilter`, clear the security context, write an RFC 9457 problem detail with status 503 directly to the response, and return without continuing the chain. Direct write is required — the filter runs before the DispatcherServlet, so `GlobalExceptionHandler` can't see it. Body mirrors `ProblemDetailFactory` output (generic "service unavailable" detail, no infra internals). Fail-closed is preserved: the request never proceeds authenticated.

### D6 — F-10: Lua script for the rate-limiter window
Replace the INCR-then-EXPIRE pair in `FixedWindowRateLimiter.checkAndIncrement` with one `DefaultRedisScript<Long>`: `INCR` + `PEXPIRE ... NX`-equivalent (`if count == 1 then PEXPIRE`) in a single atomic script returning the count. One round trip, no crash window, no new dependency (script support ships with `spring-data-redis`). `retryAfterSeconds` unchanged. Unit tests updated from `opsForValue().increment` stubs to `execute(script, keys, args)` stubs; the null-count fail-open branch gets pinned (F-20).

### D7 — F-04: deterministic BDD user lookup + skeleton cleanup
"The user's email is verified" step looks up by the scenario's known email (`findByEmail`), as `SkeletonSteps.aRegisteredAndVerifiedUserWithEmailAndPassword` already does. Add `@After("@skeleton")` hook deleting the skeleton user. Both remove the `findAll().get(size-1)` order-dependence.

### D8 — F-05: BDD login failure coverage
One `Scenario Outline` in `auth-session.feature` for wrong-password 401 and unverified 403 (same step shape, data differs), plus one standalone 429 scenario asserting the `Retry-After` header — the rate-limit flow needs extra Given steps (5 prior attempts), so it stays standalone per test conventions.

### D9 — F-18: exact GreenMail waits
Positive checks use `greenMail.waitForIncomingEmail(timeoutMs, expectedCount)` and then assert the exact count; the negative check keeps a short bounded wait then asserts zero. Kills both the false-passing `<=` and the fixed sleeps. (Async dispatch from D4 makes this mandatory, not just nice.)

### D10 — F-17: deadline polling, no new dep
Awaitility is not on the classpath and one call site doesn't justify it. Replace `Thread.sleep(1500)` with a plain poll loop: check the denylist every ~100 ms until true or a ~5 s deadline, then assert. Slow-CI-safe upper bound, exits fast in practice.

### D11 — F-16: narrow the JaCoCo exclusion
Replace `**/config/**` with wiring-only patterns (e.g. `**/config/*Config.*`) so `config/properties/**` (validating compact constructors) and `ValkeyStartupValidator` are measured. Add the missing test for the validator's failure branch. If other pure-wiring classes fall under the gate, exclude them by name — never by the whole package.

### D12 — F-19/F-20: test cleanup and branch coverage
Delete `CurrentUserProviderTest` (tests lambdas defined in the test), `DomainExceptionTest` (reflection over trivial ctors), and the private-constructor guard in `ProblemDetailFactoryTest`. Shrink `LoginRateLimiterTest`/`ResendRateLimiterTest` to one wiring assertion each (prefix, limit, message) — the limiter itself is tested once in `FixedWindowRateLimiterTest`. Add missing branches: limiter `count == null` fail-open (pins the decision), `JwtService.parse` non-UUID `sub` / missing-`iat` fallback / `exp == now` boundary (extend the existing `@MethodSource`), `LogoutService` non-positive TTL, and a real `RawTokenGeneratorTest` (32 bytes, URL-safe base64, distinct across calls).

### D13 — F-03: `permitAll` the mock board
`SecurityConfig` gets `.requestMatchers("/api/v1/mock/board").permitAll()` with a comment tying it to E12 removal. Chosen over documenting the 401 in README because the mock board exists precisely to demo the stock compose stack.

### D14 — F-13: poll Mailpit search API
`auth.setup.ts` replaces `waitForTimeout(2000)` + newest-global-message with `expect.poll` against `GET /api/v1/search?query=to:${TEST_EMAIL}`, taking the newest hit for that recipient. Recipient filter removes the pre-populated-inbox hazard; polling removes the fixed sleep.

### D15 — F-14: compose abort signals
`apiFetch` builds `AbortSignal.any([AbortSignal.timeout(ms), init.signal].filter(Boolean))` so a caller signal and the timeout both abort. `getMockBoard` forwards the TanStack Query `queryFn` context signal. `AbortSignal.any`/`timeout` are baseline in all targets (Vite/modern browsers, Node 20 test env).

### D16 — F-02/F-11/F-15: infra one-liners
- `docker-compose.yml`: `ports: ["8080:8080"]` on `be`; append `http://localhost:5173` to the default `YAJ_CORS_ALLOWED_ORIGINS`. README URLs corrected to match.
- `Makefile`: `fe-lint` runs ESLint + `npm run format:check` + `tsc --noEmit`, matching CI's gate (also makes README's "lint and format checks" description true — half of F-30 for free).

## Risks / Trade-offs

- [D4 async executor swallows dispatch failures further from the request] → failures were already post-commit and invisible to the client; the existing WARN log remains the observability path.
- [D5 duplicates a small piece of problem-detail rendering inside the filter] → accepted; wiring the filter into `HandlerExceptionResolver` is more machinery for one status. Keep the body shape in one private helper.
- [D6 changes limiter unit tests from value-op stubs to script stubs] → tests assert observable behavior (throw/no-throw, retry-after), not the script text.
- [D1 JVM-clock timestamps can skew from DB `now()` used for `created_at`] → same trade-off already shipped for `Team`; irrelevant at this project's scale.
- [D13 leaves an unauthenticated endpoint] → mock data only, removal tracked by E12.

## Migration Plan

No data migration. All changes deploy together in one branch; rollback is a revert. The JaCoCo gate change (D11) may fail the build until the validator test lands — same commit.

## Open Questions

None — all decisions above follow fixes already prescribed in the catalog.
