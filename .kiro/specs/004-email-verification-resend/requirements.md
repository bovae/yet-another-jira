# Requirements Document

## Introduction

This document specifies the requirements for **Epic E2 — Email verification + resend**
(`004-email-verification-resend`), the second authentication epic for the yet-another-jira
Kanban ticket tracker. E2 builds directly on the domain foundation (`002-backend-domain-foundation`)
and the sign-up endpoint (`003-signup-password-hashing`, "E1").

After E1, a new account is persisted with `email_verified = false` but **no verification token is
issued and no email is sent**. E2 closes that gap. It delivers three behaviors:

1. **Token issuance on sign-up.** When E1 creates an account, E2 issues a single-use email-verification
   token, stores **only a one-way hash** of that token in the existing `verification_tokens` table
   (`purpose = EMAIL_VERIFICATION`, `expires_at = created instant + 24 hours`), and sends the **raw**
   token to the account's email address inside a verification link over SMTP.
2. **Verification** at `GET /api/v1/auth/verify` and `POST /api/v1/auth/verify`. A request carrying a
   raw token that hashes to a stored, unexpired, unconsumed token marks the account
   `email_verified = true` and stamps `consumed_at`. Verification directs the user to the login screen;
   automatic login is **not** performed.
3. **Resend** at `POST /api/v1/auth/verification/resend`. A request for an unverified account issues a
   fresh token, invalidates the account's earlier unused tokens, and sends a new verification email.
   Resend is rate-limited per email through Valkey.

E2 reuses, rather than re-implements, the foundation already shipped: the `VerificationToken` entity
and `VerificationTokenRepository`, the `User` entity and `UserRepository`, the typed domain exceptions
and their RFC 9457 mapping in `GlobalExceptionHandler` through `ProblemDetailFactory`, the Valkey
wiring (`ValkeyConfig`, `StringRedisTemplate`, `ValkeyProperties`), the `@ConfigurationPropertiesScan`
properties pattern, and the permit-all `SecurityConfig` seam. The one new runtime dependency is
`spring-boot-starter-mail` for SMTP delivery; the SMTP target is configurable (Mailpit locally behind
the `mail` compose profile, `relay1.dataart.com` as the reference relay).

E2 deliberately stops at issuing, consuming, and re-issuing verification tokens. **Login and the
rejection of unverified accounts at login** belong to **Epic E3**. **Replacing the permit-all seam
with real JWT authentication and authorization** belongs to **Epic E4**; the verify and resend
endpoints are reachable under the existing permit-all policy, exactly like sign-up, and E2 introduces
no authentication enforcement of its own. The **frontend** verification-result and resend screens
belong to **Epic E12**; E2 delivers only the backend contract.

These requirements are derived from the E2 entry in `requirements/epics-catalog.md` and the product
requirements source `requirements/yet-another-jira.md` (primarily §3 user accounts and authentication,
§9 API and persistence expectations, §10 minimum screens, and §11 non-functional security
requirements).

## Decisions and Open Questions

The constraints below were under-specified by the source material. Each is captured as a concrete,
testable decision in the requirements that follow. Items marked **[CONFIRM]** change observable
behavior and should be confirmed before the design phase; the requirements encode the recommended
default so review can proceed.

1. **Token-error status codes (Requirement 8, 15). [CONFIRM]** A verify request whose token is
   structurally absent or blank returns **400** (`Validation_Error`). A verify request whose token is
   *well-formed but not usable* — unknown (no matching hash), expired, or already consumed — returns a
   single, uniform **410 Gone** (`Gone_Error`) with one message directing the user to request a new
   verification email. **No 410 mapping exists today** (`GlobalExceptionHandler` maps 400/401/404/409
   only), so this introduces a new `Gone_Error` exception and a new handler mapping. Rationale: a
   uniform 410 for unknown/expired/consumed avoids token-existence enumeration and gives the user one
   clear "request a new link" path. *Alternative:* split into 404 (unknown) + 410 (expired/consumed),
   which leaks whether a token ever existed.

