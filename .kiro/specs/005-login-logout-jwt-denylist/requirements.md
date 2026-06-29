# Requirements Document

## Introduction

This document specifies the requirements for **Epic E3 — Login / logout (JWT + denylist)**
(`005-login-logout-jwt-denylist`), the third authentication epic for the yet-another-jira
Kanban ticket tracker. E3 builds directly on the domain foundation (`002-backend-domain-foundation`),
the sign-up endpoint (`003-signup-password-hashing`, "E1"), and email verification
(`004-email-verification-resend`, "E2").

After E1 and E2 a person can register, receive a verification email, and mark their account
`email_verified = true`, but there is still **no way to authenticate**. E3 closes that gap. It delivers
three behaviors:

1. **Login** at `POST /api/v1/auth/login`. A request carrying an email and password validates the
   password against the account's stored Argon2id hash, **rejects accounts that are not yet verified**,
   and **rejects soft-deleted accounts** (`deleted_at` not null). On success the system issues a signed
   JWT bearer access token carrying `sub` (the user id), `jti` (a unique token id), `exp` (expiry), and
   `iat` (issued-at). The token is returned in the response body — never in a URL (§9).
2. **Logout** at `POST /api/v1/auth/logout`. A request carrying a valid bearer token records that
   token's `jti` in a Valkey-backed denylist with a time-to-live equal to the token's remaining
   lifetime, so that the token is refused for the rest of its validity. Logout is idempotent and
   responds `204`.
3. **Current user** at `GET /api/v1/auth/me`. A request carrying a valid bearer token returns the
   authenticated account's id, email, and verification flag.

E3 reuses, rather than re-implements, the foundation already shipped: the `User` entity and
`UserRepository` (the case-insensitive `citext` `findByEmail`), the Argon2id `PasswordEncoder` bean
from `PasswordEncoderConfig` (verifying with `passwordEncoder.matches(raw, hash)`), the Valkey wiring
(`ValkeyConfig`, `StringRedisTemplate`) using the same ephemeral `INCR`/`EXPIRE` discipline that
`ResendRateLimiter` established, the UTC `Clock` bean from `ClockConfig`, the typed domain exceptions
and their RFC 9457 mapping in `GlobalExceptionHandler` through `ProblemDetailFactory`, the
`@ConfigurationPropertiesScan` validated-record pattern (`VerificationProperties`, `SmtpProperties`),
and the permit-all `SecurityConfig` seam. The `AuthController` (`@RequestMapping("/api/v1/auth")`) is
extended with the three new handlers; all rules live in a transactional service layer
(controller → service → repository). New login, token, and logout components are placed under the
existing `auth` sub-package layout (for example `auth/login` and `auth/token`).

E3 introduces exactly one genuinely new HTTP status mapping. The existing handler maps
`ValidationException`→400, `UnauthorizedException`→401, `NotFoundException`→404, `ConflictException`→409,
`GoneException`→410, and `RateLimitException`→429, but has **no 403 mapping**; the unverified-account
case (correct password, account not yet verified) needs a new `ForbiddenException`→403 mapping (see the
Decisions section). The new runtime dependency is a JSON Web Token library; its exact Maven coordinates
and API are deferred to the design phase per the project's shared-library rules.

**Boundary with E4.** `SecurityConfig` is still the **permit-all seam**, so no Spring Security filter
populates the `SecurityContext` for a request, and the `CurrentUserProvider` interface has **no
implementation yet**. E3 therefore introduces a JWT service that both **issues** and **validates**
tokens (signature, expiry, denylist) and resolves the caller for `logout` and `me` by reading the
`Authorization: Bearer` header **directly** inside those handlers. Replacing the permit-all seam with a
real JWT authentication filter, wrapping that same validation, and implementing `CurrentUserProvider`
all belong to **Epic E4**. E3 introduces **no global authentication enforcement** and leaves
`SecurityConfig` permit-all unchanged, exactly as E2 deferred enforcement. The **frontend** auth screens
belong to **Epic E12**; E3 delivers only the backend contract.

These requirements are derived from the E3 entry in `requirements/epics-catalog.md` and the product
requirements source `requirements/yet-another-jira.md` (primarily §3 user accounts and authentication,
§9 API and persistence expectations including tokens-never-in-URLs, §10 minimum screens, and §11
non-functional security requirements).

## Decisions and Open Questions

The constraints below were under-specified by the source material. Each is captured as a concrete,
testable decision in the requirements that follow. All items have been **confirmed**, and the
requirements encode the confirmed choices.

