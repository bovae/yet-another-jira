# Implementation Plan: Email verification + resend (Epic E2)

## Overview

This plan implements `004-email-verification-resend` on top of the shipped E1 sign-up code,
reusing the existing `VerificationToken`/`User` entities and their repositories, the
`GlobalExceptionHandler` + `ProblemDetailFactory` RFC 9457 stack, the Valkey `StringRedisTemplate`
wiring, the `@ConfigurationPropertiesScan` properties pattern, and the permit-all `SecurityConfig`.

The work is sequenced bottom-up: configuration and the two new error mappings first, then the
stateless token primitives, the repository queries, the issuance/email-dispatch path, the sign-up
integration, the verify and resend services, and finally the HTTP layer that wires everything
together. Integration and end-to-end BDD coverage close the plan.

Testing follows the three project-standard layers only — JUnit 5 + Mockito unit tests,
Testcontainers PostgreSQL integration tests (`src/it`, `-Pit`), and Cucumber BDD against a real
PostgreSQL + Valkey context with an SMTP capture server (`src/bdd`, `-Pbdd`, tagged `@auth`). No
property-based testing is used, matching the design's Testing Strategy. Test sub-tasks are marked
with `*` and may be skipped for a faster MVP; core implementation sub-tasks are never optional.

## Tasks

- [x] 1. Configuration and dependency foundation
  - [x] 1.1 Add the SMTP runtime dependency
    - Declare `org.springframework.boot:spring-boot-starter-mail` in `be/pom.xml` so
      `JavaMailSender`/`JavaMailSenderImpl` are on the runtime classpath
    - _Requirements: 16.1_

  - [x] 1.2 Add validated `SmtpProperties` and the `MailConfig` mail-sender bean
    - Create `config/properties/SmtpProperties` (`@ConfigurationProperties("yaj.mail")`, `@Validated`)
      with `@NotBlank host`, `@Min(1) @Max(65535) int port`, `@NotBlank from`, and
      `@Nullable username`/`password`, mirroring `ValkeyProperties`
    - Create `config/MailConfig` (`@Configuration`) that builds a `JavaMailSenderImpl` from
      `SmtpProperties` (host, port, credentials when present), so Spring Boot's
      `MailSenderAutoConfiguration` backs off
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.6_

  - [x] 1.3 Write `SmtpPropertiesTest`
    - Jakarta-Validator unit test mirroring `ValkeyPropertiesTest`: valid values pass; blank host/from
      and zero/out-of-range ports produce violations
    - _Requirements: 4.2, 4.3_

  - [x] 1.4 Add validated `VerificationProperties`
    - Create `config/properties/VerificationProperties` (`@ConfigurationProperties("yaj.verification")`,
      `@Validated`) with `@NotNull Duration tokenTtl`, `@NotBlank String linkBaseUrl`,
      `@NotBlank String resultRedirectUrl`, `@Min(1) int resendRateLimit`,
      `@NotNull Duration resendRateWindow`; auto-registered by the existing `@ConfigurationPropertiesScan`
    - _Requirements: 4.4, 1.3, 13.1_

  - [x] 1.5 Write `VerificationPropertiesTest`
    - Validator unit test: valid values pass; blank URLs and zero/negative `resendRateLimit` produce
      violations
    - _Requirements: 4.4_

  - [x] 1.6 Add the UTC `Clock` bean and `application.yml` configuration
    - Register a `Clock.systemUTC()` `@Bean` in the `config` package for injection into the verify,
      resend, and issuer components
    - Add the `yaj.verification.*` and `yaj.mail.*` blocks to `application.yml` as environment-variable
      references (`YAJ_SMTP_HOST`, `YAJ_SMTP_PORT`, `YAJ_VERIFICATION_LINK_BASE_URL`,
      `YAJ_VERIFICATION_REDIRECT_URL`, etc.) with no plaintext SMTP credential in source
    - _Requirements: 6.2, 4.1, 4.4, 4.5_

