# Implementation Plan: Epic E3 — Login / logout (JWT + denylist)

## Overview

This plan turns the approved design into incremental coding steps for the `be` Spring Boot module
(Java 21, Spring Boot 3.5.x). Each step builds on the previous one and ends by wiring the new code into
the existing `AuthController`, leaving no orphaned code. The project stays green at every checkpoint:
the Maven build (Spotless, Error Prone + NullAway, JaCoCo 90/90) and all unit / BDD / integration tests
pass.

Testing follows the project's three-layer standard only — **unit (JUnit 5 + Mockito)**, **BDD (Cucumber,
`src/bdd`, `@auth`)**, and **integration (`src/it`, Testcontainers)**. There is no property-based testing
and no property-testing library. Where failure cases share structure, use `@ParameterizedTest` tables
(`@MethodSource` / `@CsvSource`) per the test conventions; test methods are named
`methodUnderTest_shouldExpectedBehavior_whenCondition` and use specific assertions.

Sequencing follows the design's component dependencies: foundation (deps, config, error mapping) →
`auth.jwt` core → login flow → logout / me services → controller wiring → real-infrastructure tests →
final quality-gate verification.

## Tasks

- [x] 1. Foundation: JWT dependency, configuration, and 403 error mapping
  - [x] 1.1 Add the JJWT dependency to `be/pom.xml`
    - Add a `<jjwt.version>0.12.6</jjwt.version>` property and three dependencies: `jjwt-api` (compile),
      `jjwt-impl` (runtime), `jjwt-jackson` (runtime), exactly as listed in the design's research summary.
    - Use the confirmed `io.jsonwebtoken` `0.12.x` fluent API surface (`Jwts.SIG.HS256`, `verifyWith`,
      `parseSignedClaims`); do not introduce the deprecated `0.11.x` API.
    - _Requirements: 15.1, 3.4_

  - [x] 1.2 Add `JwtProperties` and wire `yaj.jwt` configuration
    - Create `config.properties.JwtProperties` as a `@Validated @ConfigurationProperties("yaj.jwt")`
      record with `@NotBlank @Size(min = 32) String secret` and `@NotNull Duration tokenTtl`, plus a
      compact-constructor guard rejecting a zero or negative `tokenTtl` — mirroring `VerificationProperties`.
    - Add the `yaj.jwt.secret: ${YAJ_JWT_SECRET}` and `yaj.jwt.token-ttl: 1h` keys to `application.yml`
      with no default secret, so a missing/blank secret fails startup; rely on the existing
      `@ConfigurationPropertiesScan` for registration.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 3.5, 11.3_

  - [x] 1.3 Write `JwtPropertiesTest` unit test
    - Assert the compact-constructor guard throws for zero and negative `tokenTtl` and accepts a positive
      duration (parametrize the rejected durations with `@MethodSource`).
    - _Requirements: 12.4_

  - [x] 1.4 Add `ForbiddenException` and its 403 mapping in `GlobalExceptionHandler`
    - Create `error.ForbiddenException extends RuntimeException` mirroring the existing typed exceptions.
    - Add a `handleForbidden` method to `web.error.GlobalExceptionHandler` mapping `ForbiddenException`
      to a 403 RFC 9457 problem built through `ProblemDetailFactory`, same shape as `handleUnauthorized`.
    - _Requirements: 14.2, 4.2_

  - [x] 1.5 Write `GlobalExceptionHandler` mapping test for `ForbiddenException`
    - Assert `ForbiddenException` maps to status 403 with body `status = 403`, a non-empty
      `correlationId`, and a UTC ISO-8601 `timestamp`; assert the body carries no stack trace, internal
      type name, SQL, password, hash, secret, or token.
    - _Requirements: 14.2, 14.4, 14.5_