1. **JWT library and signing algorithm (Requirement 3, 15).** The concrete JWT library is
   deferred to the design phase, where the exact Maven coordinates and API will be confirmed per the
   shared-library rules. The requirements fix only the **observable token properties**: a signed JWT
   bearer with claims `sub` (user id), `jti` (unique token id), `exp` (expiry), and `iat` (issued-at);
   a signature verifiable by the server; and a signing secret drawn from externalized configuration and
   never committed. **Recommended default:** symmetric **HS256** with an externalized shared secret —
   simplest for a single-service deployment and sufficient because the same service both signs and
   verifies. *Alternative:* asymmetric **RS256** with a private/public key pair, warranted only if a
   separate service must verify tokens without holding the signing key (not the case in this scope).

2. **Access-token lifetime `exp` (Requirement 3, 12).** The token lifetime is externalized
   configuration (`yaj.jwt.token-ttl`). **Confirmed value: 1 hour.** Rationale: there are **no
   refresh tokens** (out of scope per §12 non-goals), so the lifetime is the whole session; one hour
   balances not forcing frequent re-login against bounding both token exposure and the size of the
   Valkey denylist (each revoked `jti` lives at most until its `exp`). The value is config-driven, so an
   environment can tune it without code changes. *Alternative:* a shorter window (for example 30
   minutes) for tighter exposure at the cost of more frequent logins.

3. **Unverified account at login (Requirement 4, 14).** Verification state is revealed **only
   after the password matches**: a request with a **correct** password for an account whose
   `email_verified` is `false` returns **403 Forbidden** with a message directing the user to verify
   their email; a request with an unknown email or a wrong password returns the generic **401** (see
   decisions 5 and 6). This requires a **new `ForbiddenException`→403 mapping** (none exists today),
   mirroring how E2 introduced the `Gone`/`RateLimit` mappings; the E3 catalog entry explicitly permits
   "`403` (or `401` with a clear reason)". Rationale: a caller who supplied the correct password has
   already proven they are very likely the account owner, so the clear "verify your email" message is
   the right experience and the residual enumeration exposure is limited to callers who already know the
   password. *Alternative:* return **401** with a reason string instead of 403, avoiding the new mapping
   but conflating "not authenticated" with "verify first".

4. **Soft-deleted account at login (Requirement 5).** An account whose `deleted_at` is
   non-null is treated as **invalid credentials** and returns the generic **401**, revealing nothing
   about the account's prior existence (anti-enumeration). The repository lookup may either reuse the
   existing `findByEmail` and let the service inspect `deleted_at`, or add a not-soft-deleted query
   (for example `findByEmailAndDeletedAtIsNull`); the recommended approach is to fetch by email and
   branch in the service, so the constant-work path (decision 5) stays uniform. The requirement fixes
   only the observable behavior (soft-deleted → uniform 401). *Alternative:* a dedicated `410 Gone` for
   deleted accounts, rejected because it leaks that the account once existed.

5. **Bad-credential uniformity (Requirement 6).** Both an unknown email and a wrong password
   return a **single uniform 401** with the same message, so the endpoint cannot be used to discover
   which emails are registered. **Recommended sub-decision:** when no active account matches the email,
   the service still performs a password verification against a fixed **dummy Argon2id hash** before
   returning 401, so that response timing does not become an oracle for "email exists". *Alternative:*
   skip the constant-work check, accepting a measurable timing difference between "no such user" and
   "wrong password".

6. **Logout semantics and idempotency (Requirement 8).** Logout requires a **well-formed,
   signature-valid, unexpired** bearer token to learn its `jti` and `exp`; it records that `jti` in the
   denylist with TTL = `exp − now` and returns **204**. Because membership in the denylist is treated as
   "ensure present" rather than "must be absent", logging out a token that is **already** denylisted
   still returns **204** (idempotent). A logout request with a **missing, malformed, signature-invalid,
   or expired** token returns **401** — there is nothing to revoke. *Alternative:* always return 204
   regardless of token validity, rejected because it hides client mistakes and gives no signal that the
   token was unusable.

7. **`me` failure modes (Requirement 10).** `GET /api/v1/auth/me` returns **401** when the
   bearer token is missing, malformed, signature-invalid, expired, or present in the denylist, and also
   when the token's `sub` resolves to no account or to a now-soft-deleted account. A single uniform 401
   covers every "not a valid, live session" case.

8. **Denylist key scheme and TTL (Requirement 9).** Each revoked token is stored as one Valkey
   key per `jti` under a fixed prefix (recommended `auth:jwt:denylist:<jti>`), with a minimal value
   (no user id, email, or token material) and an expiry equal to the token's remaining lifetime so the
   entry self-expires. Valkey is **never the system of record**: the token's own `exp` remains the
   authority for expiry, and the denylist only records revocations within that window (§9), exactly the
   ephemeral discipline `ResendRateLimiter` uses.

