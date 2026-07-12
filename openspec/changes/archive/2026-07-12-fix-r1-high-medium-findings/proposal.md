# fix-r1-high-medium-findings

## Why

The R1 whole-codebase review (`requirements/improvements-catalog.md`) logged 5 High and 14 open Medium findings: broken behavior (dead host ports, mock board behind auth, `modified_at` never persisting), flake sources in BDD/e2e suites, and real defects with narrow blast radius (races surfacing as 500, timing oracles, non-atomic rate limiting). Fixing them now, before the next feature epics build on these paths, is cheaper than after.

## What Changes

Addresses F-01–F-11 and F-13–F-20 (F-12 wontfixed). Grouped:

**Backend behavior**
- F-01: `@UpdateTimestamp` on `User`, `Epic`, `Ticket` (`Team` already fixed); BDD rename step re-GETs instead of trusting the response body
- F-06: `TeamService` create/rename/delete catch `DataIntegrityViolationException` → `ConflictException` (mirror `SignupService`)
- F-07: team name bounded to 100 chars (`@Size` on DTO + service check) → 400 instead of raw 500
- F-08: verification email dispatch goes `@Async` — resend latency no longer an account-existence oracle
- F-09: Valkey outage during JWT denylist check → deliberate 503 problem detail (fail-closed), not a container error
- F-10: rate limiter INCR/EXPIRE made atomic (single Lua script)

**Tests (be)**
- F-04: BDD verify-user step looks up by email; skeleton feature gets `@After` cleanup
- F-05: BDD scenarios for login 401 (bad password), 403 (unverified), 429 (rate limit + `Retry-After`)
- F-16: JaCoCo exclusion narrowed — `config/properties/**` and `ValkeyStartupValidator` measured; validator failure branch tested
- F-17: denylist IT polls with deadline instead of `Thread.sleep(1500)`
- F-18: GreenMail `waitForIncomingEmail` for exact counts instead of sleeps + `<=`
- F-19: delete misleading tests (`CurrentUserProviderTest`, `DomainExceptionTest`, private-ctor guard); shrink rate-limiter wrapper tests to wiring assertions
- F-20: unit coverage for limiter null-count fail-open, `JwtService.parse` branches, `LogoutService` non-positive TTL, `RawTokenGenerator`

**Frontend**
- F-03: `permitAll()` the mock board endpoint until FE auth (E12) lands
- F-13: e2e mail polling via Mailpit search API + `expect.poll`, no fixed sleep
- F-14: `apiFetch` combines caller signal with timeout via `AbortSignal.any`; `getMockBoard` passes the query context signal

**Infra**
- F-02: publish `be` host port 8080; correct README URLs
- F-11: add `http://localhost:5173` to default CORS origins
- F-15: `make fe-lint` adds Prettier check + typecheck to match CI

## Capabilities

### New Capabilities
- `auth-resilience`: failure-mode behavior of the auth stack — uniform-latency verification email dispatch (async), fail-closed 503 when the token denylist store is unreachable, atomic fixed-window rate-limit accounting

### Modified Capabilities
- `teams-crud`: name length bounded (400 over 100 chars); concurrent duplicate-name create/rename and delete-vs-insert races return 409, not 500; rename's advanced `modified_at` must be persisted (observable on subsequent GET)

## Impact

- **be main:** `User`/`Epic`/`Ticket` entities, `TeamService`, `TeamRequest`, `VerificationEmailDispatcher` (+ async config), `JwtAuthenticationFilter`/`JwtService`, `FixedWindowRateLimiter`, `SecurityConfig`
- **be test/bdd/it:** `AuthSessionSteps`, `SkeletonSteps`, `TeamSteps`, `VerificationSteps`, `auth-session.feature`, `TokenDenylistIntegrationTest`, deleted/shrunk unit tests, new unit cases, `pom.xml` (JaCoCo)
- **fe:** `client.ts`, `board.ts`, `e2e/auth.setup.ts`
- **infra:** `docker-compose.yml`, `Makefile`, `README.md`
- No API contract breaks: new 400/409/503 responses only replace raw 500s/errors on paths that were already failing
