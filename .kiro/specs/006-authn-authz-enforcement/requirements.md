# Requirements Document

## Introduction

This document specifies the requirements for **Epic E4 — AuthN/AuthZ enforcement + current-user**
(`006-authn-authz-enforcement`), the fourth authentication epic for the yet-another-jira Kanban
ticket tracker. E4 builds directly on the domain foundation (`002-backend-domain-foundation`),
sign-up (`003-signup-password-hashing`, "E1"), email verification
(`004-email-verification-resend`, "E2"), and login / logout (`005-login-logout-jwt-denylist`, "E3").

After E3 a person can sign up, verify their email, log in to obtain a signed JWT bearer Access_Token,
log out (revoking the token in a Valkey denylist), and read their identity at `GET /api/v1/auth/me`.
But authentication is still **not enforced**: `com.bovae.yaj.config.SecurityConfig` is the
**permit-all seam** (`auth.anyRequest().permitAll()`) that logs a `SKELETON ONLY` warning on every
startup, no Spring Security filter populates the `SecurityContext`, and the
`com.bovae.yaj.security.CurrentUserProvider` interface has **no implementation**. E3 worked around
this by reading and validating the `Authorization: Bearer` header **directly inside** the `logout`
and `me` handlers. E4 closes that gap and makes authentication mandatory.

E4 delivers four behaviors:

1. **A JWT authentication filter.** A Spring Security filter reads the `Authorization: Bearer`
   header, validates the token (signature, expiry, and Token_Denylist membership) by **reusing**
   `com.bovae.yaj.auth.jwt.JwtService.validateAccessToken` and
   `com.bovae.yaj.auth.jwt.BearerTokenExtractor`, and on success populates the `SecurityContext` with
   an authentication whose principal is the token's `sub` (the user id). It introduces no new token
   parsing or signature logic.
2. **A real authorization policy.** `SecurityConfig` is rewritten from permit-all to **default-deny**:
   the four public auth endpoints (`POST /api/v1/auth/signup`, `POST /api/v1/auth/login`,
   `GET|POST /api/v1/auth/verify`, `POST /api/v1/auth/verification/resend`) and the actuator health /
   readiness / liveness probes stay public; every other request — all remaining `/api/v1/**` business
   endpoints — requires authentication. The `SKELETON ONLY` warning and the permit-all rule are
   removed.
3. **A current-user accessor.** The `CurrentUserProvider` interface gains an implementation that
   resolves the authenticated user id from the `SecurityContext`, so later epics can stamp
   `tickets.created_by` (E7) and `comments.author` (E8) from the caller, and so `GET /api/v1/auth/me`
   resolves the caller through the enforced context rather than re-parsing the header.
4. **Uniform RFC 9457 rejection.** An unauthenticated or invalid-token request to a protected
   endpoint returns a uniform RFC 9457 `401`, produced through the existing
   `com.bovae.yaj.web.error.ProblemDetailFactory` machinery with a `correlationId` and a UTC
   `timestamp`, and never a `403` and never a login redirect.

E4 reuses, rather than re-implements, the foundation already shipped: the `JwtService` validation
contract (`validateAccessToken` returns a `TokenClaims` carrying `subject`/`jti`/`issuedAt`/
`expiresAt` and throws `UnauthorizedException` for any malformed, expired, or denylisted token), the
`BearerTokenExtractor` (`bearer ` scheme parsing, 401 on absent/garbled headers), the `TokenDenylist`
(Valkey `auth:jwt:denylist:<jti>`), the `UnauthorizedException` → `401` mapping in
`GlobalExceptionHandler`, the `ProblemDetailFactory` enrichment (`correlationId`, ISO-8601 UTC
`timestamp`), the per-request `CorrelationIdFilter` / `MdcCleanupFilter` that make the correlation id
available before the security chain runs, the `CorsConfigurationSource` already wired into
`HttpSecurity`, and the stateless / CSRF-disabled posture E3 left in place.

E4 is the boundary E2 and E3 deferred to. With E4 landed, the `CurrentUserProvider` is implemented
and the permit-all seam is gone, which **supersedes** the "security seam boundary" requirements of
E2 (Requirement 14) and E3 (Requirement 13). E4 does **not** introduce roles, team membership, or
per-resource authorization — every authenticated (verified) user may reach every business endpoint,
consistent with the product's "no fine-grained roles" scope (§12); E4 enforces only the
authenticated-versus-anonymous boundary. The business endpoints themselves (teams, epics, tickets,
comments, board) are delivered by E5–E10; E4 only enforces the gate in front of them and removes the
`GET /api/v1/mock/board` mock from the public surface. The **frontend** auth context and route guard
belong to **Epic E11**; E4 delivers only the backend enforcement contract.

