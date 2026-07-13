## 1. Backend behavior — entities & teams (F-01, F-06, F-07)

- [x] 1.1 Replace `@Generated`/`@ColumnDefault("now()")` with `@UpdateTimestamp` on `modifiedAt` in `User`, `Epic`, `Ticket` (D1)
- [x] 1.2 Wrap `TeamService.create`/`rename` writes in `catch (DataIntegrityViolationException)` → `ConflictException`; add explicit `flush()` inside the try in `delete` (D2)
- [x] 1.3 Add unit tests: create/rename/delete race translates to conflict, mirroring `signup_shouldTranslateToConflict_whenUniqueConstraintRace` (D2)
- [x] 1.4 Add `@Size(max = 100)` to `TeamRequest.name` and the post-trim length check in `TeamService.normalize`; unit tests for the 400 paths (D3)

## 2. Backend behavior — auth resilience (F-08, F-09, F-10)

- [x] 2.1 Add `@EnableAsync` + dedicated `ThreadPoolTaskExecutor` bean with `ContextPropagatingTaskDecorator`; annotate `VerificationEmailDispatcher.onVerificationEmailRequested` with `@Async` (D4)
- [x] 2.2 In `JwtAuthenticationFilter`, catch `DataAccessException` around token validation: clear context, write RFC 9457 503 problem detail, short-circuit the chain; unit test the 503 body and fail-closed behavior (D5)
- [x] 2.3 Replace INCR/EXPIRE in `FixedWindowRateLimiter` with a single atomic `DefaultRedisScript` (increment + set window TTL on first hit); update `FixedWindowRateLimiterTest` stubs (D6)

## 3. Backend tests — BDD (F-04, F-05, F-18)

- [x] 3.1 Change `AuthSessionSteps` verify-user step to look up by the scenario email; add `@After("@skeleton")` cleanup hook (D7)
- [x] 3.2 Add `Scenario Outline` for login 401 (wrong password) / 403 (unverified) and a standalone 429 scenario asserting `Retry-After` in `auth-session.feature` (D8)
- [x] 3.3 Rework `VerificationSteps` email counts: `waitForIncomingEmail(timeout, count)` + exact-count assert for positives, bounded wait + zero assert for the negative (D9)
- [x] 3.4 Change the BDD rename-timestamp step in `TeamSteps` to re-GET the team and assert the fetched `modified_at` (D1)

## 4. Backend tests — unit/IT/coverage (F-16, F-17, F-19, F-20)

- [x] 4.1 Replace `Thread.sleep(1500)` in `TokenDenylistIntegrationTest` with a deadline poll loop (~100 ms interval, ~5 s deadline) (D10)
- [x] 4.2 Narrow JaCoCo excludes in `be/pom.xml`: keep wiring-only `**/config/*Config.*`, measure `config/properties/**` and `ValkeyStartupValidator` (D11)
- [x] 4.3 Add `ValkeyStartupValidator` failure-branch test (D11)
- [x] 4.4 Delete `CurrentUserProviderTest`, `DomainExceptionTest`, and the private-constructor test in `ProblemDetailFactoryTest`; shrink `LoginRateLimiterTest`/`ResendRateLimiterTest` to wiring assertions (D12)
- [x] 4.5 Add branch coverage: limiter `count == null` fail-open, `JwtService.parse` non-UUID `sub` / missing `iat` / `exp == now` (extend `@MethodSource`), `LogoutService` non-positive TTL, new `RawTokenGeneratorTest` (D12)
- [x] 4.6 Run the full be suite + coverage gate green: `unit`, `it`, `bdd` profiles

## 5. Frontend (F-03, F-13, F-14)

- [x] 5.1 `permitAll()` `/api/v1/mock/board` in `SecurityConfig` with an E12-removal comment (D13)
- [x] 5.2 Rework `fe/e2e/auth.setup.ts`: `expect.poll` on Mailpit `GET /api/v1/search?query=to:${TEST_EMAIL}`, newest hit for that recipient, no fixed sleep (D14)
- [x] 5.3 `apiFetch`: combine timeout + caller signal via `AbortSignal.any`; forward the queryFn context signal in `getMockBoard`; unit test caller-abort propagation (D15)
- [x] 5.4 Run fe unit + e2e suites green

## 6. Infra (F-02, F-11, F-15)

- [x] 6.1 Publish `8080:8080` on the `be` service in `docker-compose.yml`; fix README host URLs (D16)
- [x] 6.2 Add `http://localhost:5173` to the default `YAJ_CORS_ALLOWED_ORIGINS` in `docker-compose.yml` (D16)
- [x] 6.3 Extend `make fe-lint` with `npm run format:check` and `tsc --noEmit` (D16)
- [x] 6.4 Verify stock `docker compose up` end-to-end: board renders, `http://localhost:8080/actuator/health` responds

## 7. Wrap-up

- [x] 7.1 Tick F-01–F-11, F-13–F-20 checkboxes in `requirements/improvements-catalog.md`
