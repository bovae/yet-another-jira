# Implementation Plan: Epic E4 — AuthN/AuthZ enforcement + current-user

## Overview

This plan turns the approved design into incremental coding steps for the `be` Spring Boot module
(Java 21, Spring Boot 3.5.x, Spring Security 6.5.x). E4 makes authentication mandatory by reusing the
E3 token machinery rather than re-implementing it: a `JwtAuthenticationFilter` populates the
`SecurityContext`, `SecurityConfig` is rewritten from permit-all to default-deny, a
`ProblemAuthenticationEntryPoint` writes the uniform RFC 9457 `401`, and `CurrentUserProvider` gains
its first implementation so `GET /api/v1/auth/me` resolves the caller through the enforced context.
Each step builds on the previous one and ends by wiring the new code into `SecurityConfig` and
`AuthController`, leaving no orphaned code. The project stays green at every checkpoint: the Maven
build (Spotless, Error Prone + NullAway, JaCoCo 90/90) and all unit / web-slice / BDD / integration
tests pass.

Testing follows the project's three-layer standard only — **unit (JUnit 5 + Mockito)**, a
**web-slice** test (`@WebMvcTest` with the security filters enabled), **integration** (`src/it`,
Testcontainers), and **BDD** (Cucumber, `src/bdd`, `@auth`, Testcontainers PostgreSQL + Valkey under
the `bdd` profile). There is **no property-based testing and no property-testing library**. Where
failure cases share structure, use `@ParameterizedTest` tables (`@MethodSource` / `@CsvSource`) per the
test conventions; test methods are named `methodUnderTest_shouldExpectedBehavior_whenCondition` and use
specific assertions.

Sequencing follows the design's component dependencies: the three new standalone components
(`JwtAuthenticationFilter`, `ProblemAuthenticationEntryPoint`, `CurrentUserProviderImpl`) and their
unit tests first → the `SecurityConfig` rewrite that wires them → the `/me` refactor through the
enforced context and the controller signature change → the enforcement contract tests (web-slice +
integration) → the `@auth` BDD scenario → final quality-gate verification. E4 adds **no new
dependency, no new DTO, no new entity, and no new configuration property**.

## Tasks

- [x] 1. JwtAuthenticationFilter — per-request bearer authentication (`auth.jwt`)
  - [x] 1.1 Implement `JwtAuthenticationFilter`
    - Create `auth.jwt.JwtAuthenticationFilter extends OncePerRequestFilter` with constructor injection
      of `BearerTokenExtractor` + `JwtService` (a plain class, not a `@Component`, so Spring Boot does
      not also auto-register it as a top-level servlet filter). In `doFilterInternal`, read
      `HttpHeaders.AUTHORIZATION`; when it is non-null and the `SecurityContext` holds no authentication,
      extract the raw token via `bearerTokenExtractor.extract`, validate via
      `jwtService.validateAccessToken`, and set a 3-arg
      `UsernamePasswordAuthenticationToken(claims.subject(), null, List.of())` into the context; catch
      `UnauthorizedException`, clear the context, and never block; always invoke `filterChain.doFilter`
      exactly once. The principal is the `sub` `UUID` — never a `UserDetails`, never a loaded `User`.
      Log nothing about the header or token.
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 11.4, 12.1, 12.2_

  - [x] 1.2 Write `JwtAuthenticationFilterTest` unit test
    - Mock `BearerTokenExtractor` + `JwtService`; drive `doFilterInternal` with a
      `MockHttpServletRequest` / `MockHttpServletResponse` and a mock `FilterChain`; clear
      `SecurityContextHolder` in `@AfterEach`. Assert a valid token sets a
      `UsernamePasswordAuthenticationToken` whose principal equals the token `sub` `UUID`; parametrize
      (`@MethodSource`) the no-header, blank-header, non-`bearer` scheme, and `validateAccessToken`-throws
      cases (stand-ins for expired / denylisted / malformed) each leaving `getAuthentication()` null;
      verify `chain.doFilter` is invoked exactly once in every case with `verify(chain, times(1))`.
    - _Requirements: 15.1, 1.2, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4_

- [x] 2. ProblemAuthenticationEntryPoint — uniform RFC 9457 `401` (`web.error`)
  - [x] 2.1 Implement `ProblemAuthenticationEntryPoint`
    - Create `web.error.ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint`
      (`@Component`), constructor-injecting the Spring Boot `ObjectMapper`. `commence` builds a
      `ProblemDetail` via `ProblemDetailFactory.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
      <fixed generic detail>)`, sets the response status to `401`, content type
      `application/problem+json`, and writes the body with `objectMapper.writeValue`. It never copies the
      `AuthenticationException` message, never returns `403`, and never issues a redirect, so no stack
      trace, internal type name, SQL, secret, or token can leak.
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 12.3, 14.2, 14.3_

  - [x] 2.2 Write `ProblemAuthenticationEntryPointTest` unit test
    - Use a real Jackson `ObjectMapper`, a `MockHttpServletResponse`, and seed the MDC correlation id;
      clear the MDC in `@AfterEach`. Assert status `401`, content type `application/problem+json`, body
      `status` = `401`, a non-empty `correlationId`, and an ISO-8601 UTC `timestamp`; assert the body
      carries no token, secret, stack-trace, internal-type-name, or SQL substring.
    - _Requirements: 6.1, 6.2, 6.4, 7.1, 12.3_

