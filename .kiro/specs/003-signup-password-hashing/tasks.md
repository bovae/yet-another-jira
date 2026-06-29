# Implementation Plan: Epic E1 — Sign-up + password hashing

> **Module:** all E1 work lives in the **`be`** Maven module
> (`be/pom.xml`, `be/src/main`, `be/src/test`, `be/src/it`, `be/src/bdd`). No other module is touched.

## Overview

This plan implements the single signup endpoint `POST /api/v1/auth/signup` described in `design.md`
and required by `requirements.md`. On top of the `002-backend-domain-foundation` persistence layer it
adds: one new runtime dependency (BouncyCastle), an externalized `yaj.signup.*` policy block, an
Argon2id `PasswordEncoder` bean, a validated `SignupProperties` record, the request/response DTOs, the
transactional `SignupService` that normalizes + validates + de-duplicates + hashes + persists, and the
thin `AuthController` that exposes the endpoint. It reuses — and does not re-implement — the E0
`User` entity, `UserRepository`, the `ValidationException`/`ConflictException` types, and the
`GlobalProblemHandler` + `ProblemDetailFactory` RFC 9457 path.

Implementation language is **Java 21 / Spring Boot 3.5.x** (taken directly from the design — no
pseudocode). Conventions follow the project's existing code, Spotless (palantir, no wildcard imports),
Error Prone + NullAway (`@NonNull`-by-default, explicit `@Nullable`), and the test conventions (JUnit 5
+ Mockito, `@ParameterizedTest` tables, Cucumber for BDD).

Dependency order honored throughout: **dependency/config → encoder bean + policy properties → DTOs →
transactional service → web controller → end-to-end wiring → full quality gate.** Each step keeps the
build compiling and the gates green; no orphaned code is introduced.

Key invariants the tasks must not break:
- **No schema change.** E1 reuses the `users` table and `User` entity exactly; the `email` column is
  PostgreSQL `citext`, so case-insensitive de-duplication happens at the database.
- **One authoritative validation path.** Validation lives in `SignupService` (throwing
  `ValidationException`/`ConflictException`); no `@Valid`/jakarta constraints on the DTO, no new
  `@RestControllerAdvice`, no parallel error shape.
- **Confidentiality is structural.** `SignupResponse` has no password/hash component; `password_hash`
  stays `@ToString.Exclude`; the service logs outcomes only (never email/password/hash).
- **Security seam untouched.** `SecurityConfig` is left permit-all; E1 issues no token, cookie, or
  session and adds no authentication filter.
- **Coverage placement.** The encoder bean and `SignupProperties` sit in the JaCoCo-excluded
  `**/config/**` packages; all branch-bearing logic stays in the gated `auth` package.

## Tasks

- [x] 1. Dependency and configuration wiring
  - [x] 1.1 Declare the BouncyCastle dependency in `be/pom.xml`
    - Add `org.bouncycastle:bcprov-jdk18on` to the main `<dependencies>` with **no** `<version>`
      (Spring Boot BOM-managed, mirroring how `commons-lang3` is declared); `Argon2PasswordEncoder`
      requires BouncyCastle on the runtime classpath.
    - Keep the POM sorted so the Spotless `sortPom` check passes; do not add a `<scope>` (default
      `compile` is correct, `runtime` is an acceptable tightening). Confirm the `enforce-thin-jar`
      antrun check stays green (BouncyCastle is expected in `BOOT-INF/lib`).
    - _Fallback:_ if the artifact resolves unmanaged (no version), pin a current `bcprov-jdk18on`
      version explicitly and record it in the pom — the only case where E1 hardcodes a third-party
      version.
    - _Requirements: 14.1, 14.2_

  - [x] 1.2 Add the `yaj.signup` policy block to `be/src/main/resources/application.yml`
    - Add `yaj.signup.min-password-length: 8`, `yaj.signup.max-password-length: 128`,
      `yaj.signup.min-email-length: 6`, and `yaj.signup.max-email-length: 254` so the policy defaults
      are externalized instead of hardcoded in `SignupService`.
    - Do not commit any plaintext production secret; this block carries only non-secret policy values.
    - _Requirements: 14.3, 14.4, 4.2_