- [x] 2. Typed exceptions and RFC 9457 error mappings
  - [x] 2.1 Add the `GoneException` and `RateLimitException` types
    - Create `error/GoneException` (message-only) and `error/RateLimitException` (message +
      `retryAfterSeconds`), mirroring the existing typed domain exceptions
    - _Requirements: 15.2, 15.3_

  - [x] 2.2 Map the new exceptions in `GlobalExceptionHandler`
    - Add a `GoneException` handler returning 410 and a `RateLimitException` handler returning 429 with a
      `Retry-After` header, both built through `ProblemDetailFactory` so the body carries
      `correlationId`/`timestamp` and leaks no stack trace, internal type, SQL, raw token, or hash
    - _Requirements: 15.2, 15.3, 15.4, 15.5, 13.3_

  - [x] 2.3 Extend `GlobalExceptionHandlerTest`
    - Assert `GoneException` produces 410 problem+json with `status: 410`; `RateLimitException` produces
      429 problem+json with `status: 429` and a `Retry-After` header; both bodies carry
      `correlationId`/`timestamp` and leak no internals
    - _Requirements: 15.2, 15.3, 15.4, 15.5_

- [x] 3. Token generation and hashing primitives
  - [x] 3.1 Add `VerificationPurpose` constant and `RawTokenGenerator`
    - Create `auth/VerificationPurpose` exposing `EMAIL_VERIFICATION = "EMAIL_VERIFICATION"`
    - Create `auth/RawTokenGenerator` (`@Component`) wrapping `SecureRandom`, returning 32 random bytes
      (256 bits, ≥128 required) as a URL-safe unpadded Base64 string
    - _Requirements: 2.1, 1.2_

  - [x] 3.2 Add `TokenHasher`
    - Create `auth/TokenHasher` (`@Component`) computing `SHA-256(rawToken)` as lowercase hex; one-way,
      deterministic, shared by issuer and verify for a stable issue-then-verify round trip
    - _Requirements: 2.2, 2.6_

  - [x] 3.3 Write `TokenHasherTest`
    - Assert deterministic output, output differs from input (one-way), stability across calls, and that
      the same raw token always hashes to the same stored value
    - _Requirements: 2.2, 2.6_

- [x] 4. Verification token repository queries
  - [x] 4.1 Add `findByTokenHashForUpdate` and `invalidateUnconsumed`
    - Add to `VerificationTokenRepository`: a `@Lock(PESSIMISTIC_WRITE)` `findByTokenHashForUpdate(hash)`
      for single-use enforcement, and a `@Modifying(clearAutomatically = true, flushAutomatically = true)`
      `invalidateUnconsumed(userId, purpose, now)` that stamps `consumed_at` on every unconsumed row of
      that purpose for the user and returns the affected count
    - _Requirements: 7.3, 10.2, 8.3_

  - [x] 4.2 Write `VerificationTokenRepositoryIntegrationTest` (`src/it`, `-Pit`)
    - Extend `AbstractPostgresIntegrationTest`: `findByTokenHashForUpdate` returns a saved token;
      `invalidateUnconsumed` stamps only the unconsumed `EMAIL_VERIFICATION` rows of one user and leaves
      consumed rows and other users/purposes untouched; a stamped `consumed_at` makes a second lookup see
      the token consumed (single-use path through real SQL)
    - _Requirements: 10.2, 7.1, 7.3_

- [x] 5. Token issuance and after-commit email dispatch
  - [x] 5.1 Add `VerificationEmailRequestedEvent`
    - Create `auth/VerificationEmailRequestedEvent(String recipientEmail, String rawToken)` — an
      in-process event carrying the raw token only in memory, never persisted or logged
    - _Requirements: 2.3, 2.4_

  - [x] 5.2 Implement `VerificationTokenIssuer`
    - Create `auth/VerificationTokenIssuer` (`@Component`, no `@Transactional` — joins the caller's
      transaction): generate raw token, hash it, persist a `VerificationToken`
      (`purpose = EMAIL_VERIFICATION`, `expiresAt = clock.instant() + tokenTtl` (24h), `consumedAt = null`,
      `userId` set), and publish `VerificationEmailRequestedEvent`; store only the hash
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3_

  - [x] 5.3 Write `VerificationTokenIssuerTest`
    - Capture the persisted `VerificationToken` (`@Captor`): assert `purpose = EMAIL_VERIFICATION`,
      `expiresAt = now + 24h` (clock-driven), `consumedAt == null`, stored hash differs from the raw
      token, and a `VerificationEmailRequestedEvent` is published
    - _Requirements: 1.2, 1.3, 1.4, 2.2_

  - [x] 5.4 Implement `VerificationEmailSender`
    - Create `auth/VerificationEmailSender` (`@Component`): build the link from
      `verificationProperties.linkBaseUrl()` + raw token `token` query param, compose a
      `SimpleMailMessage` (`from` = `smtpProperties.from()`, `to` = recipient, fixed subject, body with
      link), and send via `JavaMailSender`; never log the raw token, hash, or any SMTP credential
    - _Requirements: 3.1, 3.2, 3.3, 3.6, 2.4_

  - [x] 5.5 Implement `VerificationEmailDispatcher`
    - Create `auth/VerificationEmailDispatcher` (`@Component`) with a
      `@TransactionalEventListener(phase = AFTER_COMMIT)` method that delegates to
      `VerificationEmailSender`; catch and log `MailException` (without the raw token) so a dispatch
      failure never rolls back the committed account/token
    - _Requirements: 3.4, 3.5_

  - [x] 5.6 Write `VerificationEmailSenderTest` and `VerificationEmailDispatcherTest`
    - Sender: builds the link from `linkBaseUrl` + raw token and calls `JavaMailSender.send` with the
      right `to`/`from`. Dispatcher: a thrown `MailException` is swallowed (logged) so sign-up is
      unaffected, and a log-capture assertion confirms the raw token never appears in any log line
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 2.4_