- [x] 3. CurrentUserProviderImpl — current-user accessor (`security`)
  - [x] 3.1 Implement `CurrentUserProviderImpl`
    - Create `security.CurrentUserProviderImpl implements CurrentUserProvider` (`@Component`).
      `requireCurrentUserId` reads `SecurityContextHolder.getContext().getAuthentication()` and throws
      `UnauthorizedException` when it is null, not authenticated, or an `AnonymousAuthenticationToken`;
      returns the principal when it is a `UUID`; otherwise throws `UnauthorizedException`. No
      `UserDetails` and no `User` entity is materialized.
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 13.2_

  - [x] 3.2 Write `CurrentUserProviderImplTest` unit test
    - Set the `SecurityContextHolder` authentication per case; clear it in `@AfterEach`. Assert a
      `UsernamePasswordAuthenticationToken` with a `UUID` principal returns that `UUID`; parametrize
      (`@MethodSource`) a null authentication, an `AnonymousAuthenticationToken`, and a non-`UUID`
      principal each raising `UnauthorizedException`.
    - _Requirements: 15.2, 8.2, 8.3_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure the build, Spotless, Error Prone + NullAway, and all tests pass; ask the user if questions arise.

- [x] 5. Rewrite SecurityConfig to default-deny and wire the filter + entry point (`config`)
  - [x] 5.1 Rewrite `SecurityConfig`
    - Remove the `SKELETON ONLY` warning and the `anyRequest().permitAll()` rule. Inject
      `BearerTokenExtractor`, `JwtService`, and `ProblemAuthenticationEntryPoint`. In
      `authorizeHttpRequests`, `permitAll` the four `PUBLIC_AUTH_ENDPOINTS`
      (`/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/verify`,
      `/api/v1/auth/verification/resend`), `POST /api/v1/auth/logout`, and `/actuator/health/**`; make
      `anyRequest().authenticated()` the default-deny backstop (so `/actuator/info`, `/api/v1/mock/board`,
      and `/api/v1/auth/me` are protected). Wire `exceptionHandling(e ->
      e.authenticationEntryPoint(authenticationEntryPoint))` and `addFilterBefore(new
      JwtAuthenticationFilter(bearerTokenExtractor, jwtService),
      UsernamePasswordAuthenticationFilter.class)`. Retain the existing `CorsConfigurationSource`,
      CSRF-disabled, and `STATELESS` posture.
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.4, 5.4, 10.1, 11.1, 11.2, 11.3, 13.1, 13.4_

- [x] 6. Resolve `/me` through the enforced context
  - [x] 6.1 Refactor `CurrentUserService` to use `CurrentUserProvider`
    - Replace the `BearerTokenExtractor` + `JwtService` dependencies with `CurrentUserProvider`. Change
      `me()` to take no header argument: call `currentUserProvider.requireCurrentUserId()`, load via
      `userRepository.findById`, raise `UnauthorizedException` when the user is absent or its `deletedAt`
      is non-null, and otherwise return `MeResponse.from(user)` (no hash, no token). The filter already
      enforced signature/expiry/denylist, so the service no longer re-parses the header — it performs
      only the liveness / soft-delete check the filter intentionally omits.
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 13.2_

  - [x] 6.2 Change `AuthController.me` signature
    - Drop the `@RequestHeader Authorization` parameter from `me()` and delegate to
      `currentUserService.me()`. Leave `logout(@Nullable authorization)` and the
      `signup` / `verify` / `resend` / `login` handlers unchanged.
    - _Requirements: 9.1, 10.1, 10.4_

  - [x] 6.3 Update `CurrentUserServiceTest`
    - Replace the `BearerTokenExtractor` / `JwtService` mocks with a `CurrentUserProvider` mock (drop the
      `Bearer ...` header fixtures). Assert a resolvable live user returns a `MeResponse` with
      `id` / `email` / `emailVerified` and no hash or token; parametrize the `findById`-empty and
      soft-deleted cases each raising `UnauthorizedException`; assert a provider-thrown
      `UnauthorizedException` propagates.
    - _Requirements: 9.2, 9.3, 9.5_

  - [x] 6.4 Update `AuthControllerTest` for the `me` signature change
    - Update the `me` case so it no longer passes an `Authorization` header; verify
      `currentUserService.me()` is invoked and the JSON omits `passwordHash` / `hash` / `Set-Cookie`.
      Keep the existing `signup` / `verify` / `resend` / `login` / `logout` cases and the
      `@AutoConfigureMockMvc(addFilters = false)` slice setup.
    - _Requirements: 9.3_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure the build, Spotless, Error Prone + NullAway, and all tests pass; ask the user if questions arise.

