# Requirements Document

## Introduction

This document specifies the requirements for **Epic E1 — Sign-up + password
hashing** (`003-signup-password-hashing`), the first authentication epic for the
yet-another-jira Kanban ticket tracker. E1 builds directly on the backend domain
foundation delivered in `002-backend-domain-foundation`, where the `users` table
is mapped by the `com.bovae.yaj.domain.model.User` entity, the
`com.bovae.yaj.domain.repository.UserRepository` exposes a case-insensitive
`findByEmail`, the typed domain exceptions (`ConflictException`,
`ValidationException`) are mapped to RFC 9457 problem responses by
`com.bovae.yaj.web.error.GlobalExceptionHandler` through
`com.bovae.yaj.web.error.ProblemDetailFactory`, and security is a permit-all
seam.

E1 delivers exactly one HTTP endpoint, `POST /api/v1/auth/signup`, that
registers a new local account from an email address and a password. The endpoint
trims and case-insensitively de-duplicates the email, enforces email and password
length bounds, hashes the password with the Argon2id algorithm so that the plaintext is
never stored, logged, or returned, and persists the new account in an unverified
state. Success returns `201 Created` with a response body that excludes both the
password and the password hash. Malformed input returns `400`, and a duplicate
email returns `409`, both flowing through the existing RFC 9457 problem path.

E1 deliberately stops at account creation. Issuing the email-verification token,
sending the verification message over SMTP, and the verify/resend endpoints are
the responsibility of **Epic E2 — Email verification + resend**; E1 only leaves
the new account unverified. Replacing the permit-all security seam with real
JWT-based authentication and authorization is the responsibility of **Epic E4**;
the signup endpoint is reachable under the existing permit-all policy and E1
introduces no authentication enforcement of its own.

These requirements are derived from the E1 entry in
`requirements/epics-catalog.md` and the product requirements source
`requirements/yet-another-jira.md` (primarily §3 user accounts and
authentication, §9 API and persistence expectations, and §11 non-functional
security requirements).

## Glossary