- [x] 6. Sign-up integration
  - [x] 6.1 Issue a token from `SignupService`
    - Inject `VerificationTokenIssuer` into `SignupService` and call `issue(userId, email)` within the
      existing `@Transactional` sign-up after the user is saved, so issuance failure rolls back the user
      insert and the email dispatches only after commit
    - _Requirements: 1.1, 1.5, 1.6, 3.4_

  - [x] 6.2 Update `SignupServiceTest`
    - Add the issuer mock to the constructor; assert the issuer is invoked after a successful save and
      that an issuer failure prevents the user from being persisted (rolled back)
    - _Requirements: 1.5, 1.6_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Email verification service
  - [x] 8.1 Implement `EmailVerificationService`
    - Create `auth/EmailVerificationService` (`@Service`, `@Transactional`): blank/null token →
      `ValidationException`; hash and `findByTokenHashForUpdate` (pessimistic lock) → `GoneException`
      when absent, wrong purpose, expired (`now >= expiresAt`), or already consumed (single uniform
      message); on success stamp `consumedAt = clock.instant()` (UTC) and set the user
      `emailVerified = true` (already-true stays true); any raised error rolls back leaving
      `email_verified` unchanged
    - _Requirements: 5.4, 6.1, 6.2, 6.6, 7.1, 7.2, 7.3, 8.1, 8.3, 8.4, 8.5, 8.6_

  - [x] 8.2 Write `EmailVerificationServiceTest`
    - Expiry boundary (`expires_at <= now` → 410-mapped `GoneException`; future + unconsumed → verifies,
      asserting `email_verified` true and `consumed_at` stamped, clock-driven); single-use (already
      consumed → `GoneException`, user never saved verified); blank token → `ValidationException` with
      repositories untouched; unknown hash → `GoneException`; wrong purpose → `GoneException`;
      already-verified + valid token stays true; parameterize unknown/expired/consumed to assert the
      identical uniform message
    - _Requirements: 16.2, 16.3, 8.1, 8.3, 8.5, 7.2, 6.6_