- [x] 2. Argon2id encoder bean and externalized policy properties
  - [x] 2.1 Implement `com.bovae.yaj.config.PasswordEncoderConfig`
    - `@Configuration` exposing a `@Bean PasswordEncoder passwordEncoder()` that returns
      `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (Argon2id, current defaults, random
      per-encode salt).
    - The bean lives in `com.bovae.yaj.config` deliberately (JaCoCo-excluded `**/config/**`).
      Declaring only a `PasswordEncoder` bean must not introduce a `UserDetailsService`/
      `AuthenticationManager`, so the permit-all `SecurityConfig` seam stays unchanged.
    - _Requirements: 7.1, 12.2_

  - [x] 2.2 Implement `com.bovae.yaj.config.properties.SignupProperties`
    - `@Validated @ConfigurationProperties(prefix = "yaj.signup")` record with four validated
      components — `@Min(1) int minPasswordLength`, `@Min(1) int maxPasswordLength`,
      `@Min(1) int minEmailLength`, and `@Min(1) @Max(254) int maxEmailLength` — mirroring the existing
      `ValkeyProperties`. `maxPasswordLength` carries no `@Max` (no RFC ceiling; the 128 default is an
      Argon2id hashing-cost guard), while `maxEmailLength` is capped at the RFC 5321 limit of 254.
      Auto-registered by the `@ConfigurationPropertiesScan` already on `YetAnotherJiraApplication` —
      add no `@EnableConfigurationProperties` and no app-class change.
    - Bound from the `yaj.signup.*` values added in 1.2 (lives in JaCoCo-excluded `**/config/**`).
    - _Requirements: 14.3, 14.4_

  - [x] 2.3 Write encoder-behavior unit test `Argon2PasswordEncoderBehaviorTest` (`src/test/java`)
    - Plain JUnit 5 example-based tests against `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`
      (the same factory the bean uses), using a small fixed set of example passwords to keep Argon2
      cost low: `encode` returns a non-empty hash `!=` plaintext; `matches(raw, encode(raw))` is true
      and `matches(other, encode(raw))` is false; two `encode` calls of the same input yield different
      hashes that both verify.
    - _Requirements: 7.2, 7.3, 7.4, 7.6, 7.7_

- [x] 3. Implement the request/response DTOs (`com.bovae.yaj.web.dto`)
  - [x] 3.1 Create `SignupRequest` and `SignupResponse` records in one `web.dto` package
    - `SignupRequest(@Nullable String email, @Nullable String password)` — fields nullable on purpose
      (the service is the authoritative validator; no `@NotNull`/`@Valid`).
    - `SignupResponse(UUID id, String email, boolean emailVerified, Instant createdAt)` with a static
      `from(User user)` factory; the record has no password/hash component, so confidentiality is
      structural. `createdAt` is an `Instant` (Jackson renders ISO-8601 UTC).
    - _Requirements: 8.3, 8.4, 9.5, 10.2, 10.4_

- [x] 4. Implement `SignupService` (transactional business logic, `com.bovae.yaj.auth`)
  - [x] 4.1 Implement the `@Service @Transactional @Slf4j SignupService`
    - Constructor-inject `UserRepository`, `PasswordEncoder`, `SignupProperties` (`@RequiredArgsConstructor`).
    - `normalizeAndValidateEmail`: blank-first required check (covers null/empty/whitespace) →
      `strip()` (preserve interior + case) → length `< minEmailLength` or `> maxEmailLength` or
      `!isWellFormedEmail` → `ValidationException`. Implement the deliberate `isWellFormedEmail` helper
      (reject any interior whitespace first, then require exactly one `@`, non-empty local, domain with
      ≥1 dot between non-empty labels — no leading/trailing/consecutive dots).
    - `validatePassword`: blank-first required check, then raw `length() < minPasswordLength`
      ("too short") and raw `length() > maxPasswordLength` ("too long"), counting every character with
      no trimming → `ValidationException`.
    - Duplicate pre-check via `userRepository.findByEmail(normalizedEmail)` → `ConflictException`;
      `encode` the password, guard `passwordHash.equals(password)` (throw `IllegalStateException`
      before persisting); build a `User` setting **only** `email`, `passwordHash`,
      `emailVerified=false` (id/timestamps are DB-generated); `saveAndFlush`; catch
      `DataIntegrityViolationException` → translate to `ConflictException` (race), letting the
      exception roll the transaction back. Return `SignupResponse.from(saved)`.
    - Log through Lombok `LOG` at outcome level only (`info` on created `userId`; `info`/`warn` on
      rejections) — never log email, password, or hash.
    - Reuse the E0 `User`, `UserRepository`, `ValidationException`, `ConflictException`.
    - _Requirements: 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 7.2, 7.3, 7.5, 8.1, 8.2, 8.5, 9.1, 9.2, 9.3, 9.5, 13.1, 13.2, 13.3_

  - [x] 4.2 Write `SignupServiceTest` (`src/test/java`, Mockito, no database)
    - `@ExtendWith(MockitoExtension.class)` with `@Mock UserRepository`, `@Mock PasswordEncoder`,
      `@Captor ArgumentCaptor<User>`; build the service in `@BeforeEach` with real
      `new SignupProperties(8, 128, 6, 254)` (a record value object is not mocked).
    - Cover, per the test conventions (`@ParameterizedTest` tables for the email/password rules,
      separate methods where mock setup or verification differs): encode-exactly-once with the raw
      password; persist an unverified user whose captured `email` is normalized and `passwordHash` is
      the stub `!=` plaintext; strip/preserve interior+case; reject blank/malformed/oversize email
      (one input per `isWellFormedEmail` branch, plus interior-whitespace `"a b@c.d"` and too-short
      `"a@b.c"`) with nothing persisted; reject blank/too-short/too-long password (a 7-char and a
      129-char case parametrized together) and accept the length-8, whitespace, and 128-char boundary
      passwords with nothing persisted on failure; raise conflict for case/whitespace duplicate
      variants; translate `DataIntegrityViolationException` to a fixed-message conflict; fail without
      persisting when the encoder returns plaintext. Each failure path asserts no `saveAndFlush` (and
      `verifyNoInteractions(passwordEncoder)` where the encoder is never reached).
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 1.5, 1.6, 2.1, 2.2, 2.3, 3.1, 3.2, 3.4, 3.5, 4.1, 4.2, 4.3, 4.5, 4.7, 5.1, 5.3, 6.1, 6.2, 6.3, 7.2, 7.3, 7.5, 9.1, 10.2, 13.1_

  - [x] 4.3 Write `SignupConfidentialityTest` (`src/test/java`)
    - Use Spring Boot's `OutputCaptureExtension` (or a Logback `ListAppender` on the `SignupService`
      logger): run a successful signup and a failing signup and assert the captured log output
      contains neither the submitted password nor the produced hash at any level. Separately serialize
      a `SignupResponse` with the application `ObjectMapper` and assert no member exposes the password
      or hash.
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 15.5_

  - [x] 4.4 Write `SignupPersistenceIntegrationTest` (`src/it/java`, Testcontainers PostgreSQL)
    - `extends AbstractPostgresIntegrationTest`, autowiring `UserRepository` (+ `TestEntityManager`):
      persist a `User` via `saveAndFlush`, clear and reload, and assert a non-null `UUID` id plus
      non-null UTC `created_at`/`modified_at` (all server-side defaults, none client-supplied); then
      persist `"User@Example.com"`, assert `findByEmail("user@example.com")` returns the same row, and
      assert inserting `"USER@EXAMPLE.COM"` is rejected by the `email` `UNIQUE` constraint
      (`DataIntegrityViolationException`) — proving real `citext` case-insensitive uniqueness.
    - _Requirements: 9.2, 9.3, 9.4, 2.2, 5.1, 6.1_

- [x] 5. Checkpoint — compile and run the unit + integration suite
  - Ensure the encoder/properties/DTO/service layer compiles and `./mvnw test -Pbdd,it` (the
    `make be-test` path) runs the encoder, service, confidentiality, and persistence integration tests
    green. Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement `AuthController` and wire the endpoint end-to-end (`com.bovae.yaj.web.controller`)
  - [x] 6.1 Implement the thin `AuthController`
    - `@RestController @RequestMapping("/api/v1/auth")` with
      `@PostMapping(value = "/signup", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)`
      and `@ResponseStatus(HttpStatus.CREATED)`, binding `@RequestBody SignupRequest` and delegating to
      `signupService.signup(request)` — no validation, hashing, persistence, or `UserRepository`
      access in the controller. `consumes = json` lets the base `ResponseEntityExceptionHandler` in
      the existing `GlobalProblemHandler` map an absent/unparseable body to 400 and a wrong
      `Content-Type` to 415 without invoking the service. Set no `Location` header (no read endpoint
      yet) and issue no token/cookie. Leave `SecurityConfig` unchanged.
    - _Requirements: 1.1, 1.2, 1.3, 1.7, 10.1, 10.3, 11.1, 11.2, 11.3, 11.4, 12.1, 12.2, 12.3_

  - [x] 6.2 Write `AuthControllerTest` (`src/test/java`, web slice)
    - `@WebMvcTest(AuthController.class)` + `@AutoConfigureMockMvc(addFilters = false)` +
      `@MockitoBean SignupService`: assert 201 with `application/json` body carrying `id`, `email`,
      `emailVerified=false`, and `createdAt` serialized as an ISO-8601 `...Z` instant; assert the
      response JSON has no `password`/`passwordHash`/`hash` member and no `Set-Cookie`/token; assert an
      empty/garbage body yields 400 problem+json and a `text/plain` body yields 415, both with
      `verifyNoInteractions(signupService)`.
    - _Requirements: 1.7, 8.3, 8.4, 10.1, 10.2, 10.3, 10.4, 11.1, 12.3_

  - [x] 6.3 Write the BDD signup scenario (`src/bdd`, `@auth`, full context + Testcontainers)
    - Add `be/src/bdd/resources/features/signup.feature` tagged `@auth` with the single
      `Scenario: Valid signup creates an unverified account`, and a `SignupSteps` glue class following
      the existing BDD conventions (`CucumberSpringConfig` `RANDOM_PORT` + Testcontainers). Use
      `TestRestTemplate` with `@LocalServerPort` to POST the request body unauthenticated (proving
      permit-all reachability), assert `201`, then autowire `UserRepository` to assert the
      `"new-user@example.com"` row exists with `email_verified=false` and no verification token. Build
      the request body as a `Map<String, String>` and let `TestRestTemplate`'s Jackson converter
      serialize and escape it (no hand-built JSON string), so future negative scenarios can pass null
      field values. Add an `@After("@auth")` cleanup hook calling `userRepository.deleteAll()`.
    - _Requirements: 15.6, 1.5, 9.1, 12.1, 13.2_

- [x] 7. Final checkpoint — run the full quality gate and close any gaps
  - Run `make be-lint` (`./mvnw spotless:check verify -Pbdd,it`): Spotless (palantir, no wildcard
    imports) clean, Error Prone + NullAway clean, and JaCoCo **90% line + 90% branch** over the
    non-excluded packages (the `bdd` profile aggregates unit + BDD coverage). Confirm the full context
    starts with the `PasswordEncoder` bean present and BouncyCastle resolved, and that `SecurityConfig`
    is unchanged with no new security beans. Close any coverage or static-analysis gaps. Ensure all
    tests pass, ask the user if questions arise.
  - _Requirements: 15.7, 15.8, 15.9, 15.10, 7.1, 14.1, 14.2, 12.2_

## Notes

- All E1 code lives in the **`be`** module; no source outside `be` is modified.
- The design specifies **no** property-based testing and no PBT library — the design has no
  Correctness Properties section, so this plan uses unit tests (`src/test`), integration tests
  (`src/it`, Testcontainers PostgreSQL), and one BDD scenario (`src/bdd`) only. The input-space
  coverage a property would give is expressed as `@ParameterizedTest` tables over chosen inputs.
- Pure-logic and slice tests sit next to their implementation to catch errors early; the
  database-backed integration test reuses the E0 `AbstractPostgresIntegrationTest` base and the BDD
  scenario runs only after the endpoint is fully wired (Task 6).
- The encoder bean (`config.PasswordEncoderConfig`) and `config.properties.SignupProperties` live in
  the JaCoCo-excluded `**/config/**` packages; the gated coverage is carried by `auth.SignupService`,
  `web.controller.AuthController`, and the `web.dto` records.
- Each test sub-task references the requirement clauses it covers; Tasks 5 and 7 provide incremental
  and final validation against the mandatory 90/90 + Spotless + Error Prone/NullAway gate.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "3.1"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["4.1"] },
    { "id": 3, "tasks": ["4.2", "4.3", "4.4", "6.1"] },
    { "id": 4, "tasks": ["6.2", "6.3"] }
  ]
}
```