2. **Email-enumeration privacy on resend (Requirement 11, 12). [CONFIRM]** Resend returns the **same
   uniform success response** whether the email is unknown, unverified, or already verified, and sends
   an email only for an unverified registered account. This **intentionally diverges** from the literal
   product text ("resend for an already-verified account is a no-op success **or clear message**"): a
   distinct "already verified" message would let an unauthenticated caller probe which emails are
   registered and verified. Rationale: §11 privacy. *Alternative:* return a distinct message for the
   already-verified case, accepting the enumeration exposure.

3. **Resend rate-limit response (Requirement 13). [CONFIRM]** When the per-email resend rate limit is
   exceeded, the endpoint returns **429 Too Many Requests** (`Rate_Limit_Error`) with a `Retry-After`
   header. **No 429 mapping exists today**, so this introduces a new exception and handler mapping.
   *Alternative:* return the same uniform success as the anti-enumeration path (no new mapping, but the
   client cannot tell it is being throttled). The rate-limit threshold and window are externalized
   configuration; recommended defaults are **5 requests per 15 minutes per email** — confirm the
   numbers.

4. **SMTP send failure on sign-up (Requirement 3). [CONFIRM]** The verification token is persisted
   atomically with the new account, but the email is dispatched **after** the sign-up transaction
   commits, as a best-effort side effect. If SMTP dispatch fails, sign-up still returns **201** and the
   failure is logged; the user recovers through resend. Rationale: the account and token are durable,
   email delivery is not transactional, and resend is the recovery path. *Alternative:* fail the
   sign-up (rollback) when SMTP is unreachable.

5. **`GET /verify` response shape (Requirement 6). [CONFIRM]** `GET /api/v1/auth/verify?token=...`
   exists so a link clicked from an email reaches the backend directly; on success it issues a **303
   redirect to the configured login URL** (`Verification_Result_Redirect_URL`), satisfying "verification
   leads to the login screen". On failure (missing, blank, unknown, expired, or consumed token) it
   issues a **303 redirect to the configured error URL** (`Verification_Error_Redirect_URL`) instead of
   a problem-response body, so a browser following the emailed link never sees raw JSON. `POST
   /api/v1/auth/verify` performs the same verification and returns a JSON result (and 400/410 problem
   responses) for programmatic/frontend callers. A state-changing `GET` is the one sanctioned
   exception, consistent with §9 permitting the single-use token in the verification URL. *Alternative:*
   the emailed link targets a frontend route that calls `POST`, and the backend exposes `POST` only.

6. **Token hashing algorithm (Requirement 2).** The stored value is a one-way hash of a high-entropy
   random token. Because the token is a 128-bit-plus random secret (not a low-entropy password), a fast
   one-way hash (e.g. SHA-256) is appropriate; Argon2id is unnecessary here. The exact algorithm is a
   design-phase choice; this requirement fixes only the one-way, hash-only-storage property.

## Glossary