9. **`me` response shape (Requirement 10).** The success body is `{ id, email, emailVerified }`.
   The response never echoes the password hash and never includes a new or refreshed access token
   (`me` is a read, not a token-issuing endpoint).

10. **Authentication boundary in E3 (Requirement 13).** E3 resolves the caller's identity for
    `logout` and `me` by reading and validating the `Authorization: Bearer` token **directly in the
    handlers**, because the permit-all `SecurityConfig` means no filter has populated the
    `SecurityContext` and `CurrentUserProvider` has no implementation yet. E3 leaves `SecurityConfig`
    permit-all **unchanged**, adds **no** authentication filter and **no** authorization enforcement, and
    implements **no** `CurrentUserProvider` — all deferred to Epic E4, which will wrap this same
    validation in a Spring Security filter. Confirm this boundary.

## Glossary

- **Login_Logout_Feature**: The complete E3 deliverable taken as a whole: the login, logout, and current-user endpoints, their controller handlers and services, the JWT issuance-and-validation service, the Valkey token denylist, and the supporting configuration wiring.
- **Auth_Controller**: The existing Spring MVC controller `com.bovae.yaj.web.controller.AuthController` (`@RequestMapping("/api/v1/auth")`), extended by E3 with the login, logout, and current-user handlers and restricted to HTTP concerns.
- **Login_Endpoint**: The HTTP endpoint exposed at `POST /api/v1/auth/login` that authenticates credentials and issues an Access_Token.
- **Logout_Endpoint**: The HTTP endpoint exposed at `POST /api/v1/auth/logout` that revokes the caller's Access_Token.
- **Current_User_Endpoint**: The HTTP endpoint exposed at `GET /api/v1/auth/me` that returns the authenticated account.
- **Login_Service**: The transactional service component that implements the login business rules: email normalization, credential verification, the verified and not-soft-deleted checks, and the request to issue an Access_Token.
- **Logout_Service**: The service component that implements the logout business rules: validating the presented Access_Token enough to learn its claims and recording the revocation in the Token_Denylist.
- **Jwt_Service**: The component that **issues** a signed Access_Token for a User_Entity and **validates** a presented Access_Token (signature, expiry, denylist membership, required claims), resolving the Token_Subject_Claim.
- **Access_Token**: The signed JSON Web Token bearer credential issued at login and presented on the Authorization Bearer_Header for logout and current-user requests.
- **Token_Subject_Claim**: The JWT `sub` claim, equal to the issuing User_Entity id (a UUID).
- **Token_Id_Claim**: The JWT `jti` claim, a unique identifier assigned to each issued Access_Token and used as the Token_Denylist key.
- **Token_Expiry_Claim**: The JWT `exp` claim, the instant after which the Access_Token is no longer valid.
- **Token_Issued_At_Claim**: The JWT `iat` claim, the instant at which the Access_Token was issued.
- **Token_Signature**: The cryptographic signature over the Access_Token, verifiable by the server using the Jwt_Signing_Secret.
- **Jwt_Signing_Secret**: The externalized secret (or key) used by the Jwt_Service to sign and verify the Token_Signature, supplied through configuration and never committed to source control.
- **Token_Ttl**: The externalized validity duration applied to every issued Access_Token to compute its Token_Expiry_Claim.
- **Jwt_Properties**: A validated `@ConfigurationProperties` record (prefix `yaj.jwt`) holding the Jwt_Signing_Secret and the Token_Ttl, mirroring the existing `VerificationProperties` / `SmtpProperties` pattern and auto-registered by the existing `@ConfigurationPropertiesScan`.
- **Password_Encoder**: The existing Argon2id `org.springframework.security.crypto.password.PasswordEncoder` bean defined in `com.bovae.yaj.config.PasswordEncoderConfig`, used through its `matches(rawPassword, encodedPassword)` operation.
- **Dummy_Password_Hash**: A fixed, valid Argon2id hash against which the Login_Service verifies a submitted password when no active account matches the email, so that response timing does not reveal whether the email is registered.
- **Token_Denylist**: The Valkey-backed set of revoked Token_Id_Claim values that the Jwt_Service consults during validation; an ephemeral revocation cache, never the system of record.
- **Denylist_Key**: A single Valkey key representing one revoked Token_Id_Claim, formed from a fixed prefix and the Token_Id_Claim (for example `auth:jwt:denylist:<jti>`).
- **Valkey_Store**: The existing Valkey instance wired through `com.bovae.yaj.config.ValkeyConfig` and the `StringRedisTemplate`, used by E3 only as an ephemeral revocation cache.
- **User_Entity**: The existing JPA entity `com.bovae.yaj.domain.model.User` mapping the `users` table, including the `email` (`citext`), `password_hash`, `email_verified`, and `deleted_at` columns.
- **User_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.UserRepository`, including its case-insensitive `findByEmail` lookup, optionally extended by E3 with a not-soft-deleted lookup.
- **Normalized_Email**: A submitted email value after removal of leading and trailing whitespace, compared case-insensitively against the `citext` `users.email` column.
- **Soft_Deleted_User**: A User_Entity whose `deleted_at` value is non-null.
- **Bearer_Header**: The HTTP `Authorization` request header carrying the Access_Token in the `Bearer <token>` scheme.
- **Current_User_Provider**: The existing interface `com.bovae.yaj.security.CurrentUserProvider`, which E3 deliberately leaves without an implementation (deferred to epic E4).
- **Clock**: The existing UTC `java.time.Clock` bean defined in `com.bovae.yaj.config.ClockConfig`, used as the time source for issuing and validating tokens.
- **Validation_Error**: A `com.bovae.yaj.error.ValidationException`, mapped by the Problem_Handler to HTTP status 400.
- **Unauthorized_Error**: A `com.bovae.yaj.error.UnauthorizedException`, mapped by the Problem_Handler to HTTP status 401.
- **Forbidden_Error**: A new typed domain exception introduced by E3 for the unverified-account-at-login case, mapped by the Problem_Handler to HTTP status 403.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalExceptionHandler` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`) that produces RFC 9457 problem responses.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and a UTC `timestamp`.
- **Security_Policy**: The existing `com.bovae.yaj.config.SecurityConfig` permit-all seam under which every request is authorized without authentication, to be replaced in epic E4.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/model/**`, `**/config/**`, `**/mapper/*Impl*`, and `**/*Application.*`.
- **Login_Rate_Limiter**: The component that enforces per-email rate limiting on login attempts, stored in Valkey with a fixed window counter following the same ephemeral discipline as the ResendRateLimiter.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance and a Valkey instance, both provisioned by Testcontainers, under the `bdd` profile.