- [x] 2. Implement the `auth.jwt` core (claims, denylist, JWT service, bearer extractor)
  - [x] 2.1 Add the `TokenClaims` record
    - Create `auth.jwt.TokenClaims(UUID subject, String jti, Instant issuedAt, Instant expiresAt)` as the
      validated claim carrier returned by `JwtService`.
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 2.2 Implement `TokenDenylist` over `StringRedisTemplate`
    - Create `auth.jwt.TokenDenylist` with `revoke(String jti, Duration ttl)` doing
      `opsForValue().set("auth:jwt:denylist:" + jti, "1", ttl)` and `contains(String jti)` doing
      `hasKey(...)`, following the `ResendRateLimiter` ephemeral discipline; the marker holds no user id,
      email, or token material.
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 7.3_

  - [x] 2.3 Write `TokenDenylistTest` unit test
    - Mock `StringRedisTemplate` (+ `ValueOperations`); verify `revoke` calls `set` with the prefixed key,
      `"1"` marker, and the passed TTL (capture args with `@Captor`); verify `contains` mirrors `hasKey`.
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 2.4 Implement `JwtService` (issue, validate, parse-for-revocation)
    - Build the `SecretKey` once via `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` from `JwtProperties`;
      inject the UTC `Clock`. `issue(UUID)` sets `sub`/`jti`(random UUID)/`iat`(now)/`exp`(now + tokenTtl)
      and signs HS256. A shared private `parse` verifies signature + expiry (pinning the injected `Clock`)
      and required `sub`/`jti`/`exp`, wrapping every `JwtException` and any missing claim as
      `UnauthorizedException`. `validateAccessToken` = parse + denylist check; `parseForRevocation` = parse
      only (no denylist).
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 7.1, 7.2, 7.3, 7.4, 7.5, 9.5, 12.4_

  - [x] 2.5 Write `JwtServiceTest` unit test
    - Use a real `SecretKey` from an in-test secret (>= 32 chars) and a fixed `Clock`; mock `TokenDenylist`.
      Assert the issue/validate round trip recovers `sub`, `iat`, and `exp` with a valid signature; two
      issuances yield distinct `jti`; validation rejects malformed, signature-tampered, expired,
      missing-`sub`/`jti`/`exp`, and denylisted tokens with `UnauthorizedException` (parametrize the
      rejection cases via `@MethodSource`).
    - _Requirements: 15.6, 3.1, 3.2, 3.3, 3.6, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 2.6 Implement `BearerTokenExtractor`
    - Create `auth.jwt.BearerTokenExtractor` with `extract(@Nullable String authorizationHeader)` that
      accepts only a case-insensitive `Bearer <token>` scheme with a non-blank token and raises
      `UnauthorizedException` when the header is absent or malformed.
    - _Requirements: 8.2, 8.6, 10.2, 10.5_

  - [x] 2.7 Write `BearerTokenExtractorTest` unit test
    - Assert a well-formed `Bearer <token>` returns the raw token; parametrize null, blank, wrong-scheme,
      and missing-token headers (`@MethodSource` / `@CsvSource`) and assert each raises
      `UnauthorizedException`.
    - _Requirements: 8.6, 10.5_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure the build, Spotless, Error Prone + NullAway, and all tests pass; ask the user if questions arise.

- [x] 4. Implement the login flow (DTOs + `LoginService`)
  - [x] 4.1 Add the request/response DTO records
    - Create `web.dto.LoginRequest(@Nullable String email, @Nullable String password)`,
      `web.dto.LoginResponse(String accessToken, String tokenType, long expiresInSeconds)` with a
      `bearer(...)` factory, and `web.dto.MeResponse(UUID id, String email, boolean emailVerified)` with a
      `from(User)` factory; the token lives only in the body and no response carries the password hash.
    - _Requirements: 1.2, 2.3, 2.4, 2.5, 10.3, 10.4_

  - [x] 4.2 Implement `LoginService`
    - Create `auth.login.LoginService` (`@Transactional(readOnly = true)`): reject blank email / empty
      password with `ValidationException` before any lookup; trim the email and look up via
      `userRepository.findByEmail` (citext, case-insensitive); treat absent-or-soft-deleted as
      no-active-account, run `passwordEncoder.matches` once against a dummy hash computed at construction,
      then throw the uniform `UnauthorizedException`; for an active account, wrong password throws the same
      uniform `UnauthorizedException`, correct password on an unverified account throws `ForbiddenException`,
      and correct password on a verified account calls `jwtService.issue(user.getId())` and builds
      `LoginResponse`. Never log the password, hash, secret, or token.
    - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 4.1, 4.3, 5.1, 5.3, 6.1, 6.2, 6.4, 11.2, 11.4_

  - [x] 4.3 Write `LoginServiceTest` unit test
    - Mock `UserRepository`, `PasswordEncoder`, `JwtService`. Cover: verified active account + correct
      password issues exactly one resolvable token; correct password on an unverified account throws
      `ForbiddenException` and issues no token; blank email / empty password throws `ValidationException`
      with `verifyNoInteractions(userRepository, passwordEncoder)`; trimmed/case-insensitive lookup; the
      no-active-account path calls `passwordEncoder.matches` exactly once against the dummy hash
      (`verify(..., times(1))`). Parametrize the three uniform 401 branches (unknown email, wrong password,
      soft-deleted) with `@MethodSource` asserting the same exception and message; keep the 403 and success
      cases standalone.
    - _Requirements: 15.2, 15.3, 15.4, 1.4, 1.5, 2.1, 4.1, 5.2, 6.3, 6.4_