- **Signup_Feature**: The complete E1 deliverable taken as a whole: the signup HTTP endpoint, its controller and service, the Argon2id password-encoder configuration, and the supporting dependency wiring.
- **Signup_Endpoint**: The HTTP endpoint exposed at `POST /api/v1/auth/signup`.
- **Signup_Controller**: The Spring MVC controller that handles `Signup_Endpoint` requests and is restricted to HTTP concerns.
- **Signup_Service**: The transactional service component that implements the signup business rules and persists the new account.
- **Signup_Request**: The JSON request payload submitted to the `Signup_Endpoint`, carrying an email value and a password value.
- **Signup_Response**: The JSON response body the `Signup_Endpoint` returns on successful account creation.
- **Submitted_Email**: The raw email value carried by the `Signup_Request`.
- **Submitted_Password**: The raw password value carried by the `Signup_Request`.
- **Normalized_Email**: The `Submitted_Email` after removal of leading and trailing whitespace.
- **Minimum_Password_Length**: The lower bound of 8 characters that the `Submitted_Password` must satisfy.
- **Maximum_Password_Length**: The upper bound of 128 characters that the `Submitted_Password` must not exceed.
- **Minimum_Email_Length**: The lower bound of 6 characters that the `Normalized_Email` must satisfy.
- **Maximum_Email_Length**: The upper bound of 254 characters (RFC 5321) that the `Normalized_Email` must not exceed.
- **Password_Encoder**: The Spring Security `PasswordEncoder` bean configured to use the Argon2id algorithm, defined in the `com.bovae.yaj.config` package.
- **Password_Hash**: The Argon2id hash string the `Password_Encoder` produces from the `Submitted_Password`.
- **User_Entity**: The existing JPA entity `com.bovae.yaj.domain.model.User` that maps the `users` table, including the `email` (`citext`), `password_hash`, `email_verified`, `created_at`, and `modified_at` columns.
- **User_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.UserRepository`, including its `findByEmail` lookup.
- **Email_Uniqueness_Constraint**: The existing database `UNIQUE` constraint on the `citext` `users.email` column that rejects a duplicate email case-insensitively.
- **Validation_Error**: A `com.bovae.yaj.error.ValidationException`, mapped by the `Problem_Handler` to HTTP status 400.
- **Conflict_Error**: A `com.bovae.yaj.error.ConflictException`, mapped by the `Problem_Handler` to HTTP status 409.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalExceptionHandler` (`@RestControllerAdvice`) that produces RFC 9457 problem responses.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and a UTC `timestamp`.
- **Security_Policy**: The existing `com.bovae.yaj.config.SecurityConfig` permit-all seam under which every request is authorized without authentication, to be replaced in epic E4.
- **Email_Verification_Flow**: The token issuance, SMTP delivery, and verify/resend behavior owned by epic E2 and explicitly outside E1 scope.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/model/**`, `**/config/**`, `**/mapper/*Impl*`, and `**/*Application.*`.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance provisioned by Testcontainers under the `bdd` profile.

## Requirements

### Requirement 1: Signup endpoint and layering

**User Story:** As an unregistered visitor, I want to submit an email and password to a signup endpoint, so that the system creates a local account for me. (§3, §9)

#### Acceptance Criteria

1. THE Signup_Controller SHALL expose the `Signup_Endpoint` at the HTTP route `POST /api/v1/auth/signup`.
2. THE Signup_Endpoint SHALL accept a `Signup_Request` as an `application/json` body carrying an email value and a password value.
3. THE Signup_Controller SHALL delegate all signup business rules and persistence to the Signup_Service, and SHALL NOT access the User_Repository or perform any persistence operation directly.
4. THE Signup_Service SHALL execute the entire signup operation within a single database transaction that commits only when the signup succeeds and rolls back on any failure, leaving no partially persisted account.
5. WHEN a `Signup_Request` satisfies every validation and uniqueness rule, THE Signup_Service SHALL persist exactly one new User_Entity through the User_Repository.
6. IF any validation or uniqueness rule fails during signup, THEN THE Signup_Service SHALL persist no new User_Entity.
7. IF the request body is absent, is not `application/json`, or cannot be parsed as a JSON object, THEN THE Signup_Controller SHALL reject the request without invoking the Signup_Service and SHALL persist no new User_Entity.

### Requirement 2: Email normalization

**User Story:** As a system owner, I want submitted emails trimmed and compared case-insensitively, so that the same address is never registered twice in a different letter case or with surrounding whitespace. (§3)

#### Acceptance Criteria

1. WHEN the Signup_Service receives a Submitted_Email, THE Signup_Service SHALL derive the Normalized_Email by removing every leading and trailing whitespace character while preserving the interior characters and the original letter case of the Submitted_Email.
2. WHEN the Signup_Service compares the Normalized_Email against existing accounts, THE Signup_Service SHALL treat the Normalized_Email and a stored email value as the same account email when they are identical except for letter case, consistent with the case-insensitive `citext` `users.email` column.
3. WHEN the Signup_Service persists a new User_Entity, THE Signup_Service SHALL store the Normalized_Email, with its original letter case preserved, as the account email value so that the stored value contains no leading or trailing whitespace.

### Requirement 3: Email format validation

**User Story:** As a system owner, I want malformed email values rejected, so that only syntactically valid email addresses are stored. (§3, §9)

#### Acceptance Criteria

1. IF the Submitted_Email is absent, empty, or blank after trimming, THEN THE Signup_Service SHALL raise a Validation_Error indicating that an email address is required.
2. IF the Normalized_Email does not contain exactly one `@` character separating a non-empty local part from a domain part that holds at least one dot between non-empty labels, or the Normalized_Email exceeds the Maximum_Email_Length (254 characters), THEN THE Signup_Service SHALL raise a Validation_Error identifying the email as malformed.
3. WHEN the Signup_Service raises a Validation_Error for a malformed or missing email, THE Signup_Endpoint SHALL respond with HTTP status 400.
4. IF the Normalized_Email contains any whitespace character after leading and trailing whitespace has been removed, THEN THE Signup_Service SHALL raise a Validation_Error identifying the email as malformed.
5. IF the Normalized_Email contains fewer characters than the Minimum_Email_Length (6 characters), THEN THE Signup_Service SHALL raise a Validation_Error identifying the email as malformed.

### Requirement 4: Password length policy

**User Story:** As a security owner, I want minimum and maximum password lengths enforced on the server, so that weak passwords are rejected regardless of client-side checks. (§3, §9, §11)

#### Acceptance Criteria

1. IF the Submitted_Password is absent, empty, or consists solely of whitespace characters (blank), THEN THE Signup_Service SHALL raise a Validation_Error indicating that a password is required, regardless of the number of characters present.
2. IF the Submitted_Password is non-blank and contains fewer characters than the Minimum_Password_Length (8 characters), counting every character including whitespace and without trimming, THEN THE Signup_Service SHALL raise a Validation_Error identifying the password as too short.
3. WHEN the Submitted_Password is non-blank and contains at least the Minimum_Password_Length (8 characters) and at most the Maximum_Password_Length (128 characters), counting every character including whitespace and without trimming, THE Signup_Service SHALL accept the Submitted_Password as satisfying the password length policy.
4. WHEN the Signup_Service raises a Validation_Error for a missing, blank, too-short, or too-long password, THE Signup_Endpoint SHALL respond with HTTP status 400 and an error response indicating the password validation failure without exposing internal details.
5. IF the Signup_Service raises a Validation_Error for the Submitted_Password, THEN THE Signup_Service SHALL NOT create a user account and SHALL leave persistent state unchanged.
6. THE Signup_Service SHALL enforce the password length policy on the server as the authoritative check, independently of any client-side validation.
7. IF the Submitted_Password is non-blank and contains more characters than the Maximum_Password_Length (128 characters), counting every character including whitespace and without trimming, THEN THE Signup_Service SHALL raise a Validation_Error identifying the password as too long.

### Requirement 5: Duplicate email rejection on pre-check

**User Story:** As a system owner, I want a signup with an already-registered email rejected with a conflict, so that email addresses remain unique. (§3, §9)

#### Acceptance Criteria

1. IF the Normalized_Email matches the stored email of an existing User_Entity compared case-insensitively, THEN THE Signup_Service SHALL raise a Conflict_Error indicating that the email address is already registered, before attempting to persist a new User_Entity.
2. WHEN the Signup_Service raises a Conflict_Error for a duplicate email, THE Signup_Endpoint SHALL respond with HTTP status 409.
3. WHEN the Signup_Service raises a Conflict_Error for a duplicate email, THE Signup_Service SHALL persist no new User_Entity.

### Requirement 6: Duplicate email race handling

**User Story:** As a system owner, I want concurrent signups for the same email to still produce a clean conflict, so that a race between the duplicate pre-check and the insert never surfaces a server error. (§3, §9)

#### Acceptance Criteria

1. IF the Email_Uniqueness_Constraint rejects the insert of a new User_Entity because a concurrent signup persisted the same Normalized_Email after the pre-check, THEN THE Signup_Service SHALL translate that database constraint violation into a Conflict_Error indicating that the email address is already registered.
2. WHEN the Signup_Service translates the Email_Uniqueness_Constraint violation into a Conflict_Error, THE Signup_Endpoint SHALL respond with HTTP status 409 rather than a 5xx server error status.
3. WHEN the Email_Uniqueness_Constraint rejects an insert, THE Signup_Endpoint SHALL exclude database driver text, SQL statements, database constraint and index names, and internal type names from the response body.
4. IF the Email_Uniqueness_Constraint rejects the insert of a new User_Entity during a concurrent signup race, THEN THE Signup_Service SHALL persist no new User_Entity for that rejected request.

### Requirement 7: Argon2id password encoder

**User Story:** As a security owner, I want passwords hashed with Argon2id, so that stored credentials are protected by an established password-hashing algorithm. (§3, §11)

#### Acceptance Criteria

1. THE Signup_Feature SHALL define a Password_Encoder bean that uses the Argon2id algorithm within the `com.bovae.yaj.config` package.
2. WHEN the Signup_Service persists a new User_Entity, THE Signup_Service SHALL set the `password_hash` value to a non-empty Password_Hash produced by the Password_Encoder from the Submitted_Password.
3. THE Signup_Service SHALL store a `password_hash` value that differs from the Submitted_Password plaintext.
4. WHEN the Password_Encoder verifies the Submitted_Password against the stored Password_Hash, THE Password_Encoder SHALL report a match.
5. IF the Password_Hash produced for a signup equals the Submitted_Password plaintext, THEN THE Signup_Service SHALL fail the signup, persist no new User_Entity, and return no successful Signup_Response.
6. WHEN the Password_Encoder encodes the same Submitted_Password value more than once, THE Password_Encoder SHALL produce a different Password_Hash for each encoding.
7. IF a password value other than the Submitted_Password that produced the stored Password_Hash is verified against that Password_Hash, THEN THE Password_Encoder SHALL report no match.

### Requirement 8: Plaintext and hash confidentiality

**User Story:** As a security owner, I want the password plaintext and the password hash kept out of logs and responses, so that credentials are never exposed. (§3, §11)

#### Acceptance Criteria

1. THE Signup_Feature SHALL exclude the Submitted_Password value from every log statement it emits at any log level, including any exception message or stack trace those statements carry.
2. THE Signup_Feature SHALL exclude the Password_Hash value from every log statement it emits at any log level, including any exception message or stack trace those statements carry.
3. THE Signup_Response SHALL exclude the Submitted_Password value from every member of its response body.
4. THE Signup_Response SHALL exclude the Password_Hash value from every member of its response body.
5. WHEN the Signup_Endpoint returns an error response for a failed signup, THE Signup_Endpoint SHALL exclude the Submitted_Password value and the Password_Hash value from every member of that response body.

### Requirement 9: New account persistence defaults

**User Story:** As a system owner, I want a newly created account persisted as unverified with server-set identifiers and timestamps, so that the account state is correct and consistent. (§3, §6, §9)

#### Acceptance Criteria

1. WHEN the Signup_Service persists a new User_Entity, THE Signup_Service SHALL set the `email_verified` value to `false`.
2. WHEN the Signup_Service persists a new User_Entity, THE Signup_Service SHALL assign a non-null `java.util.UUID` identifier obtained from the server-side database default rather than from a client-supplied value.
3. THE Signup_Service SHALL obtain the `created_at` and `modified_at` values for the new User_Entity from the server-side database defaults in UTC rather than from a client-supplied value.
4. WHEN the new User_Entity insert completes, THE Signup_Service SHALL expose a non-null `java.util.UUID` identifier, a non-null UTC `created_at` value, and a non-null UTC `modified_at` value for that account.
5. IF the Signup_Request carries any client-supplied identifier or timestamp value, THEN THE Signup_Service SHALL ignore that value and apply the server-side database defaults instead.

### Requirement 10: Success response contract

**User Story:** As an unregistered visitor, I want a clear success response that confirms my account without leaking my credentials, so that the client can advance to the verification step. (§3, §9)

#### Acceptance Criteria

1. WHEN the Signup_Service successfully persists a new User_Entity, THE Signup_Endpoint SHALL respond with HTTP status 201 and a non-empty Signup_Response body.
2. THE Signup_Response SHALL represent the created account with its `UUID` identifier, the stored Normalized_Email value, and its `email_verified` value of `false`.
3. THE Signup_Endpoint SHALL return the Signup_Response as an `application/json` body.
4. WHERE the Signup_Response includes a timestamp, THE Signup_Endpoint SHALL serialize that timestamp as an ISO-8601 representation denoting the instant in UTC.

### Requirement 11: Error mapping through RFC 9457

**User Story:** As an API consumer, I want signup errors returned as RFC 9457 problem responses with the correct status codes, so that failures stay consistent and machine-readable. (§9, §11)

#### Acceptance Criteria

1. WHEN a Validation_Error raised during signup reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 400 and a body `status` member equal to 400.
2. WHEN a Conflict_Error raised during signup reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 409 and a body `status` member equal to 409.
3. WHEN the Problem_Handler maps a signup error, THE Problem_Handler SHALL build the response through the Problem_Detail_Factory so that the response body carries a non-empty `correlationId` member and a `timestamp` member serialized as an ISO-8601 representation in UTC.
4. WHEN the Problem_Handler maps a signup error, THE Problem_Handler SHALL exclude stack traces, internal type names, SQL statements, and the Submitted_Password from every member of the response body.

### Requirement 12: Security seam boundary

**User Story:** As a developer, I want the signup endpoint reachable without authentication under the current security seam, so that unregistered visitors can sign up before the real authentication enforcement lands in E4. (§3)

#### Acceptance Criteria

1. THE Signup_Endpoint SHALL be reachable under the existing permit-all Security_Policy by a request that supplies no authentication credentials, without such a request being rejected for missing authentication.
2. THE Signup_Feature SHALL introduce no JWT issuance, no authentication filter, and no authorization enforcement, and SHALL leave the existing permit-all Security_Policy unchanged, deferring those concerns to epic E4.
3. WHEN the Signup_Endpoint returns any response, THE Signup_Endpoint SHALL exclude any JWT, authentication token, session token, and authentication cookie from that response.
4. WHERE epic E4 later replaces the existing permit-all Security_Policy with one that requires authentication for application endpoints, THE Signup_Endpoint SHALL remain reachable without authentication so that unregistered visitors can sign up.

### Requirement 13: Email verification deferred to E2

**User Story:** As a product owner, I want signup to stop at creating an unverified account, so that the verification token and SMTP delivery remain the responsibility of the email-verification epic. (§3)

#### Acceptance Criteria

1. WHEN the Signup_Feature creates a new account, THE Signup_Feature SHALL leave that account with `email_verified` equal to `false`.
2. WHEN the Signup_Feature creates a new account, THE Signup_Feature SHALL persist no Email_Verification_Flow verification token for that account.
3. WHEN the Signup_Feature creates a new account, THE Signup_Feature SHALL send no Email_Verification_Flow verification email to that account during the signup operation.

### Requirement 14: Dependency and secret hygiene

**User Story:** As a maintainer, I want the Argon2id dependency added cleanly and no secrets committed, so that the build supports password hashing without exposing credentials. (§11)

#### Acceptance Criteria

1. THE Project_Build SHALL declare in `be/pom.xml` the BouncyCastle dependency that the Argon2id Password_Encoder requires on the runtime classpath.
2. WHEN the Project_Build runs, THE Project_Build SHALL resolve the declared BouncyCastle dependency onto the runtime classpath without a missing-dependency failure, so that the Argon2id Password_Encoder can be instantiated.
3. THE Signup_Feature SHALL keep all source-controlled files free of plaintext production secret values, including real account passwords, database and service credentials, API keys, access tokens, and private keys, excluding non-production placeholder and test-fixture values.
4. WHERE the Signup_Feature requires a secret value at runtime, THE Signup_Feature SHALL obtain that value from externalized configuration rather than from a value hardcoded in a source-controlled file.

### Requirement 15: Verification and quality gates

**User Story:** As a maintainer, I want the signup feature verified against the project's quality gates, so that the behavior is proven before the verification and login epics build on it. (§11; E1 Definition of Done)

#### Acceptance Criteria

1. THE Project_Build SHALL include a Signup_Service unit test that verifies the Password_Encoder is invoked exactly once with the Submitted_Password value to produce the Password_Hash during a successful signup.
2. THE Project_Build SHALL include a Signup_Service unit test that asserts a duplicate email produces a Conflict_Error mapped to HTTP status 409.
3. THE Project_Build SHALL include a Signup_Service unit test that asserts a Submitted_Password shorter than the Minimum_Password_Length produces a Validation_Error mapped to HTTP status 400.
4. THE Project_Build SHALL include a Signup_Service unit test that asserts a malformed Submitted_Email produces a Validation_Error mapped to HTTP status 400.
5. THE Project_Build SHALL include a unit test that asserts neither the Submitted_Password nor the Password_Hash appears in the Signup_Response or in the log statements emitted during the signup operation.
6. THE Project_Build SHALL include a Cucumber BDD scenario, executed under the Integration_Test_Context, that submits a valid Signup_Request to the Signup_Endpoint, asserts the response is HTTP status 201, and asserts the corresponding `users` row exists with `email_verified` equal to `false`.
7. THE Project_Build SHALL satisfy the Coverage_Gate thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages.
8. WHEN the Spotless and the Error Prone with NullAway checks run, THE Project_Build SHALL complete without formatting or static-analysis violations.
9. IF line coverage or branch coverage over the non-excluded packages is below 90%, THEN THE Project_Build SHALL fail.
10. IF Spotless detects a formatting violation or Error Prone with NullAway detects a static-analysis violation, THEN THE Project_Build SHALL fail.