## Requirements

### Requirement 1: Login endpoint and layering

**User Story:** As a verified user, I want to submit my email and password to a login endpoint, so that the system can authenticate me and issue an access token. (§3, §9)

#### Acceptance Criteria

1. THE Auth_Controller SHALL expose the Login_Endpoint at the HTTP route `POST /api/v1/auth/login`.
2. THE Login_Endpoint SHALL accept an `application/json` body carrying an email value and a password value.
3. THE Auth_Controller SHALL delegate all login business rules, credential verification, and token issuance to the Login_Service, and SHALL NOT access the User_Repository, the Password_Encoder, or the Jwt_Service directly.
4. WHEN the Login_Service receives a submitted email value, THE Login_Service SHALL derive the Normalized_Email by removing leading and trailing whitespace and SHALL compare it case-insensitively against the `citext` `users.email` column.
5. IF a login request omits the email value, supplies an email value that is empty or blank after trimming, or omits the password value or supplies an empty password value, THEN THE Login_Service SHALL raise a Validation_Error and THE Login_Endpoint SHALL respond with HTTP status 400.

### Requirement 2: Successful login and access-token issuance

**User Story:** As a verified user, I want a correct email and password to return a signed access token, so that I can make authenticated requests. (§3, §9)

#### Acceptance Criteria

1. WHEN a login request supplies a Normalized_Email that matches a registered User_Entity whose `deleted_at` is null and whose `email_verified` is `true`, and the submitted password verifies against that User_Entity `password_hash`, THE Login_Service SHALL request the Jwt_Service to issue exactly one Access_Token for that User_Entity.
2. WHEN the Login_Service verifies a submitted password, THE Login_Service SHALL use the Password_Encoder `matches` operation against the stored `password_hash` and SHALL NOT compare the password as plaintext.
3. WHEN the Jwt_Service issues an Access_Token for a successful login, THE Login_Endpoint SHALL respond with HTTP status 200 and a response body carrying the Access_Token.
4. WHEN the Login_Endpoint returns an Access_Token, THE Login_Endpoint SHALL place the Access_Token only in the response body and SHALL NOT place the Access_Token in any URL, query parameter, or redirect location, consistent with §9.
5. WHEN the Login_Endpoint returns any response, THE Login_Endpoint SHALL exclude the User_Entity `password_hash` from that response.

### Requirement 3: Access-token properties, claims, and signing

**User Story:** As a security owner, I want every issued token to be a signed JWT with well-defined claims and a configured signing secret, so that the server can verify tokens and bound their lifetime. (§3, §9, §11)