- [x] 5. Implement the logout and current-user services
  - [x] 5.1 Implement `LogoutService`
    - Create `auth.logout.LogoutService`: extract the token via `BearerTokenExtractor` (401 if
      missing/malformed), parse it via `jwtService.parseForRevocation` (401 if malformed/bad-sig/expired),
      compute `ttl = Duration.between(clock.instant(), claims.expiresAt())`, guard `ttl > 0`, then
      `tokenDenylist.revoke(claims.jti(), ttl)`; returns void so the controller maps to 204. Idempotent
      because `revoke` (SET with TTL) overwrites an existing entry.
    - _Requirements: 8.2, 8.3, 8.4, 8.5, 8.6, 9.3_

  - [x] 5.2 Write `LogoutServiceTest` unit test
    - Mock `JwtService`, `TokenDenylist`; fixed `Clock`. Assert a valid, unexpired token records its `jti`
      with a TTL derived from `exp` (capture key + TTL with `@Captor`); an already-denylisted token still
      succeeds and re-records (idempotency); missing / malformed / bad-signature / expired tokens raise
      `UnauthorizedException`.
    - _Requirements: 15.5, 8.3, 8.5, 8.6, 9.3_

  - [x] 5.3 Implement `CurrentUserService`
    - Create `auth.me.CurrentUserService`: extract the token via `BearerTokenExtractor`, validate it via
      `jwtService.validateAccessToken` (signature + expiry + denylist), parse `sub` to a `UUID`
      (failure → `UnauthorizedException`), load via `userRepository.findById`, and raise
      `UnauthorizedException` when absent or soft-deleted; otherwise return `MeResponse.from(user)` with no
      hash and no token.
    - _Requirements: 10.2, 10.3, 10.4, 10.6, 7.1, 7.2, 7.3, 7.5_

  - [x] 5.4 Write `CurrentUserServiceTest` unit test
    - Mock `JwtService`, `UserRepository`. Assert a valid token resolving to a live user returns a
      `MeResponse` with `id` / `email` / `emailVerified` and no hash or token; a `sub` resolving to no user
      or a soft-deleted user raises `UnauthorizedException`; extractor / validation failures propagate as
      401.
    - _Requirements: 10.3, 10.4, 10.6_

- [x] 6. Wire the three handlers into `AuthController`
  - [x] 6.1 Extend `AuthController` with `login`, `logout`, and `me` handlers
    - Add `POST /login` (200, JSON body in/out) delegating to `LoginService`; `POST /logout`
      (`@ResponseStatus(NO_CONTENT)`, `@Nullable` `Authorization` header) delegating to `LogoutService`;
      `GET /me` (200, JSON out, `@Nullable` `Authorization` header) delegating to `CurrentUserService`.
      The controller carries no rules and passes the raw `Authorization` value straight through, leaving
      `SecurityConfig` permit-all unchanged and adding no `CurrentUserProvider` implementation.
    - _Requirements: 1.1, 2.3, 2.4, 8.1, 8.4, 10.1, 13.1, 13.2, 13.3, 13.4_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure the build, Spotless, Error Prone + NullAway, and all tests pass; ask the user if questions arise.

- [x] 8. Real-infrastructure tests (integration + BDD)
  - [x] 8.1 Write `TokenDenylist` integration test under `src/it`
    - Extend `AbstractPostgresIntegrationTest` (real Valkey via Testcontainers): assert `revoke(jti, ttl)`
      writes `auth:jwt:denylist:<jti>` with a remaining TTL within expected bounds, `contains(jti)` returns
      `true` while present, and the key self-expires so `contains(jti)` returns `false` afterward.
    - _Requirements: 15.5, 9.1, 9.3, 9.5_

  - [x] 8.2 Extend `TestcontainersConfig` for the `@auth` BDD run
    - Register `yaj.jwt.secret` (>= 32 chars) and `yaj.jwt.token-ttl` in
      `TestcontainersConfig.registerProperties`, and extend the `@After("@auth")` cleanup to also delete
      `auth:jwt:denylist:*` keys.
    - _Requirements: 15.7_

  - [x] 8.3 Add the `@auth` Cucumber scenario and step definitions
    - Add `auth-session.feature` (tag `@auth`) under `src/bdd` and matching steps: sign up → verify →
      `POST /login` to obtain a token → `GET /me` asserting `id` / `email` / `email_verified` →
      `POST /logout` (204) → a subsequent `GET /me` with the same token returns 401. Reuse
      `TestRestTemplate`, `SharedScenarioState`, `UserRepository`, and `StringRedisTemplate` as `SignupSteps`
      does.
    - _Requirements: 15.7, 2.3, 2.4, 8.1, 8.4, 8.5, 10.3, 10.6_

