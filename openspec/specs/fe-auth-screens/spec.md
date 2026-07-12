# fe-auth-screens

## Purpose
Frontend authentication screens: sign-up, login, email-verification result and error screens, and the resend-verification action. These screens talk to the backend auth API, surface RFC 9457 problem details to the user, and integrate with the app's auth context and route guards.

## Requirements

### Requirement: Sign-up screen registers a new account
The frontend SHALL provide a sign-up screen at `/signup` with email and password fields that submits to `POST /api/v1/auth/signup`. Client-side hints (email format, password ≥ 8 characters) MAY guide input, but the server response is authoritative. While the request is in flight the form SHALL show a loading state and prevent duplicate submission. On success (`201`) the screen SHALL show a check-your-email confirmation for the registered address instead of the form, with a link to `/login`. The screen SHALL link to `/login` for existing accounts. The password MUST never appear in a URL or be logged.

#### Scenario: Successful sign-up
- **WHEN** a user submits a valid email and a password of at least 8 characters
- **THEN** `POST /api/v1/auth/signup` is called and a confirmation state renders telling the user a verification email was sent to that address, with a link to the login screen

#### Scenario: Duplicate email
- **WHEN** the backend responds `409`
- **THEN** the form re-renders with the backend problem `detail` message and the user can correct and resubmit

#### Scenario: Validation rejection
- **WHEN** the backend responds `400` (malformed email or weak password)
- **THEN** the form re-renders with the backend problem `detail` message

#### Scenario: Submission in flight
- **WHEN** a sign-up request is pending
- **THEN** the submit control is disabled and a loading indicator shows

### Requirement: Login screen authenticates and redirects
The frontend SHALL provide a login screen at `/login` with email and password fields that authenticates via the auth context's `login` action. On success the user SHALL be redirected to the location preserved in navigation state by the route guard, or to `/` when none exists. The screen SHALL link to `/signup`. While the request is in flight the form SHALL show a loading state and prevent duplicate submission.

#### Scenario: Successful login redirects to intended location
- **WHEN** a user who was redirected to `/login` from `/teams` logs in successfully
- **THEN** they land on `/teams`

#### Scenario: Successful login without intended location
- **WHEN** a user navigates directly to `/login` and logs in successfully
- **THEN** they land on `/`

#### Scenario: Invalid credentials
- **WHEN** the backend responds `401`
- **THEN** the form shows the backend problem `detail` message and no session is created

#### Scenario: Unverified account
- **WHEN** the backend responds `403` (email not verified)
- **THEN** the form shows the backend message and offers the resend-verification action

#### Scenario: Rate limited
- **WHEN** the backend responds `429`
- **THEN** the form shows the backend message asking the user to retry later

### Requirement: Verification result screen consumes the emailed token
The frontend SHALL provide a verification result screen at `/verify` that reads the `token` query parameter and submits it to `POST /api/v1/auth/verify` exactly once on load, showing a loading state while pending. On success it SHALL show a verified confirmation with a link to `/login` (no automatic login). On failure (`400`, `410`) or when the token parameter is absent it SHALL show an error state offering the resend-verification action.

#### Scenario: Valid token
- **WHEN** the user opens `/verify?token=<valid>`
- **THEN** the token is verified and a success state renders with a link to the login screen

#### Scenario: Expired or already-used token
- **WHEN** the backend responds `410` (or `400`)
- **THEN** an error state renders with the backend message and the resend-verification action

#### Scenario: Missing token
- **WHEN** the user opens `/verify` with no `token` parameter
- **THEN** an error state renders with the resend-verification action, without calling the API

### Requirement: Verification error screen serves the backend redirect
The frontend SHALL provide a public `/verify-error` screen rendering the verification-error state with the resend-verification action. This is the landing page for the backend's configured error redirect (`YAJ_VERIFICATION_ERROR_REDIRECT_URL`) when a browser-followed `GET /api/v1/auth/verify` link fails.

#### Scenario: Backend redirects a failed link
- **WHEN** the user lands on `/verify-error`
- **THEN** an error state explains the link was invalid or expired and offers the resend-verification action

### Requirement: Resend-verification action requests a new email
The frontend SHALL provide a resend-verification action, reachable from both the login screen and the verification result/error screens, that takes an email address and submits it to `POST /api/v1/auth/verification/resend`. On the uniform `202` response it SHALL display the backend's message. On `429` it SHALL display the rate-limit message. While pending it SHALL show a loading state and prevent duplicate submission.

#### Scenario: Resend accepted
- **WHEN** the user submits an email address
- **THEN** `POST /api/v1/auth/verification/resend` is called and the uniform confirmation message renders

#### Scenario: Resend rate limited
- **WHEN** the backend responds `429`
- **THEN** the rate-limit message renders and the user may retry later

### Requirement: Auth API errors surface backend problem details
Auth API calls made by these screens (`signup`, `verify`, `resend`, and login via the auth context) SHALL parse RFC 9457 problem responses and surface the `detail` message to the user. When a response has no parseable problem body, a generic failure message SHALL be shown instead. Raw status codes, stack traces, or response internals MUST NOT be rendered.

#### Scenario: Problem detail rendered
- **WHEN** an auth endpoint responds with a problem body containing `detail`
- **THEN** that message is what the user sees

#### Scenario: Malformed error response
- **WHEN** an auth endpoint fails without a parseable problem body (e.g. network error, timeout)
- **THEN** a generic human-readable failure message renders