- [x] 9. Resend service and rate limiter
  - [x] 9.1 Implement `ResendRateLimiter`
    - Create `auth/ResendRateLimiter` (`@Component`) over `StringRedisTemplate`: key
      `verif:resend:rl:<sha256(lowercase email)>` (reuse `TokenHasher`); `INCR`, set TTL =
      `resendRateWindow` when count is 1, and raise `RateLimitException(retryAfterSeconds)` from the
      remaining TTL when count exceeds `resendRateLimit`; Valkey holds only the ephemeral counter
    - _Requirements: 13.1, 13.2, 13.5, 13.6_

  - [x] 9.2 Write `ResendRateLimiterTest`
    - Mocked `StringRedisTemplate`: first request sets TTL = window; count beyond the limit raises
      `RateLimitException` carrying `retryAfterSeconds` from the key TTL; the key derives from a hash of
      the email, not the raw address
    - _Requirements: 13.6, 13.2, 13.5_

  - [x] 9.3 Implement `VerificationResendService`
    - Create `auth/VerificationResendService` (`@Service`, `@Transactional`): normalize (blank →
      `ValidationException`); call the rate limiter **before** any account lookup; `findByEmail`
      (case-insensitive `citext`); no account or already-verified → issue nothing/send nothing/return;
      unverified → `invalidateUnconsumed(userId, EMAIL_VERIFICATION, now)` then
      `verificationTokenIssuer.issue(...)` (invalidate before issue)
    - _Requirements: 9.4, 9.5, 10.1, 10.2, 10.3, 11.1, 11.2, 11.3, 12.3, 12.4, 13.2, 13.4_

  - [x] 9.4 Write `VerificationResendServiceTest`
    - Issuing a new token invalidates earlier unconsumed `EMAIL_VERIFICATION` tokens
      (`invalidateUnconsumed` then issuer, `InOrder`); exceeding the limit → `RateLimitException` with
      issuer and sender never invoked; unknown email and already-verified email each return with no token
      issued and no email event, asserting the identical uniform outcome; blank email →
      `ValidationException` with the rate limiter never consulted; rate limiter consulted before lookup
    - _Requirements: 16.4, 16.5, 10.2, 11.1, 11.2, 11.3, 12.3, 12.4, 13.4_

- [x] 10. HTTP layer — DTOs and controller wiring
  - [x] 10.1 Add the verify and resend DTOs
    - Create `web/dto/VerifyRequest(@Nullable String token)`,
      `web/dto/VerifyResponse(boolean verified, String message, String next)` with a `success()` factory,
      `web/dto/ResendRequest(@Nullable String email)`, and `web/dto/ResendResponse(String message)` with a
      `uniform()` factory; none carry any JWT, session token, or cookie field
    - _Requirements: 6.3, 6.5, 12.1, 12.2_

  - [x] 10.2 Extend `AuthController` with the verify and resend handlers
    - Add `GET /verify` (`token` query param, `required = false`) returning `303` to
      `verificationProperties.resultRedirectUrl()` with an empty body and no auth token/cookie;
      `POST /verify` (JSON `VerifyRequest`) returning `200` `VerifyResponse`; `POST /verification/resend`
      (JSON `ResendRequest`) returning `202` uniform `ResendResponse`; delegate all rules to the services,
      touching no repository directly
    - _Requirements: 5.1, 5.2, 5.3, 6.3, 6.4, 6.5, 9.1, 9.2, 9.3, 12.1, 12.2_

  - [x] 10.3 Extend `AuthControllerTest` (`@WebMvcTest`)
    - `GET /verify` → 303 with the configured `Location` and no `Set-Cookie`/token; `POST /verify` → 200
      JSON body and no token; `POST /verification/resend` → 202 uniform body; controller delegates and
      never touches repositories
    - _Requirements: 5.1, 5.2, 5.3, 6.3, 6.4, 6.5, 9.1, 9.2, 9.3, 12.1_

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. End-to-end BDD verification flow (`src/bdd`, `-Pbdd`, `@auth`)
  - [x] 12.1 Add the SMTP capture server to the BDD context
    - Add a test-scoped SMTP capture dependency (GreenMail in-JVM) to `be/pom.xml`; extend
      `TestcontainersConfig` to start the capture server and register `yaj.mail.host`/`yaj.mail.port`
      (and `yaj.verification.*`) to point at it; add an `@After("@auth")` hook clearing persisted users
      and captured messages
    - _Requirements: 16.6_

  - [x] 12.2 Author `verification.feature`
    - Tagged `@auth` with a `Background` for the shared SMTP-capture setup: end-to-end verify (sign up →
      capture email → extract raw token → `POST /verify` → success and `email_verified = true`); GET-link
      verify → 303 to the redirect URL; consumed/expired link → 410; resend issues a fresh email and
      invalidates the prior token (old → 410, new → success); resend privacy (unknown and already-verified
      → identical 202, no email); resend rate limit → 429 with `Retry-After`, no further email. Use a
      `Scenario Outline`/`Examples` for the privacy and status-code variants
    - _Requirements: 16.6, 10.3, 10.4, 11.1, 11.2, 11.3, 12.1, 12.2, 12.3, 13.2, 13.3_

  - [x] 12.3 Implement the Cucumber step definitions
    - Add `VerificationSteps` (tagged `@auth`) driving the real endpoints over the random-port context,
      reading captured messages from the SMTP capture server, extracting the raw token from the link, and
      asserting status codes, captured-email counts, the `Retry-After` header, and the `users.email_verified`
      state
    - _Requirements: 16.6, 6.4, 10.4, 12.1, 12.2, 13.3_