These requirements are derived from the E4 entry in `requirements/epics-catalog.md` and the product
requirements source `requirements/yet-another-jira.md` (primarily §3 — all business endpoints and
screens require authentication except sign-up / login / verify / resend; §6 — `created_by` set from
the authenticated user; §9 — meaningful auth-failure status codes and tokens never in URLs; and §11
— protect authenticated endpoints).

## Decisions and Open Questions

The constraints below were under-specified by the source material. Each is captured as a concrete,
testable decision in the requirements that follow. Items marked **[CONFIRM]** change observable
behavior and should be confirmed before the design phase; the requirements encode the recommended
default so review can proceed. Unmarked items record a confirmed or non-behavioral choice.

1. **Default-deny posture and actuator exposure (Requirement 3, 4). [CONFIRM]** `SecurityConfig`
   becomes **default-deny** (`anyRequest().authenticated()`) rather than enumerating only the
   protected routes. Public matchers: the four auth endpoints (`/api/v1/auth/signup`,
   `/api/v1/auth/login`, `/api/v1/auth/verify`, `/api/v1/auth/verification/resend`) and the health
   probe paths `/actuator/health/**` (covering `/actuator/health`, `/actuator/health/readiness`, and
   `/actuator/health/liveness`, all enabled in `application.yml`). **`/actuator/info` requires
   authentication** under the default-deny rule, because `management.info.env.enabled` is `true` and a
   public info endpoint could expose environment details. There are **no static-asset matchers**:
   `spring.web.resources.add-mappings` is `false`, so the backend serves no static assets (the SPA is
   served by the separate frontend tier), and the catalog's "static assets public" note has no backend
   surface to apply to. Rationale: default-deny is the secure default — a future endpoint added without
   a matcher is protected, not accidentally public. *Alternative:* also permit `/actuator/info`, or
   enumerate protected routes with an `anyRequest().permitAll()` fallback (rejected — fails open).

2. **Invalid or denylisted token presented to a public endpoint (Requirement 1, 5, 7). [CONFIRM]** A
   malformed, expired, or denylisted token sent to a **public** endpoint is **ignored**: the
   Jwt_Authentication_Filter leaves the request unauthenticated and the request proceeds and succeeds
   as an Anonymous_Request. Only a **protected** endpoint reached without a valid authentication yields
   `401`. Rationale: a client retrying `POST /api/v1/auth/login` while still holding a stale or revoked
   token must not be blocked from logging in again; public endpoints must stay reachable regardless of
   any leftover credential. *Alternative:* reject any malformed bearer header everywhere (rejected — it
   would 401 a login or resend attempt that carried an expired token).

3. **Logout idempotency under enforcement (Requirement 10). [CONFIRM]** E3 Requirement 8.5 makes
   `POST /api/v1/auth/logout` **idempotent**: logging out an already-denylisted token still returns
   `204`. The E4 authentication filter rejects denylisted tokens on protected routes, so placing
   `logout` behind `authenticated()` would make a repeat logout return `401` and regress that
   contract. **Decision:** `POST /api/v1/auth/logout` is kept reachable **without** filter-level
   denylist rejection (it remains in the permitted set and continues to self-validate via
   `JwtService.parseForRevocation`, which does not consult the denylist), preserving idempotent logout.
   A logout request with a missing, malformed, signature-invalid, or expired token still returns `401`
   exactly as in E3. *Alternative:* place `logout` behind full authentication and accept that logging
   out an already-revoked token now returns `401` — simpler matcher list, but a regression of E3
   Requirement 8.5.

4. **Stateless filter; soft-deleted / unknown subject resolved downstream (Requirement 1, 9).
   [CONFIRM]** The Jwt_Authentication_Filter trusts the **validated** token's `sub` and sets the
   Authentication_Principal to that user id **without loading the User_Entity**, so an authenticated
   request performs **no per-request user lookup** in the filter. A still-valid, non-denylisted token
   whose `sub` resolves to no account or to a Soft_Deleted_User therefore **passes the filter** but is
   rejected with `401` by the handler that loads the user (the Current_User_Endpoint), preserving E3
   Requirement 10.6. Rationale: keeping the filter free of database access avoids a lookup on every
   authenticated request (a performance and coupling concern) and leaves liveness/soft-delete checks
   where the entity is actually read. *Alternative:* load and validate the account inside the filter
   (centralized rejection, at the cost of a database query on every authenticated request).