#### Acceptance Criteria

1. WHEN the Jwt_Service issues an Access_Token, THE Jwt_Service SHALL set the Token_Subject_Claim (`sub`) to the issuing User_Entity id.
2. WHEN the Jwt_Service issues an Access_Token, THE Jwt_Service SHALL assign a Token_Id_Claim (`jti`) that is unique for each issued Access_Token.
3. WHEN the Jwt_Service issues an Access_Token, THE Jwt_Service SHALL set the Token_Issued_At_Claim (`iat`) to the issuing instant read from the Clock in UTC, and SHALL set the Token_Expiry_Claim (`exp`) to that issuing instant plus the configured Token_Ttl.
4. WHEN the Jwt_Service issues an Access_Token, THE Jwt_Service SHALL produce a Token_Signature over the token using the Jwt_Signing_Secret, such that the Token_Signature is verifiable by the server.
5. THE Jwt_Service SHALL obtain the Jwt_Signing_Secret from externalized configuration and SHALL NOT use any signing secret hardcoded in a source-controlled file.
6. FOR an Access_Token issued by the Jwt_Service, a subsequent validation of that same Access_Token by the Jwt_Service SHALL confirm a valid Token_Signature and SHALL recover the original Token_Subject_Claim (issue-then-validate round trip).

### Requirement 4: Unverified account rejection at login

**User Story:** As an unverified user, I want a clear instruction to verify my email when I try to log in, so that I know why I cannot yet sign in. (§3)

#### Acceptance Criteria

1. IF a login request supplies a Normalized_Email that matches a registered User_Entity whose `deleted_at` is null, and the submitted password verifies against that User_Entity `password_hash`, but that User_Entity `email_verified` value is `false`, THEN THE Login_Service SHALL raise a Forbidden_Error and SHALL request no Access_Token for that account.
2. WHEN the Login_Service raises a Forbidden_Error for an unverified account, THE Login_Endpoint SHALL respond with HTTP status 403 and a message directing the user to verify the account email address.
3. WHEN the Login_Service rejects an unverified account, THE Login_Endpoint SHALL exclude any Access_Token from the response.

### Requirement 5: Soft-deleted account rejection at login

**User Story:** As a security owner, I want a soft-deleted account to be unable to log in and to reveal nothing about itself, so that disabling an account is effective and non-enumerable. (§3, §9, §11)

#### Acceptance Criteria

1. IF a login request supplies a Normalized_Email that matches a Soft_Deleted_User, THEN THE Login_Service SHALL reject the login as invalid credentials and SHALL raise an Unauthorized_Error.
2. WHEN the Login_Service rejects a Soft_Deleted_User, THE Login_Endpoint SHALL respond with the same uniform HTTP status 401 and message used for an unknown email and for a wrong password, revealing nothing about the account's existence or state.
3. WHEN the Login_Service rejects a Soft_Deleted_User, THE Login_Service SHALL request no Access_Token and THE Login_Endpoint SHALL exclude any Access_Token from the response.

### Requirement 6: Bad-credential uniformity and enumeration resistance

**User Story:** As a security owner, I want every failed login to look the same, so that the endpoint cannot be used to discover which emails are registered. (§9, §11)

#### Acceptance Criteria

1. IF a login request supplies a Normalized_Email that matches no registered User_Entity, THEN THE Login_Service SHALL raise an Unauthorized_Error.
2. IF a login request supplies a Normalized_Email that matches a registered User_Entity whose `deleted_at` is null, but the submitted password does not verify against that User_Entity `password_hash`, THEN THE Login_Service SHALL raise an Unauthorized_Error.
3. WHEN the Login_Service raises an Unauthorized_Error for an unknown email, a wrong password, or a Soft_Deleted_User, THE Login_Endpoint SHALL respond with HTTP status 401 and a single uniform message that does not reveal whether the email is registered.
4. WHEN a login request supplies a Normalized_Email that matches no registered User_Entity or matches a Soft_Deleted_User, THE Login_Service SHALL perform a password verification against the Dummy_Password_Hash before raising the Unauthorized_Error, so that the elapsed processing time does not reveal whether the email is registered.

### Requirement 7: Access-token validation

**User Story:** As a security owner, I want presented tokens validated for signature, expiry, and revocation, so that only live, untampered, non-revoked tokens authenticate a caller. (§3, §9)

#### Acceptance Criteria