- [x] 9. Final checkpoint - Verify quality gates
  - Run the full build under the `bdd` profile plus the `src/it` integration execution; ensure Spotless and
    Error Prone + NullAway pass with no violations and JaCoCo meets 90% line and 90% branch over the
    non-excluded packages. Ask the user if questions arise.
  - _Requirements: 15.8, 15.9, 15.10_

- [x] 10. Login rate limiting
  - [x] 10.1 Add `loginRateLimit` and `loginRateWindow` to `JwtProperties` and `application.yml`
    - Extend the `JwtProperties` record with `@Min(1) int loginRateLimit` and `@NotNull Duration loginRateWindow`.
      Add `login-rate-limit: 5` and `login-rate-window: 15m` keys to `application.yml` under `yaj.jwt`.
    - _Requirements: 16.4_

  - [x] 10.2 Implement `LoginRateLimiter`
    - Create `auth.login.LoginRateLimiter` mirroring `ResendRateLimiter`: per-email fixed window counter
      in Valkey under `auth:login:rl:` + SHA-256 hash of the lowercase email, throwing `RateLimitException`
      when the limit is exceeded.
    - _Requirements: 16.1, 16.2, 16.5, 16.6_

  - [x] 10.3 Wire `LoginRateLimiter` into `LoginService`
    - Inject `LoginRateLimiter` and call `checkAndIncrement(email)` after input validation but before the
      repository lookup, so rate-limited requests are rejected fast.
    - _Requirements: 16.1, 16.2, 16.3_

  - [x] 10.4 Write `LoginRateLimiterTest` unit test
    - Mock `StringRedisTemplate` + `ValueOperations` + `TokenHasher`. Assert: expire set on first call,
      not on subsequent; throws `RateLimitException` when limit exceeded; does not throw at the limit.
    - _Requirements: 16.2, 16.6_

  - [x] 10.5 Update `LoginServiceTest` for rate limiting
    - Add a mock for `LoginRateLimiter`; assert `RateLimitException` propagates and short-circuits DB/encoder/JWT work.
    - _Requirements: 16.2_

  - [x] 10.6 Update test infrastructure for rate-limit keys
    - Update `JwtPropertiesTest`, `JwtServiceTest`, and `LoginServiceTest` for 4-arg `JwtProperties` constructor.
      Register `yaj.jwt.login-rate-limit` and `yaj.jwt.login-rate-window` in `TestcontainersConfig`.
      Add `auth:login:rl:*` cleanup to `@After("@auth")` hook.
    - _Requirements: 16.5_

## Notes

- Tasks marked with `*` are test sub-tasks. They can be deferred for a faster first pass, but the build's
  Definition of Done depends on them: Requirements 15.2–15.7 name specific unit / BDD / integration tests,
  and the JaCoCo 90/90 gate (15.8, 15.10) is only met once the starred tests are in place. Skipping them
  is an MVP shortcut, not a final state.
- No task uses property-based testing or a property-testing library (no jqwik). The design's "Key Behaviors
  and Invariants" are verified by example-based unit tests plus `@ParameterizedTest` tables, the `@auth`
  BDD scenario, and the Testcontainers integration tests.
- `JwtProperties` lives in `config.properties` (JaCoCo-excluded `**/config/**`); the `web.dto` records,
  `TokenClaims`, and `ForbiddenException` carry no meaningful branches and are exercised through the
  service/handler tests rather than dedicated tests.
- Each task references the specific requirement clauses it satisfies and the design components by name for
  traceability. Checkpoints provide incremental validation; the project stays green between them.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.4", "2.1", "2.2", "2.6", "4.1"] },
    { "id": 1, "tasks": ["1.3", "1.5", "2.3", "2.4", "2.7", "8.1", "8.2"] },
    { "id": 2, "tasks": ["2.5", "4.2", "5.1", "5.3"] },
    { "id": 3, "tasks": ["4.3", "5.2", "5.4", "6.1"] },
    { "id": 4, "tasks": ["8.3"] },
    { "id": 5, "tasks": ["10.1", "10.2", "10.3", "10.4", "10.5", "10.6"] }
  ]
}
```