- [x] 13. Final checkpoint - quality gates
  - Run the Maven build and ensure all unit, integration (`-Pit`), and BDD (`-Pbdd`) tests pass, JaCoCo
    meets ≥90% line and ≥90% branch over non-excluded packages, and Spotless and Error Prone + NullAway
    report no violations. Ask the user if questions arise.
  - _Requirements: 16.7, 16.8, 16.9_

- [x] 14. Post-review hardening
  - [x] 14.1 Bound SMTP operations with a configurable timeout
    - Add `@NotNull Duration timeout` to `SmtpProperties`; have `MailConfig` set
      `mail.smtp.connectiontimeout`/`timeout`/`writetimeout` on the `JavaMailSenderImpl` from it; add
      `yaj.mail.timeout: ${YAJ_SMTP_TIMEOUT:5s}` to `application.yml`; extend `SmtpPropertiesTest` with a
      null-timeout violation case. Prevents a hung relay (the after-commit dispatch runs on the request
      thread) from exhausting the servlet pool
    - _Requirements: 3.7, 4.7_

  - [x] 14.2 Make the resend rate-limiter TTL self-healing and locale-stable
    - In `ResendRateLimiter`, lowercase the email with `Locale.ROOT` before hashing the key, and on the
      over-limit path re-arm the window expiry when the counter is found without a TTL (so a lost
      `EXPIRE` cannot lock an email out permanently); add the `ResendRateLimiterTest` case covering the
      missing-TTL re-arm
    - _Requirements: 13.6, 13.7_

  - [x] 14.3 Redirect failed `GET /verify` to the error screen
    - Add `@NotBlank String resultErrorRedirectUrl` to `VerificationProperties` (and
      `result-error-redirect-url: ${YAJ_VERIFICATION_ERROR_REDIRECT_URL}` to `application.yml` and the
      BDD `TestcontainersConfig`); have `AuthController`'s `GET /verify` catch
      `ValidationException`/`GoneException` and `303` to `resultErrorRedirectUrl` (POST still returns
      400/410); extend `VerificationPropertiesTest` (blank error URL) and `AuthControllerTest`
      (`GET` failure → 303 to error URL), and add the GET-failure-redirect BDD scenario
    - _Requirements: 6.7, 4.4_

## Notes

- Tasks marked with `*` are test sub-tasks; they may be skipped for a faster MVP but are required to
  satisfy the E2 Definition of Done (Requirements 16.2–16.6) and the coverage gate (16.7).
- Testing uses only the project's three standard layers — JUnit 5 + Mockito unit tests, Testcontainers
  PostgreSQL integration tests, and Cucumber `@auth` BDD with an SMTP capture server. Property-based
  testing is intentionally not used, matching the design's Testing Strategy.
- Each task references the specific requirement clauses it satisfies for traceability.
- `config/**` and `model/**` are outside the coverage gate, so `MailConfig`, `SmtpProperties`,
  `VerificationProperties`, the `Clock` bean, and the entities fall outside it; the services, controller,
  issuer, hasher, sender, dispatcher, rate limiter, exceptions, and handler are inside it and are covered
  by the unit + IT + BDD suites above.
- `SecurityConfig` is intentionally untouched (Requirement 14): the verify and resend routes stay
  reachable under the existing permit-all seam.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.4", "1.6", "2.1", "3.1", "3.2", "4.1", "5.1", "10.1"] },
    { "id": 1, "tasks": ["1.2", "1.5", "2.2", "3.3", "4.2", "5.2", "8.1", "9.1"] },
    { "id": 2, "tasks": ["1.3", "2.3", "5.3", "5.4", "6.1", "8.2", "9.2", "9.3"] },
    { "id": 3, "tasks": ["5.5", "6.2", "9.4", "10.2"] },
    { "id": 4, "tasks": ["5.6", "10.3", "12.1"] },
    { "id": 5, "tasks": ["12.2"] },
    { "id": 6, "tasks": ["12.3"] }
  ]
}
```