1. WHEN the Jwt_Service validates a presented Access_Token, THE Jwt_Service SHALL reject the token unless its Token_Signature verifies against the Jwt_Signing_Secret.
2. WHILE the current server time read from the Clock is on or after a presented Access_Token Token_Expiry_Claim, THE Jwt_Service SHALL treat that token as expired and SHALL reject it.
3. WHEN the Jwt_Service validates a presented Access_Token whose Token_Id_Claim is present in the Token_Denylist, THE Jwt_Service SHALL reject the token.
4. IF a presented Access_Token is structurally malformed, or is missing the Token_Subject_Claim, the Token_Id_Claim, or the Token_Expiry_Claim, THEN THE Jwt_Service SHALL reject the token.
5. WHEN the Jwt_Service rejects a presented Access_Token, THE Jwt_Service SHALL raise an Unauthorized_Error.

### Requirement 8: Logout, token revocation, and idempotency

**User Story:** As a logged-in user, I want logout to revoke my current token, so that the token can no longer be used after I sign out. (§3, §9)

#### Acceptance Criteria

1. THE Auth_Controller SHALL expose the Logout_Endpoint at the HTTP route `POST /api/v1/auth/logout`.
2. WHEN the Logout_Endpoint receives a request, THE Logout_Endpoint SHALL read the Access_Token from the Authorization Bearer_Header and SHALL NOT read it from any URL or query parameter.
3. WHEN the Logout_Endpoint receives an Access_Token whose Token_Signature is valid and whose Token_Expiry_Claim is in the future, THE Logout_Service SHALL record that token Token_Id_Claim in the Token_Denylist with an entry expiry equal to the interval from the current server time to the Token_Expiry_Claim.
4. WHEN the Logout_Service records a revocation, THE Logout_Endpoint SHALL respond with HTTP status 204 and an empty body.
5. WHEN the Logout_Endpoint receives a valid Access_Token whose Token_Id_Claim is already present in the Token_Denylist, THE Logout_Service SHALL keep that token denylisted and THE Logout_Endpoint SHALL respond with HTTP status 204, so that repeated logout of the same token is idempotent.
6. IF a logout request supplies no Access_Token, or supplies an Access_Token that is malformed, has an invalid Token_Signature, or whose Token_Expiry_Claim is on or before the current server time, THEN THE Logout_Service SHALL raise an Unauthorized_Error and THE Logout_Endpoint SHALL respond with HTTP status 401.

### Requirement 9: Token denylist storage in Valkey

**User Story:** As a system owner, I want revoked tokens recorded in Valkey with self-expiring entries, so that revocations are enforced without Valkey becoming the system of record. (§9, §11)

#### Acceptance Criteria

1. THE Token_Denylist SHALL store one Denylist_Key per revoked Token_Id_Claim in the Valkey_Store, formed from a fixed prefix and the Token_Id_Claim.
2. WHEN the Logout_Service adds a Denylist_Key, THE Logout_Service SHALL set that key's value to a minimal marker that contains no User_Entity id, no email, and no Access_Token material.
3. WHEN the Logout_Service adds a Denylist_Key, THE Logout_Service SHALL set an expiry on that key equal to the interval from the current server time to the revoked token Token_Expiry_Claim, so that the entry self-expires when the token would have expired.
4. THE Token_Denylist SHALL treat the Valkey_Store as an ephemeral revocation cache rather than as the system of record, the revoked token's own Token_Expiry_Claim remaining the authority for expiry, consistent with §9.
5. WHILE a revoked token has not yet reached its Token_Expiry_Claim and its Denylist_Key remains present in the Valkey_Store, THE Jwt_Service SHALL treat that token as rejected.

### Requirement 10: Current-user endpoint

**User Story:** As a logged-in user, I want an endpoint that returns my identity, so that the client can show who is signed in. (§3, §9)

#### Acceptance Criteria

1. THE Auth_Controller SHALL expose the Current_User_Endpoint at the HTTP route `GET /api/v1/auth/me`.
2. WHEN the Current_User_Endpoint receives a request, THE Current_User_Endpoint SHALL read the Access_Token from the Authorization Bearer_Header and SHALL request the Jwt_Service to validate it.
3. WHEN the Jwt_Service validates the Access_Token and its Token_Subject_Claim resolves to a registered User_Entity whose `deleted_at` is null, THE Current_User_Endpoint SHALL respond with HTTP status 200 and a body carrying that User_Entity id, email, and `email_verified` value.
4. THE Current_User_Endpoint SHALL exclude the User_Entity `password_hash` from every response and SHALL NOT issue or include any Access_Token in the response.
5. IF a Current_User_Endpoint request supplies no Access_Token, or supplies an Access_Token that is malformed, has an invalid Token_Signature, is expired, or is present in the Token_Denylist, THEN THE Jwt_Service SHALL raise an Unauthorized_Error and THE Current_User_Endpoint SHALL respond with HTTP status 401.
6. IF the validated Token_Subject_Claim resolves to no registered User_Entity or resolves to a Soft_Deleted_User, THEN THE Current_User_Endpoint SHALL raise an Unauthorized_Error and respond with HTTP status 401.