- **Email_Verification_Feature**: The complete E2 deliverable taken as a whole: token issuance on sign-up, the verify and resend endpoints, their controllers and services, the SMTP sender, the resend rate limiter, and the supporting configuration wiring.
- **Signup_Service**: The existing `com.bovae.yaj.auth.SignupService` from E1, extended by E2 to trigger verification-token issuance when a new account is created.
- **Verification_Token_Issuer**: The component that generates a verification token, persists its hash through the Verification_Token_Repository, and hands the raw token to the Verification_Email_Sender. Invoked on sign-up and on resend.
- **Verify_Endpoint**: The HTTP endpoint exposed at `GET /api/v1/auth/verify` and `POST /api/v1/auth/verify` that consumes a verification token.
- **Resend_Endpoint**: The HTTP endpoint exposed at `POST /api/v1/auth/verification/resend` that re-issues a verification email.
- **Verification_Controller**: The Spring MVC controller (the existing `com.bovae.yaj.web.controller.AuthController`, `@RequestMapping("/api/v1/auth")`) that handles Verify_Endpoint and Resend_Endpoint requests and is restricted to HTTP concerns.
- **Email_Verification_Service**: The transactional service component that implements the verify business rules and updates the account.
- **Verification_Resend_Service**: The transactional service component that implements the resend business rules: rate-limit check, prior-token invalidation, new-token issuance, and email dispatch.
- **Verification_Email_Sender**: The component that sends a verification email over SMTP, containing the Verification_Link.
- **Raw_Token**: The high-entropy random token value sent to the user inside the Verification_Link. It is never persisted and never logged.
- **Token_Hash**: The one-way hash of the Raw_Token, stored in the `verification_tokens.token_hash` column. The Raw_Token cannot be derived from the Token_Hash.
- **Verification_Token**: A row of the existing `verification_tokens` table, mapped by `com.bovae.yaj.domain.model.VerificationToken` (`id`, `user_id`, `token_hash`, `purpose`, `expires_at`, `consumed_at`, `created_at`). E2 does not change this schema.
- **Verification_Token_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.VerificationTokenRepository`, including `findByTokenHash`, extended by E2 with the queries the resend invalidation and verify lookup need.
- **Verification_Purpose**: The literal token purpose value `EMAIL_VERIFICATION` stored in `verification_tokens.purpose`.
- **Token_Expiry_Window**: The validity duration of a Verification_Token, fixed at 24 hours from the token's creation instant.
- **Verification_Link**: The URL delivered in the verification email that carries the Raw_Token, built from the Verification_Link_Base_URL.
- **Verification_Link_Base_URL**: The externalized base URL from which the Verification_Link is constructed.
- **Verification_Result_Redirect_URL**: The externalized login-screen URL to which a successful `GET /api/v1/auth/verify` redirects.
- **Verification_Error_Redirect_URL**: The externalized error-screen URL to which a failed `GET /api/v1/auth/verify` (missing, blank, unknown, expired, or consumed token) redirects.
- **User_Entity**: The existing JPA entity `com.bovae.yaj.domain.model.User` mapping the `users` table, including the `email` (`citext`), `email_verified`, `created_at`, and `modified_at` columns.
- **User_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.UserRepository`, including its case-insensitive `findByEmail` lookup.
- **Normalized_Email**: A submitted email value after removal of leading and trailing whitespace, compared case-insensitively against the `citext` `users.email` column.
- **Resend_Rate_Limiter**: The Valkey-backed component that limits the number of resend requests accepted for a given Normalized_Email within the Resend_Rate_Window.
- **Valkey_Store**: The existing Valkey instance wired through `com.bovae.yaj.config.ValkeyConfig` and the `StringRedisTemplate`, used by E2 only as an ephemeral rate-limit counter and never as the system of record.
- **Resend_Rate_Limit**: The externalized maximum number of resend requests accepted for one Normalized_Email within one Resend_Rate_Window.
- **Resend_Rate_Window**: The externalized rolling time window over which the Resend_Rate_Limit is enforced.
- **Verification_Properties**: A validated `@ConfigurationProperties` record (prefix `yaj.verification`) holding the Token_Expiry_Window, the Verification_Link_Base_URL, the Verification_Result_Redirect_URL, the Verification_Error_Redirect_URL, and the Resend_Rate_Limit / Resend_Rate_Window values, mirroring the existing `SignupProperties` / `ValkeyProperties` pattern and auto-registered by the existing `@ConfigurationPropertiesScan`.
- **Mail_Configuration**: The externalized SMTP host, SMTP port, and SMTP operation timeout consumed by the Verification_Email_Sender, supplied through environment variables (`YAJ_SMTP_HOST`, `YAJ_SMTP_PORT`, `YAJ_SMTP_TIMEOUT`).
- **Validation_Error**: A `com.bovae.yaj.error.ValidationException`, mapped by the Problem_Handler to HTTP status 400.
- **Gone_Error**: A new typed domain exception introduced by E2 for a token that is unknown, expired, or already consumed, mapped by the Problem_Handler to HTTP status 410.
- **Rate_Limit_Error**: A new typed domain exception introduced by E2 for an exceeded resend rate limit, mapped by the Problem_Handler to HTTP status 429.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalExceptionHandler` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`) that produces RFC 9457 problem responses.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and a UTC `timestamp`.
- **Security_Policy**: The existing `com.bovae.yaj.config.SecurityConfig` permit-all seam under which every request is authorized without authentication, to be replaced in epic E4.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/model/**`, `**/config/**`, `**/mapper/*Impl*`, and `**/*Application.*`.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance provisioned by Testcontainers and an SMTP capture server (Mailpit or GreenMail) under the `bdd` profile.

## Requirements

### Requirement 1: Verification token issuance on sign-up

**User Story:** As an unverified registrant, I want a verification token issued the moment my account is created, so that a verification email can reach me. (§3)

#### Acceptance Criteria

1. WHEN the Signup_Service persists a new User_Entity, THE Verification_Token_Issuer SHALL issue exactly one Verification_Token for that account.
2. WHEN the Verification_Token_Issuer issues a Verification_Token, THE Verification_Token_Issuer SHALL set its `purpose` to the Verification_Purpose value `EMAIL_VERIFICATION`.
3. WHEN the Verification_Token_Issuer issues a Verification_Token, THE Verification_Token_Issuer SHALL set its `expires_at` to the token's creation instant plus the Token_Expiry_Window of 24 hours.
4. WHEN the Verification_Token_Issuer issues a Verification_Token, THE Verification_Token_Issuer SHALL persist the token with a null `consumed_at` value and associate it with the new account through the `user_id` column.
5. THE Verification_Token_Issuer SHALL persist the Verification_Token within the same database transaction that persists the new User_Entity, so that a failure to persist either one leaves neither persisted.
6. IF the persistence of the Verification_Token fails, THEN THE Signup_Service SHALL persist no new User_Entity for that sign-up.

### Requirement 2: Verification token secrecy and storage

**User Story:** As a security owner, I want only a one-way hash of the verification token stored, so that a database disclosure cannot reveal a usable token. (§3, §9, §11)

#### Acceptance Criteria

1. WHEN the Verification_Token_Issuer generates a Raw_Token, THE Verification_Token_Issuer SHALL draw the Raw_Token from a cryptographically secure random source providing at least 128 bits of entropy.
2. WHEN the Verification_Token_Issuer persists a Verification_Token, THE Verification_Token_Issuer SHALL store in `token_hash` a one-way Token_Hash derived from the Raw_Token, such that the stored value differs from the Raw_Token.
3. THE Verification_Token_Issuer SHALL store no representation of the Raw_Token other than the Token_Hash in any persistent store.
4. THE Email_Verification_Feature SHALL exclude the Raw_Token and the Token_Hash from every log statement it emits at any log level, including any exception message or stack trace those statements carry.
5. WHERE a Verification_Token is issued, THE Email_Verification_Feature SHALL place the Raw_Token only inside the Verification_Link delivered by the Verification_Email_Sender, consistent with §9 permitting a single-use email-verification token in the verification URL.
6. FOR a Raw_Token issued by the Verification_Token_Issuer, a subsequent verify request carrying that same Raw_Token SHALL match the stored Token_Hash for the corresponding Verification_Token (issue-then-verify round trip).

### Requirement 3: Verification email delivery over SMTP

**User Story:** As an unverified registrant, I want to receive a verification email with a link, so that I can confirm my address. (§3, §10, §11)

#### Acceptance Criteria

1. WHEN a Verification_Token is issued, THE Verification_Email_Sender SHALL send one email to the account's stored email address containing a Verification_Link that carries the Raw_Token.
2. THE Verification_Email_Sender SHALL construct the Verification_Link from the externalized Verification_Link_Base_URL together with the Raw_Token.
3. THE Verification_Email_Sender SHALL dispatch the verification email through the SMTP server identified by the Mail_Configuration.
4. WHEN a Verification_Token is issued during sign-up, THE Email_Verification_Feature SHALL dispatch the verification email after the sign-up database transaction commits, so that email dispatch is not part of the account-creation transaction.
5. IF the Verification_Email_Sender fails to dispatch the verification email during sign-up, THEN THE Signup_Endpoint SHALL still respond with HTTP status 201, and THE Email_Verification_Feature SHALL record a log entry describing the dispatch failure without exposing the Raw_Token.
6. THE Verification_Email_Sender SHALL exclude any SMTP credential from every log statement it emits and from every response body.
7. THE Mail_Configuration SHALL bound the SMTP connection, read, and write operations of the Verification_Email_Sender with a finite timeout, so that an unresponsive SMTP server cannot block the dispatching thread indefinitely.

### Requirement 4: SMTP and verification configuration externalization

**User Story:** As a maintainer, I want SMTP and verification settings supplied from configuration, so that the SMTP target can be swapped per environment without code changes and no secret is committed. (§3, §11)

#### Acceptance Criteria

1. THE Email_Verification_Feature SHALL read the SMTP host and SMTP port of the Mail_Configuration from externalized configuration bound to the environment variables `YAJ_SMTP_HOST` and `YAJ_SMTP_PORT`.
2. THE Mail_Configuration SHALL accept an SMTP port in the range 1 to 65535 inclusive.
3. IF the configured SMTP port is non-numeric or outside the range 1 to 65535, THEN THE Project_Build SHALL fail the application startup with an error identifying the invalid SMTP port.
4. THE Email_Verification_Feature SHALL read the Token_Expiry_Window, the Verification_Link_Base_URL, the Verification_Result_Redirect_URL, the Verification_Error_Redirect_URL, the Resend_Rate_Limit, and the Resend_Rate_Window from the Verification_Properties record bound to the `yaj.verification` configuration prefix.
5. THE Email_Verification_Feature SHALL keep all source-controlled files free of plaintext SMTP credential values, representing any such value only as an environment-variable reference or non-production placeholder that resolves at runtime.
6. WHERE the Email_Verification_Feature requires an SMTP credential at runtime, THE Email_Verification_Feature SHALL obtain that credential from externalized configuration rather than from a value hardcoded in a source-controlled file.
7. THE Email_Verification_Feature SHALL read the SMTP operation timeout of the Mail_Configuration from externalized configuration bound to the environment variable `YAJ_SMTP_TIMEOUT`, applying a finite default when that variable is unset.

### Requirement 5: Verify endpoint and layering

**User Story:** As an unverified user, I want to submit my verification token to an endpoint, so that the system can confirm my email address. (§3, §9)

#### Acceptance Criteria

1. THE Verification_Controller SHALL expose the Verify_Endpoint at the HTTP routes `GET /api/v1/auth/verify` and `POST /api/v1/auth/verify`.
2. THE Verify_Endpoint SHALL accept the Raw_Token as a `token` query parameter on the `GET` route and as a `token` member of an `application/json` body on the `POST` route.
3. THE Verification_Controller SHALL delegate all verification business rules and persistence to the Email_Verification_Service, and SHALL NOT access the Verification_Token_Repository or the User_Repository directly.
4. THE Email_Verification_Service SHALL execute the entire verification operation within a single database transaction that commits only when verification succeeds and rolls back on any failure.

### Requirement 6: Successful email verification

**User Story:** As an unverified user, I want a valid token to verify my account and send me to the login screen, so that I can then log in. (§3, §10)

#### Acceptance Criteria

1. WHEN a verify request supplies a Raw_Token that hashes to a stored Verification_Token whose `purpose` is `EMAIL_VERIFICATION`, whose `consumed_at` is null, and whose `expires_at` is after the current server time, THE Email_Verification_Service SHALL set the associated User_Entity `email_verified` value to `true`.
2. WHEN the Email_Verification_Service verifies an account, THE Email_Verification_Service SHALL stamp the matched Verification_Token `consumed_at` with the current server time in UTC.
3. WHEN the Email_Verification_Service verifies an account through the `POST` route, THE Verify_Endpoint SHALL respond with HTTP status 200 and a response body indicating that verification succeeded and that the user should proceed to the login screen.
4. WHEN the Email_Verification_Service verifies an account through the `GET` route, THE Verify_Endpoint SHALL direct the user to the configured Verification_Result_Redirect_URL with an HTTP 303 redirect.
5. WHEN the Verify_Endpoint returns any response, THE Verify_Endpoint SHALL exclude any JWT, authentication token, session token, and authentication cookie from that response, so that verification performs no automatic login.
6. WHEN the Email_Verification_Service verifies an account that is already verified through a still-valid token, THE Email_Verification_Service SHALL leave the account `email_verified` value as `true`.
7. WHEN a verify request through the `GET` route fails because the token is missing, blank, unknown, expired, or already consumed, THE Verify_Endpoint SHALL direct the user to the configured Verification_Error_Redirect_URL with an HTTP 303 redirect and SHALL NOT return a problem-response body, while still excluding any authentication token or cookie from the response.

### Requirement 7: Single-use enforcement and idempotency

**User Story:** As a security owner, I want each verification token usable exactly once, so that a leaked or replayed link cannot be reused. (§3)

#### Acceptance Criteria

1. WHEN the Email_Verification_Service consumes a Verification_Token, THE Email_Verification_Service SHALL record a non-null `consumed_at` value on that token so that the token is no longer eligible for a future verification.
2. IF a verify request supplies a Raw_Token that hashes to a Verification_Token whose `consumed_at` is already non-null, THEN THE Email_Verification_Service SHALL raise a Gone_Error and SHALL make no change to the associated User_Entity.
3. WHILE two verify requests carrying the same valid Raw_Token are processed concurrently, THE Email_Verification_Service SHALL consume the token for at most one request and SHALL raise a Gone_Error for the other request.

### Requirement 8: Verify token validation and error mapping

**User Story:** As an unverified user, I want a clear error when my link is invalid or expired, so that I know to request a new verification email. (§3, §9)

#### Acceptance Criteria

1. IF a verify request omits the `token` value or supplies a `token` value that is empty or blank, THEN THE Email_Verification_Service SHALL raise a Validation_Error indicating that a verification token is required.
2. WHEN the Email_Verification_Service raises a Validation_Error for a missing or blank token on the `POST` route, THE Verify_Endpoint SHALL respond with HTTP status 400; on the `GET` route THE Verify_Endpoint SHALL instead redirect per Requirement 6.7.
3. IF a verify request supplies a non-blank Raw_Token that hashes to no stored Verification_Token, THEN THE Email_Verification_Service SHALL raise a Gone_Error indicating that the verification link is invalid or expired.
4. WHILE the current server time is on or after a matched Verification_Token `expires_at` instant, THE Email_Verification_Service SHALL treat that token as expired and SHALL raise a Gone_Error indicating that the verification link is invalid or expired.
5. WHEN the Email_Verification_Service raises a Gone_Error for an unknown, expired, or already-consumed token on the `POST` route, THE Verify_Endpoint SHALL respond with HTTP status 410 and a single uniform message that does not reveal which of those conditions occurred; on the `GET` route THE Verify_Endpoint SHALL instead redirect per Requirement 6.7.
6. IF the Email_Verification_Service raises any error during a verify request, THEN THE Email_Verification_Service SHALL leave the associated User_Entity `email_verified` value unchanged.

### Requirement 9: Resend endpoint and layering

**User Story:** As an unverified user, I want to request a new verification email, so that I can verify after my original link expired or was lost. (§3, §10)

#### Acceptance Criteria

1. THE Verification_Controller SHALL expose the Resend_Endpoint at the HTTP route `POST /api/v1/auth/verification/resend`.
2. THE Resend_Endpoint SHALL accept an `application/json` body carrying an email value.
3. THE Verification_Controller SHALL delegate all resend business rules and persistence to the Verification_Resend_Service, and SHALL NOT access the Verification_Token_Repository or the User_Repository directly.
4. WHEN the Verification_Resend_Service receives a submitted email value, THE Verification_Resend_Service SHALL derive the Normalized_Email by removing leading and trailing whitespace and SHALL compare it case-insensitively against the `citext` `users.email` column.
5. THE Verification_Resend_Service SHALL execute the resend operation within a single database transaction that commits only when the operation succeeds and rolls back on any failure.

### Requirement 10: Resend token re-issuance and invalidation of prior tokens

**User Story:** As an unverified user, I want a resend to give me a fresh single-use token and invalidate my older ones, so that only the newest link works. (§3)

#### Acceptance Criteria

1. WHEN the Verification_Resend_Service processes a resend for an unverified registered account, THE Verification_Resend_Service SHALL issue one new Verification_Token through the Verification_Token_Issuer with `purpose` `EMAIL_VERIFICATION` and `expires_at` set to its creation instant plus the Token_Expiry_Window of 24 hours.
2. WHEN the Verification_Resend_Service issues a new Verification_Token for an account, THE Verification_Resend_Service SHALL invalidate every earlier unconsumed `EMAIL_VERIFICATION` Verification_Token belonging to that account so that only the newly issued token can be consumed.
3. WHEN the Verification_Resend_Service issues a new Verification_Token, THE Verification_Email_Sender SHALL send a new verification email carrying the new Raw_Token to the account's email address.
4. IF a verify request later supplies the Raw_Token of a Verification_Token that was invalidated by a resend, THEN THE Email_Verification_Service SHALL raise a Gone_Error and SHALL make no change to the associated User_Entity.

### Requirement 11: Resend for an already-verified account

**User Story:** As a system owner, I want resend to be a safe no-op for an already-verified account, so that a redundant request neither issues tokens nor errors. (§3)

#### Acceptance Criteria

1. IF the Normalized_Email matches a registered account whose `email_verified` value is already `true`, THEN THE Verification_Resend_Service SHALL issue no new Verification_Token for that account.
2. IF the Normalized_Email matches an already-verified account, THEN THE Verification_Email_Sender SHALL send no verification email for that request.
3. WHEN the Verification_Resend_Service processes a resend for an already-verified account, THE Resend_Endpoint SHALL respond with the same uniform success response defined in Requirement 12.

### Requirement 12: Resend email-enumeration privacy

**User Story:** As a security owner, I want resend to reveal nothing about which emails are registered, so that the endpoint cannot be used to enumerate accounts. (§9, §11)

#### Acceptance Criteria

1. WHEN the Verification_Resend_Service completes processing a resend request that passed validation and the rate-limit check, THE Resend_Endpoint SHALL respond with HTTP status 202 and a uniform success body.
2. THE Resend_Endpoint SHALL return the identical uniform success response whether the Normalized_Email matches no account, matches an unverified account, or matches an already-verified account.
3. IF the Normalized_Email matches no registered account, THEN THE Verification_Resend_Service SHALL issue no Verification_Token and THE Verification_Email_Sender SHALL send no email, while THE Resend_Endpoint SHALL still return the uniform success response.
4. IF the submitted email value is absent, empty, or blank after trimming, THEN THE Verification_Resend_Service SHALL raise a Validation_Error and THE Resend_Endpoint SHALL respond with HTTP status 400.

### Requirement 13: Resend rate limiting via Valkey

**User Story:** As a system owner, I want resend requests rate-limited per email, so that the endpoint cannot be abused to flood an inbox or the SMTP relay. (§3, §9, §11)

#### Acceptance Criteria

1. THE Resend_Rate_Limiter SHALL permit at most Resend_Rate_Limit accepted resend requests for a given Normalized_Email within each Resend_Rate_Window.
2. WHEN a resend request for a Normalized_Email arrives within the Resend_Rate_Window after the Resend_Rate_Limit for that Normalized_Email has been reached, THE Resend_Rate_Limiter SHALL reject the request and THE Verification_Resend_Service SHALL raise a Rate_Limit_Error.
3. WHEN the Verification_Resend_Service raises a Rate_Limit_Error, THE Resend_Endpoint SHALL respond with HTTP status 429 and SHALL include a `Retry-After` header.
4. WHEN the Resend_Rate_Limiter rejects a resend request, THE Verification_Resend_Service SHALL issue no Verification_Token and THE Verification_Email_Sender SHALL send no email for that request.
5. THE Resend_Rate_Limiter SHALL hold its per-email counters in the Valkey_Store and SHALL treat the Valkey_Store as an ephemeral counter rather than as the system of record for verification state.
6. THE Resend_Rate_Limiter SHALL set an expiry on each per-email counter in the Valkey_Store equal to the Resend_Rate_Window, so that counters do not persist beyond their window.
7. IF a per-email counter is found without an expiry while the Resend_Rate_Limit is being enforced, THEN THE Resend_Rate_Limiter SHALL re-apply an expiry equal to the Resend_Rate_Window to that counter, so that a counter whose expiry was lost cannot rate-limit a Normalized_Email indefinitely.

### Requirement 14: Security seam boundary

**User Story:** As a developer, I want the verify and resend endpoints reachable without authentication under the current seam, so that unverified users can verify and resend before E4 lands the real enforcement. (§3)

#### Acceptance Criteria

1. THE Verify_Endpoint and THE Resend_Endpoint SHALL be reachable under the existing permit-all Security_Policy by a request that supplies no authentication credentials, without such a request being rejected for missing authentication.
2. THE Email_Verification_Feature SHALL introduce no JWT issuance, no authentication filter, and no authorization enforcement, and SHALL leave the existing permit-all Security_Policy unchanged, deferring those concerns to epic E4.
3. WHERE epic E4 later replaces the existing permit-all Security_Policy with one that requires authentication for application endpoints, THE Verify_Endpoint and THE Resend_Endpoint SHALL remain reachable without authentication.

### Requirement 15: Error mapping through RFC 9457

**User Story:** As an API consumer, I want verification and resend errors returned as RFC 9457 problem responses with the correct status codes, so that failures stay consistent and machine-readable. (§9, §11)

#### Acceptance Criteria

1. WHEN a Validation_Error raised during verification or resend reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 400 and a body `status` member equal to 400.
2. THE Email_Verification_Feature SHALL introduce a Gone_Error exception type and a Problem_Handler mapping that produces an RFC 9457 problem response with HTTP status 410 and a body `status` member equal to 410.
3. THE Email_Verification_Feature SHALL introduce a Rate_Limit_Error exception type and a Problem_Handler mapping that produces an RFC 9457 problem response with HTTP status 429 and a body `status` member equal to 429.
4. WHEN the Problem_Handler maps a verification or resend error, THE Problem_Handler SHALL build the response through the Problem_Detail_Factory so that the response body carries a non-empty `correlationId` member and a `timestamp` member serialized as an ISO-8601 representation in UTC.
5. WHEN the Problem_Handler maps a verification or resend error, THE Problem_Handler SHALL exclude stack traces, internal type names, SQL statements, the Raw_Token, and the Token_Hash from every member of the response body.

### Requirement 16: Dependency, secret hygiene, and quality gates

**User Story:** As a maintainer, I want the SMTP dependency added cleanly and the feature verified against the project's quality gates, so that the behavior is proven before the login epic builds on it. (§11; E2 Definition of Done)

#### Acceptance Criteria

1. THE Project_Build SHALL declare `org.springframework.boot:spring-boot-starter-mail` in `be/pom.xml` so that the Verification_Email_Sender has SMTP support on the runtime classpath.
2. THE Project_Build SHALL include an Email_Verification_Service unit test asserting that a token whose `expires_at` is on or after the current time produces a Gone_Error mapped to HTTP status 410, and that a token whose `expires_at` is in the future and is unconsumed verifies successfully (expiry boundary).
3. THE Project_Build SHALL include an Email_Verification_Service unit test asserting that consuming a token a second time produces a Gone_Error and leaves `email_verified` unchanged (single-use).
4. THE Project_Build SHALL include a Verification_Resend_Service unit test asserting that issuing a new token invalidates every earlier unconsumed `EMAIL_VERIFICATION` token for that account.
5. THE Project_Build SHALL include a Verification_Resend_Service unit test asserting that exceeding the Resend_Rate_Limit produces a Rate_Limit_Error mapped to HTTP status 429, and that a request for an unknown email and a request for an already-verified email each produce the identical uniform success response with no email sent.
6. THE Project_Build SHALL include a Cucumber BDD scenario, tagged `@auth` and executed under the Integration_Test_Context with an SMTP capture server (Mailpit or GreenMail), that signs up an account, captures the verification email, extracts the Raw_Token from the Verification_Link, submits it to the Verify_Endpoint, asserts the response succeeds, and asserts the corresponding `users` row has `email_verified` equal to `true`.
7. THE Project_Build SHALL satisfy the Coverage_Gate thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages.
8. WHEN the Spotless and the Error Prone with NullAway checks run, THE Project_Build SHALL complete without formatting or static-analysis violations.
9. IF line coverage or branch coverage over the non-excluded packages is below 90%, or IF Spotless detects a formatting violation, or IF Error Prone with NullAway detects a static-analysis violation, THEN THE Project_Build SHALL fail.