5. **Uniform `401` for both missing and invalid credentials; never `403`, never a redirect
   (Requirement 6). [CONFIRM]** Every unauthenticated access to a protected endpoint — token missing,
   malformed, expired, signature-invalid, or denylisted — produces a single uniform RFC 9457 `401`
   through a custom Authentication_Entry_Point, never Spring Security's default `403` access-denied
   page and never a redirect to a login form. Rationale: §9 requires meaningful auth-failure status
   codes and the API is a token API with no server-rendered login page; a uniform `401` also avoids
   distinguishing "no token" from "bad token". *Alternative:* return `403` for an authenticated
   principal lacking authority — not applicable in E4, which has no roles, so authorization failure and
   authentication failure collapse to the same `401`.

6. **CurrentUser principal shape (Requirement 8).** The filter stores the user id (`sub` as a `UUID`)
   as the Authentication_Principal; `CurrentUserProvider.requireCurrentUserId()` returns that id and
   throws `UnauthorizedException` when the `SecurityContext` holds no authentication or only an
   anonymous authentication. No Spring Security `UserDetails` and no `User_Entity` is materialized for
   authentication. This is internal shape, not externally observable, so it is not marked
   **[CONFIRM]**.

7. **CORS preflight stays open (Requirement 11).** `OPTIONS` CORS preflight requests are not subject
   to authentication, so the browser preflight the SPA issues continues to succeed against the
   already-wired `CorsConfigurationSource`. Standard Spring Security behavior when CORS is configured;
   stated explicitly so it is verified rather than assumed.

8. **Mock board endpoint becomes authenticated (Requirement 5).** `GET /api/v1/mock/board`
   (`MockBoardController`) falls under the default-deny rule and now requires authentication. The mock
   is removed entirely in E9; until then it is simply no longer publicly reachable. No separate
   decision is needed — it is a consequence of decision 1.

## Glossary