### Requirement 11: Token confidentiality and secret hygiene

**User Story:** As a security owner, I want tokens, passwords, and signing secrets kept out of URLs, logs, responses, and source, so that authentication material cannot leak. (§3, §9, §11)

#### Acceptance Criteria

1. THE Login_Logout_Feature SHALL place the Access_Token only in HTTP request and response bodies and in the Authorization header, and SHALL NOT place any Access_Token, Token_Id_Claim, or Jwt_Signing_Secret in any URL or query parameter, consistent with §9.
2. THE Login_Logout_Feature SHALL exclude the submitted password, the User_Entity `password_hash`, the Jwt_Signing_Secret, and any full Access_Token from every log statement it emits at any log level, including any exception message and stack trace those statements carry.
3. THE Login_Logout_Feature SHALL keep all source-controlled files free of plaintext Jwt_Signing_Secret values, representing any such value only as an environment-variable reference or non-production placeholder that resolves at runtime.
4. THE Login_Logout_Feature SHALL verify passwords with the Argon2id Password_Encoder and SHALL introduce no alternative password storage or comparison mechanism, consistent with §3.

### Requirement 12: JWT and login configuration externalization

**User Story:** As a maintainer, I want the signing secret and token lifetime supplied from configuration, so that they can be set per environment and no secret is committed. (§3, §11)

#### Acceptance Criteria

1. THE Login_Logout_Feature SHALL read the Jwt_Signing_Secret and the Token_Ttl from the Jwt_Properties record bound to the `yaj.jwt` configuration prefix, auto-registered by the existing `@ConfigurationPropertiesScan`.
2. THE Jwt_Properties SHALL bind the Jwt_Signing_Secret to an externalized environment variable so that the secret is supplied per environment and never committed to source control.
3. IF the Jwt_Signing_Secret is absent or blank at startup, THEN THE Project_Build SHALL fail the application startup with an error identifying the missing signing secret.
4. THE Jwt_Properties SHALL require the Token_Ttl to be a positive duration, and THE Jwt_Service SHALL apply the configured Token_Ttl to every issued Token_Expiry_Claim.

### Requirement 13: Security seam boundary

**User Story:** As a developer, I want login, logout, and current-user reachable under the current seam with identity resolved directly from the bearer token, so that authentication works before E4 lands the real enforcement. (§3, §9)

#### Acceptance Criteria

1. THE Login_Endpoint, THE Logout_Endpoint, and THE Current_User_Endpoint SHALL be reachable under the existing permit-all Security_Policy by a request that supplies no Spring Security authentication, without such a request being rejected by an authentication filter.
2. WHEN the Logout_Endpoint or the Current_User_Endpoint requires the calling user's identity, THE Login_Logout_Feature SHALL resolve that identity by reading and validating the Access_Token from the Authorization Bearer_Header directly within the request handling, rather than relying on the Spring `SecurityContext`, because no authentication filter populates that context before epic E4.
3. THE Login_Logout_Feature SHALL provide no implementation of the existing Current_User_Provider interface, deferring that to epic E4.
4. THE Login_Logout_Feature SHALL leave the existing permit-all Security_Policy unchanged and SHALL introduce no authentication filter and no authorization enforcement on application endpoints, deferring those concerns to epic E4.
5. WHERE epic E4 later replaces the existing permit-all Security_Policy with one that requires authentication for application endpoints, THE Login_Endpoint SHALL remain reachable without authentication so that a user can obtain an Access_Token.

### Requirement 14: Error mapping through RFC 9457

**User Story:** As an API consumer, I want login, logout, and current-user errors returned as RFC 9457 problem responses with the correct status codes, so that failures stay consistent and machine-readable. (§9, §11)

#### Acceptance Criteria

1. WHEN an Unauthorized_Error raised during login, logout, or current-user resolution reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 401 and a body `status` member equal to 401.
2. THE Login_Logout_Feature SHALL introduce a Forbidden_Error exception type and a Problem_Handler mapping that produces an RFC 9457 problem response with HTTP status 403 and a body `status` member equal to 403.
3. WHEN a Validation_Error raised during login reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 400 and a body `status` member equal to 400.
4. WHEN the Problem_Handler maps a login, logout, or current-user error, THE Problem_Handler SHALL build the response through the Problem_Detail_Factory so that the response body carries a non-empty `correlationId` member and a `timestamp` member serialized as an ISO-8601 representation in UTC.
5. WHEN the Problem_Handler maps a login, logout, or current-user error, THE Problem_Handler SHALL exclude stack traces, internal type names, SQL statements, the submitted password, the User_Entity `password_hash`, the Jwt_Signing_Secret, and any Access_Token from every member of the response body.