- [x] 8. Enforcement contract tests (web-slice + integration)
  - [x] 8.1 Write `AuthEnforcementSliceTest` (`@WebMvcTest`, filters enabled)
    - `@WebMvcTest` over `/api/v1/auth/me` importing the real `SecurityConfig` +
      `ProblemAuthenticationEntryPoint` with the security filters **enabled** (no `addFilters = false`),
      providing `BearerTokenExtractor` and `JwtService` as `@MockitoBean`. Assert a request with no
      `Authorization` header receives `401` with `application/problem+json` and body `status` = `401`,
      and that a request whose stubbed token validates is allowed through to the handler (`200`).
      Parametrize a variant asserting `/api/v1/mock/board` and `/actuator/info` also `401` without a
      token while `/actuator/health` is reachable.
    - _Requirements: 15.3, 5.1, 5.2, 5.4, 4.2, 4.4, 6.1_

  - [x] 8.2 Write `MeSoftDeleteRejectionIntegrationTest` under `src/it`
    - Extend `AbstractPostgresIntegrationTest` (real PostgreSQL via Testcontainers). Seed a user,
      soft-delete it (`deletedAt` non-null), set the `SecurityContext` principal to that user's id, and
      assert `CurrentUserService.me()` raises `UnauthorizedException`; clear `SecurityContextHolder`
      afterward. Proves the filter-passes / handler-rejects split (decision 4) against a real row.
    - _Requirements: 9.5_

- [x] 9. BDD enforcement scenario (`@auth`)
  - [x] 9.1 Add the enforcement scenario and one new step
    - Add a scenario to `auth-session.feature` (tag `@auth`): sign up → verify → request current-user
      with **no** access token → `401` → log in → `200` + access token → request current-user with the
      token → `200` + email. Add the single new step `the user requests current-user without an access
      token` to `AuthSessionSteps` issuing `GET /api/v1/auth/me` with no `Authorization` header, reusing
      `SharedScenarioState`, `UserRepository`, and `RestTemplate` exactly as the existing steps do.
      Confirm the existing login → `/me` `200` → logout → `/me` `401` scenario still passes, now flowing
      through the real filter + entry point.
    - _Requirements: 15.4, 5.1, 6.1, 9.2_

- [x] 10. Final checkpoint - Verify quality gates
  - Run the full build under the `bdd` profile plus the `src/it` integration execution; ensure Spotless
    and Error Prone + NullAway pass with no violations and JaCoCo meets 90% line and 90% branch over the
    non-excluded packages — covering `JwtAuthenticationFilter` (`auth.jwt`),
    `ProblemAuthenticationEntryPoint` (`web.error`), and `CurrentUserProviderImpl` (`security`). Ask the
    user if questions arise.
  - _Requirements: 15.5, 15.6, 15.7_

## Notes

- No task uses property-based testing or a property-testing library (no jqwik). The new components are a
  servlet filter, a Spring Security configuration, a small context-reading accessor, and an entry point
  — none has a "for all inputs" surface that PBT would serve. The design's "Key Behaviors and
  Invariants" are verified by example-based unit tests plus `@ParameterizedTest` tables, the
  `AuthEnforcementSliceTest` web-slice test, the `MeSoftDeleteRejectionIntegrationTest` integration test,
  and the `@auth` BDD scenario.
- Test sub-tasks are not deferred. The build's Definition of Done depends on them: Requirements
  15.1–15.4 name specific unit / web-slice / BDD tests, and the JaCoCo 90/90 gate (15.5, 15.7) is only
  met once they are in place — `JwtAuthenticationFilter`, `ProblemAuthenticationEntryPoint`, and
  `CurrentUserProviderImpl` all fall in non-excluded packages.
- `SecurityConfig` lives in the JaCoCo-excluded `config` package and is exercised end-to-end by the
  web-slice and BDD layers rather than a direct unit test. `MeResponse` (`web.dto`) and the reused
  `auth.jwt` types (`JwtService`, `BearerTokenExtractor`, `TokenClaims`, `TokenDenylist`) are already
  covered by E3.
- The BDD harness already provisions Testcontainers PostgreSQL + Valkey and registers `yaj.jwt.*` in
  `TestcontainersConfig.registerProperties`, so no new harness wiring is required (unlike E3).
- Each task references the specific requirement clauses it satisfies and names the design components for
  traceability. Checkpoints provide incremental validation; the project stays green between them.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "3.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "3.2", "5.1", "6.1"] },
    { "id": 2, "tasks": ["6.2", "6.3"] },
    { "id": 3, "tasks": ["6.4", "8.1", "8.2"] },
    { "id": 4, "tasks": ["9.1"] }
  ]
}
```