- **AuthN_Enforcement_Feature**: The complete E4 deliverable taken as a whole: the JWT authentication filter, the rewritten authorization policy, the RFC 9457 authentication entry point, and the current-user accessor implementation.
- **Security_Policy**: The `com.bovae.yaj.config.SecurityConfig` Spring Security configuration. Before E4 it is the permit-all seam (`anyRequest().permitAll()` plus a `SKELETON ONLY` startup warning); E4 replaces it with the enforcing policy described below.
- **Jwt_Authentication_Filter**: The new Spring Security filter introduced by E4 that reads the Bearer_Header, validates the Access_Token through the Jwt_Service, and populates the Security_Context on success. Wired into the Spring Security filter chain ahead of the authorization decision.
- **Authentication_Entry_Point**: The new component that writes the uniform RFC 9457 `401` problem response whenever an unauthenticated request reaches a Protected_Endpoint, overriding Spring Security's default access-denied and redirect behavior.
- **Bearer_Header**: The HTTP `Authorization` request header carrying the Access_Token in the `Bearer <token>` scheme.
- **Bearer_Token_Extractor**: The existing `com.bovae.yaj.auth.jwt.BearerTokenExtractor`, reused by the Jwt_Authentication_Filter to pull the raw token from the Bearer_Header and to raise an Unauthorized_Error on an absent or malformed header.
- **Jwt_Service**: The existing `com.bovae.yaj.auth.jwt.JwtService`, reused by the Jwt_Authentication_Filter through its `validateAccessToken` operation, which verifies the Token_Signature, enforces the Token_Expiry_Claim, rejects a Token_Id_Claim present in the Token_Denylist, and returns a Token_Claims, raising an Unauthorized_Error on any failure.
- **Token_Claims**: The existing `com.bovae.yaj.auth.jwt.TokenClaims` record returned by the Jwt_Service, carrying the Token_Subject_Claim, the Token_Id_Claim, the issued-at instant, and the Token_Expiry_Claim.
- **Access_Token**: The signed JWT bearer credential issued at login (E3) and presented on the Bearer_Header for authenticated requests.
- **Token_Subject_Claim**: The JWT `sub` claim, equal to the issuing User_Entity id (a `UUID`).
- **Token_Id_Claim**: The JWT `jti` claim, used as the Token_Denylist key.
- **Token_Expiry_Claim**: The JWT `exp` claim, the instant after which the Access_Token is no longer valid.
- **Token_Signature**: The cryptographic signature over the Access_Token, verified by the Jwt_Service using the configured signing secret.
- **Token_Denylist**: The existing `com.bovae.yaj.auth.jwt.TokenDenylist`, the Valkey-backed set of revoked Token_Id_Claim values consulted by `JwtService.validateAccessToken`.
- **Security_Context**: The Spring Security `SecurityContext` held in the `SecurityContextHolder` for the duration of a request, into which the Jwt_Authentication_Filter places the authenticated principal.
- **Authentication_Principal**: The principal stored in the Security_Context authentication by the Jwt_Authentication_Filter; in E4 it is the authenticated user id (the Token_Subject_Claim as a `UUID`).
- **Current_User_Provider**: The existing interface `com.bovae.yaj.security.CurrentUserProvider` with the single operation `requireCurrentUserId()`, which E4 implements for the first time.
- **Current_User_Provider_Impl**: The new E4 implementation of the Current_User_Provider that resolves the Authentication_Principal from the Security_Context.
- **Protected_Endpoint**: Any HTTP endpoint that requires authentication under the rewritten Security_Policy — every request that is not a Public_Endpoint, including all business `/api/v1/**` routes, the Current_User_Endpoint, `/api/v1/mock/board`, and `/actuator/info`.
- **Public_Endpoint**: An HTTP endpoint reachable without authentication under the rewritten Security_Policy: the Auth_Public_Endpoints, the Logout_Endpoint, and the Health_Probe_Endpoints.
- **Auth_Public_Endpoints**: The four authentication endpoints that must stay public: `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `GET /api/v1/auth/verify`, `POST /api/v1/auth/verify`, and `POST /api/v1/auth/verification/resend`.
- **Health_Probe_Endpoints**: The actuator health and probe paths `/actuator/health`, `/actuator/health/readiness`, and `/actuator/health/liveness`, matched as `/actuator/health/**`.
- **Login_Endpoint**: The existing `POST /api/v1/auth/login` handler (E3), a member of the Auth_Public_Endpoints.
- **Logout_Endpoint**: The existing `POST /api/v1/auth/logout` handler (E3), kept reachable without filter-level denylist rejection so logout stays idempotent (decision 3).
- **Current_User_Endpoint**: The existing `GET /api/v1/auth/me` handler (E3), a Protected_Endpoint under E4 that returns the authenticated account's id, email, and verification flag.
- **Anonymous_Request**: A request that carries no valid authentication after the Jwt_Authentication_Filter runs — no Bearer_Header, or a Bearer_Header whose token failed validation — represented by Spring Security's anonymous authentication.
- **Cors_Preflight_Request**: A browser CORS preflight `OPTIONS` request, handled against the existing `CorsConfigurationSource` and not subject to authentication.
- **Stateless_Session_Policy**: The `SessionCreationPolicy.STATELESS` configuration retained from the existing Security_Policy, under which the server creates no HTTP session for authentication.
- **User_Entity**: The existing JPA entity `com.bovae.yaj.domain.model.User`, including the `email`, `email_verified`, and `deleted_at` columns.
- **User_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.UserRepository`, including `findById`.
- **Soft_Deleted_User**: A User_Entity whose `deleted_at` value is non-null.
- **Unauthorized_Error**: A `com.bovae.yaj.error.UnauthorizedException`, mapped by the Problem_Handler to HTTP status `401`.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalExceptionHandler` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`) that maps `UnauthorizedException` to an RFC 9457 `401`.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and a UTC `timestamp`.
- **Correlation_Id**: The per-request correlation id established by the existing `com.bovae.yaj.web.filter.CorrelationIdFilter` into the MDC before the security chain runs, and read by the Problem_Detail_Factory.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/*Application.*`, `**/config/**`, `**/mapper/*Impl*`, and `**/model/**`.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance and a Valkey instance provisioned by Testcontainers under the `bdd` profile.

## Requirements

### Requirement 1: JWT authentication filter

**User Story:** As a security owner, I want a Spring Security filter that authenticates each request from its bearer token, so that the authenticated identity is established before any business handler runs. (§3, §11)

#### Acceptance Criteria

1. THE AuthN_Enforcement_Feature SHALL introduce a Jwt_Authentication_Filter into the Spring Security filter chain that runs before the authorization decision for every request.
2. WHEN a request carries a Bearer_Header whose Access_Token passes Jwt_Service validation, THE Jwt_Authentication_Filter SHALL populate the Security_Context with an authentication whose Authentication_Principal is the Token_Subject_Claim resolved to a `UUID`.
3. THE Jwt_Authentication_Filter SHALL obtain the raw token from the Bearer_Header through the existing Bearer_Token_Extractor and SHALL validate the token through the existing `JwtService.validateAccessToken` operation, introducing no separate token-parsing, signature-verification, expiry, or denylist logic.
4. WHEN the Jwt_Authentication_Filter populates the Security_Context, THE Jwt_Authentication_Filter SHALL set the Authentication_Principal to the validated Token_Subject_Claim without loading the User_Entity from the User_Repository.
5. WHEN a request carries no Bearer_Header, or carries a Bearer_Header whose Access_Token fails Jwt_Service validation, THE Jwt_Authentication_Filter SHALL leave the Security_Context without an authenticated principal and SHALL allow the request to continue to the authorization decision.
6. WHEN the Jwt_Authentication_Filter has processed a request, THE Jwt_Authentication_Filter SHALL invoke the remainder of the filter chain exactly once for that request.

### Requirement 2: Reuse of the established token-validation contract

**User Story:** As a maintainer, I want the filter to reuse the existing JWT validation rather than duplicate it, so that login, logout, and enforcement agree on what a valid token is. (§9, §11)

#### Acceptance Criteria

1. WHEN the Jwt_Authentication_Filter validates an Access_Token, THE Jwt_Service SHALL reject the token unless the Token_Signature verifies against the configured signing secret.
2. WHILE the current server time is on or after a presented Access_Token Token_Expiry_Claim, THE Jwt_Service SHALL treat that token as expired and the Jwt_Authentication_Filter SHALL leave the request unauthenticated.
3. WHEN the Jwt_Authentication_Filter validates an Access_Token whose Token_Id_Claim is present in the Token_Denylist, THE Jwt_Service SHALL reject the token and the Jwt_Authentication_Filter SHALL leave the request unauthenticated.
4. IF a presented Access_Token is structurally malformed or is missing the Token_Subject_Claim, the Token_Id_Claim, or the Token_Expiry_Claim, THEN the Jwt_Service SHALL reject the token and the Jwt_Authentication_Filter SHALL leave the request unauthenticated.
5. THE AuthN_Enforcement_Feature SHALL add no new JSON Web Token dependency to `be/pom.xml`, reusing the JWT library E3 already declared.

### Requirement 3: Enforcing authorization policy replaces the permit-all seam

**User Story:** As a security owner, I want every endpoint authenticated by default with only the auth and health endpoints public, so that no business endpoint is reachable without a valid token. (§3, §9, §11)

#### Acceptance Criteria

1. THE Security_Policy SHALL require authentication for every request that is not a Public_Endpoint, applying a default-deny rule so that any route without an explicit public matcher requires authentication.
2. THE Security_Policy SHALL permit the Auth_Public_Endpoints without authentication.
3. THE Security_Policy SHALL permit the Health_Probe_Endpoints matched as `/actuator/health/**` without authentication.
4. THE Security_Policy SHALL remove the permit-all rule that authorized every request without authentication and SHALL remove the `SKELETON ONLY` permit-all startup warning emitted by the prior Security_Policy.
5. THE Security_Policy SHALL retain the Stateless_Session_Policy and the disabled CSRF protection and the existing CORS configuration source from the prior Security_Policy.

### Requirement 4: Public endpoints remain reachable without authentication

**User Story:** As an unauthenticated visitor, I want to sign up, log in, verify my email, resend verification, and let health probes run without a token, so that I can reach the application and operators can monitor it. (§3, §9)

#### Acceptance Criteria

1. WHEN a request that supplies no authentication is sent to any Auth_Public_Endpoints route, THE Security_Policy SHALL allow the request to reach its handler without rejecting it for missing authentication.
2. WHEN a request that supplies no authentication is sent to a Health_Probe_Endpoints route, THE Security_Policy SHALL allow the request to reach the actuator health endpoint without rejecting it for missing authentication.
3. WHERE a request to an Auth_Public_Endpoints route carries a Bearer_Header whose Access_Token is expired, malformed, or present in the Token_Denylist, THE Security_Policy SHALL still allow the request to reach its handler as an Anonymous_Request.
4. THE Security_Policy SHALL require authentication for `/actuator/info`, treating it as a Protected_Endpoint rather than a Public_Endpoint.

### Requirement 5: Protected endpoints require authentication

**User Story:** As a security owner, I want every business endpoint to demand a valid token, so that unauthenticated callers cannot read or modify application data. (§3, §6, §9, §11)

#### Acceptance Criteria

1. IF a request to a Protected_Endpoint supplies no Bearer_Header, THEN THE Security_Policy SHALL reject the request with HTTP status `401` and SHALL NOT invoke the endpoint handler.
2. IF a request to a Protected_Endpoint supplies a Bearer_Header whose Access_Token is expired, malformed, signature-invalid, or present in the Token_Denylist, THEN THE Security_Policy SHALL reject the request with HTTP status `401` and SHALL NOT invoke the endpoint handler.
3. WHEN a request to a Protected_Endpoint supplies a Bearer_Header whose Access_Token passes Jwt_Service validation, THE Security_Policy SHALL allow the request to reach the endpoint handler with the authenticated identity available in the Security_Context.
4. THE Security_Policy SHALL treat every `/api/v1/**` route other than the Auth_Public_Endpoints and the Logout_Endpoint as a Protected_Endpoint, including `/api/v1/mock/board`.

### Requirement 6: Uniform RFC 9457 401 for rejected requests

**User Story:** As an API consumer, I want an unauthenticated request to return a consistent RFC 9457 401, so that I can detect and handle authentication failures the same way as every other API error. (§9, §11)

#### Acceptance Criteria

1. WHEN the Security_Policy rejects a request to a Protected_Endpoint for missing or invalid authentication, THE Authentication_Entry_Point SHALL produce an RFC 9457 problem response with HTTP status `401` and a body `status` member equal to `401`.
2. WHEN the Authentication_Entry_Point produces a `401`, THE Authentication_Entry_Point SHALL build the response so that the body carries a non-empty `correlationId` member and a `timestamp` member serialized as an ISO-8601 representation in UTC, consistent with the Problem_Detail_Factory output for handler-raised errors.
3. WHEN the Security_Policy rejects a request for missing or invalid authentication, THE Authentication_Entry_Point SHALL return HTTP status `401` and SHALL NOT return HTTP status `403` and SHALL NOT return an HTTP redirect to a login location.
4. WHEN the Authentication_Entry_Point produces a `401`, THE Authentication_Entry_Point SHALL exclude stack traces, internal type names, SQL statements, the signing secret, and any Access_Token from every member of the response body.
5. WHEN a request to a Protected_Endpoint is rejected for missing authentication and when a request to a Protected_Endpoint is rejected for an invalid, expired, or denylisted Access_Token, THE Authentication_Entry_Point SHALL return the same uniform `401` response for both cases.

### Requirement 7: Correlation id preserved on filter-originated rejections

**User Story:** As an operator, I want a 401 produced by the security layer to carry the same correlation id as the request logs, so that I can trace a rejected request end to end. (§11)

#### Acceptance Criteria

1. WHEN the Authentication_Entry_Point produces a `401`, THE Authentication_Entry_Point SHALL read the Correlation_Id established by the CorrelationIdFilter for the current request and SHALL place it in the response body `correlationId` member.
2. THE AuthN_Enforcement_Feature SHALL order the Jwt_Authentication_Filter and the Authentication_Entry_Point after the CorrelationIdFilter so that the Correlation_Id is present in the MDC when a `401` is produced.
3. WHEN a request to a Protected_Endpoint is rejected with a `401`, THE AuthN_Enforcement_Feature SHALL echo the Correlation_Id on the response through the existing CorrelationIdFilter response-header behavior.

### Requirement 8: Current-user accessor implementation

**User Story:** As a developer, I want a current-user accessor that returns the authenticated user id, so that later epics can set `created_by` and comment `author` from the caller. (§6, §9)

#### Acceptance Criteria

1. THE AuthN_Enforcement_Feature SHALL provide a Current_User_Provider_Impl that implements the existing Current_User_Provider interface.
2. WHEN the Security_Context holds an authentication populated by the Jwt_Authentication_Filter, THE Current_User_Provider_Impl `requireCurrentUserId` operation SHALL return the Authentication_Principal as the authenticated user id `UUID`.
3. IF the Security_Context holds no authentication or holds only an anonymous authentication, THEN THE Current_User_Provider_Impl `requireCurrentUserId` operation SHALL raise an Unauthorized_Error.
4. WHEN the Jwt_Authentication_Filter authenticates a request whose Access_Token Token_Subject_Claim equals a given user id, THE Current_User_Provider_Impl SHALL return that same user id for that request (filter-to-accessor round trip).

### Requirement 9: Current-user endpoint under enforcement

**User Story:** As a logged-in user, I want the current-user endpoint to return my identity through the enforced security context, so that the client can show who is signed in. (§3, §9)

#### Acceptance Criteria

1. THE Current_User_Endpoint at `GET /api/v1/auth/me` SHALL be a Protected_Endpoint that requires a valid Access_Token.
2. WHEN a request to the Current_User_Endpoint supplies an Access_Token that passes Jwt_Service validation and whose Token_Subject_Claim resolves to a registered User_Entity whose `deleted_at` is null, THE Current_User_Endpoint SHALL respond with HTTP status `200` and a body carrying that User_Entity id, email, and `email_verified` value.
3. WHEN the Current_User_Endpoint returns any response, THE Current_User_Endpoint SHALL exclude the User_Entity `password_hash` and SHALL exclude any Access_Token from that response.
4. IF a request to the Current_User_Endpoint supplies no Access_Token, or supplies an Access_Token that is malformed, signature-invalid, expired, or present in the Token_Denylist, THEN THE Security_Policy SHALL reject the request with HTTP status `401` before the Current_User_Endpoint handler runs.
5. IF a request to the Current_User_Endpoint supplies a valid Access_Token whose Token_Subject_Claim resolves to no registered User_Entity or resolves to a Soft_Deleted_User, THEN THE Current_User_Endpoint SHALL raise an Unauthorized_Error and respond with HTTP status `401`.

### Requirement 10: Logout idempotency preserved under enforcement

**User Story:** As a logged-in user, I want logout to keep working idempotently after enforcement lands, so that signing out twice does not surface a confusing error. (§3, §9)

#### Acceptance Criteria

1. THE Logout_Endpoint at `POST /api/v1/auth/logout` SHALL remain reachable without the Jwt_Authentication_Filter rejecting a request solely because the presented Access_Token Token_Id_Claim is present in the Token_Denylist.
2. WHEN a request to the Logout_Endpoint supplies an Access_Token whose Token_Signature is valid and whose Token_Expiry_Claim is in the future, THE Logout_Endpoint SHALL record the token Token_Id_Claim in the Token_Denylist and respond with HTTP status `204`.
3. WHEN a request to the Logout_Endpoint supplies a valid Access_Token whose Token_Id_Claim is already present in the Token_Denylist, THE Logout_Endpoint SHALL respond with HTTP status `204`, so that repeated logout of the same token remains idempotent.
4. IF a request to the Logout_Endpoint supplies no Access_Token, or supplies an Access_Token that is malformed, signature-invalid, or whose Token_Expiry_Claim is on or before the current server time, THEN THE Logout_Endpoint SHALL respond with HTTP status `401`.

### Requirement 11: Statelessness and CORS preflight

**User Story:** As a frontend developer, I want enforcement to stay stateless and to leave CORS preflight open, so that the SPA can call the API across origins without sessions or blocked preflights. (§9, §11)

#### Acceptance Criteria

1. THE AuthN_Enforcement_Feature SHALL create no HTTP session for authentication, retaining the Stateless_Session_Policy.
2. WHEN a Cors_Preflight_Request is received for any route, THE Security_Policy SHALL allow that preflight request without requiring authentication.
3. THE Security_Policy SHALL apply the existing CORS configuration source so that cross-origin responses carry the configured CORS headers for both Public_Endpoint and Protected_Endpoint routes.
4. WHEN the Jwt_Authentication_Filter authenticates a request, THE Jwt_Authentication_Filter SHALL leave no authentication state in the Security_Context after the request completes, so that no authenticated identity leaks into a later request on the same pooled thread.

### Requirement 12: Token and credential confidentiality

**User Story:** As a security owner, I want the enforcement layer to keep tokens and the signing secret out of URLs, logs, and responses, so that enforcement does not become a leak path. (§9, §11)

#### Acceptance Criteria

1. THE AuthN_Enforcement_Feature SHALL read the Access_Token only from the Bearer_Header and SHALL NOT read any Access_Token from a URL or query parameter.
2. THE AuthN_Enforcement_Feature SHALL exclude any full Access_Token, the Token_Id_Claim, and the signing secret from every log statement it emits at any log level, including any exception message and stack trace those statements carry.
3. WHEN the Authentication_Entry_Point or the Jwt_Authentication_Filter rejects a request, THE AuthN_Enforcement_Feature SHALL exclude the presented Access_Token and the signing secret from the response body and from the response headers.

### Requirement 13: Security seam cleanup and boundary supersession

**User Story:** As a maintainer, I want the permit-all seam and its workarounds retired, so that the codebase no longer carries the skeleton authentication placeholder. (§3, §11)

#### Acceptance Criteria

1. THE AuthN_Enforcement_Feature SHALL replace the permit-all Security_Policy that E2 and E3 left unchanged, so that the security-seam boundary deferred by E2 Requirement 14 and E3 Requirement 13 is closed.
2. THE AuthN_Enforcement_Feature SHALL provide the Current_User_Provider_Impl that E3 Requirement 13 deferred, so that the Current_User_Provider interface is no longer without an implementation.
3. THE AuthN_Enforcement_Feature SHALL preserve the public reachability of the Auth_Public_Endpoints so that a user who holds no Access_Token can still sign up, log in, verify, and resend verification, consistent with E2 Requirement 14.3 and E3 Requirement 13.5.
4. THE AuthN_Enforcement_Feature SHALL introduce no role-based or resource-based authorization, so that every authenticated request is authorized to reach every Protected_Endpoint, consistent with the product's exclusion of fine-grained roles and team membership.

### Requirement 14: Error mapping consistency

**User Story:** As an API consumer, I want authentication failures from the security layer to match the shape of authentication failures from handlers, so that one error-handling path covers both. (§9, §11)

#### Acceptance Criteria

1. WHEN an Unauthorized_Error raised by a handler during a Protected_Endpoint request reaches the Problem_Handler, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status `401` and a body `status` member equal to `401`.
2. THE Authentication_Entry_Point SHALL produce a `401` problem response whose `status`, `correlationId`, and `timestamp` members match the shape of the `401` problem response that the Problem_Handler produces for a handler-raised Unauthorized_Error.
3. THE AuthN_Enforcement_Feature SHALL introduce no new HTTP status mapping, reusing the `401` semantics already mapped for the Unauthorized_Error.

### Requirement 15: Dependency hygiene and quality gates

**User Story:** As a maintainer, I want enforcement verified against the project's quality gates, so that the authentication boundary is proven before the business epics build on it. (§11; E4 Definition of Done)

#### Acceptance Criteria

1. THE Project_Build SHALL include a Jwt_Authentication_Filter test asserting that a valid Access_Token populates the Security_Context with the Token_Subject_Claim as the Authentication_Principal, and that an expired Access_Token, a denylisted Access_Token, and a request with no Bearer_Header each leave the Security_Context unauthenticated (valid, expired, denylisted, missing).
2. THE Project_Build SHALL include a Current_User_Provider_Impl test asserting that an authenticated Security_Context yields the authenticated user id, and that an absent or anonymous authentication yields an Unauthorized_Error.
3. THE Project_Build SHALL include a slice test asserting that a request to a Protected_Endpoint without an Access_Token receives an RFC 9457 `401` whose body `status` member equals `401`, and that the same endpoint with a valid Access_Token is reached.
4. THE Project_Build SHALL include a Cucumber BDD scenario, tagged `@auth` and executed under the Integration_Test_Context with Testcontainers PostgreSQL and Valkey, that asserts a Protected_Endpoint returns HTTP status `401` without an Access_Token and returns HTTP status `200` when called with an Access_Token obtained by signing up, verifying, and logging in.
5. THE Project_Build SHALL satisfy the Coverage_Gate thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages, covering the Jwt_Authentication_Filter, the Authentication_Entry_Point, and the Current_User_Provider_Impl, none of which fall under a Coverage_Gate exclusion.
6. WHEN the Spotless and the Error Prone with NullAway checks run, THE Project_Build SHALL complete without formatting or static-analysis violations.
7. IF line coverage or branch coverage over the non-excluded packages is below 90%, or IF Spotless detects a formatting violation, or IF Error Prone with NullAway detects a static-analysis violation, THEN THE Project_Build SHALL fail.