### Requirement 15: Dependency, secret hygiene, and quality gates

**User Story:** As a maintainer, I want the JWT dependency added cleanly and the feature verified against the project's quality gates, so that authentication is proven before the enforcement epic builds on it. (§11; E3 Definition of Done)

#### Acceptance Criteria

1. THE Project_Build SHALL declare a JSON Web Token library in `be/pom.xml` so that the Jwt_Service issues and validates signed tokens through that library rather than a hand-rolled implementation, with the exact Maven coordinates and API confirmed during the design phase.
2. THE Project_Build SHALL include a Login_Service unit test asserting that a correct email and password for a verified, non-deleted account causes an Access_Token to be issued, and that a wrong password for that same account produces an Unauthorized_Error mapped to HTTP status 401 (good and bad password).
3. THE Project_Build SHALL include a Login_Service unit test asserting that a correct password for an unverified account produces a Forbidden_Error mapped to HTTP status 403 (unverified rejected).
4. THE Project_Build SHALL include a Login_Service unit test asserting that a Normalized_Email matching a Soft_Deleted_User produces the same uniform Unauthorized_Error mapped to HTTP status 401 as an unknown email (soft-deleted rejected, anti-enumeration).
5. THE Project_Build SHALL include a Logout_Service unit test asserting that logging out a valid Access_Token records its Token_Id_Claim in the Token_Denylist with an expiry derived from the Token_Expiry_Claim, and that logging out an already-denylisted token still responds with HTTP status 204 (denylist add and idempotency).
6. THE Project_Build SHALL include Jwt_Service unit tests asserting that an Access_Token with a valid Token_Signature, a future Token_Expiry_Claim, and no Token_Denylist entry validates and resolves its Token_Subject_Claim, and that an Access_Token that is expired, signature-invalid, or denylisted is rejected with an Unauthorized_Error (current-user happy path and 401 paths exercised through validation).
7. THE Project_Build SHALL include a Cucumber BDD scenario, tagged `@auth` and executed under the Integration_Test_Context with Testcontainers PostgreSQL and Valkey, that signs up an account, verifies it, logs in to obtain an Access_Token, calls `GET /api/v1/auth/me` with that token and asserts the returned id, email, and `email_verified`, logs out, and asserts that a subsequent `GET /api/v1/auth/me` with the same token responds with HTTP status 401 (signup → verify → login → authed `me` → logout → token rejected).
8. THE Project_Build SHALL satisfy the Coverage_Gate thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages.
9. WHEN the Spotless and the Error Prone with NullAway checks run, THE Project_Build SHALL complete without formatting or static-analysis violations.
10. IF line coverage or branch coverage over the non-excluded packages is below 90%, or IF Spotless detects a formatting violation, or IF Error Prone with NullAway detects a static-analysis violation, THEN THE Project_Build SHALL fail.


### Requirement 16: Login rate limiting

**User Story:** As a security owner, I want the login endpoint to rate-limit attempts per email address, so that brute-force attacks are infeasible without locking out other users. (§11)

#### Acceptance Criteria

1. THE Login_Service SHALL enforce a per-email rate limit on login attempts, counting each attempt that passes input validation (non-blank email and non-empty password) toward the limit, regardless of whether credentials are valid.
2. IF the number of login attempts for a Normalized_Email within the configured fixed window exceeds the configured login rate limit, THEN THE Login_Service SHALL raise a RateLimitException and SHALL NOT perform credential verification, user lookup, or token issuance for that request.
3. WHEN the Login_Service raises a RateLimitException, THE Login_Endpoint SHALL respond with HTTP status 429 and a `Retry-After` header indicating the seconds remaining in the rate-limit window.
4. THE Login_Logout_Feature SHALL read the login rate limit and fixed window from the Jwt_Properties record (`yaj.jwt.login-rate-limit` and `yaj.jwt.login-rate-window`), defaulting to 5 attempts per 15-minute window.
5. THE Login_Logout_Feature SHALL store login attempt counters in the Valkey_Store under a key prefix (`auth:login:rl:`) using a SHA-256 hash of the normalized lowercase email, so that no PII is stored in Valkey keys.
6. THE Login_Logout_Feature SHALL set an expiry on each rate-limit counter equal to the configured fixed window, so that counters self-expire and do not accumulate indefinitely.
