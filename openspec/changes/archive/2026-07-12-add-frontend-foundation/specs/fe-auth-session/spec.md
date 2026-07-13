# fe-auth-session

## ADDED Requirements

### Requirement: Session hydrates from stored token on load
The frontend SHALL persist the JWT access token in refresh-safe storage (localStorage, key `accessToken`) that is not the system of record. On application load with a token present, it SHALL validate the session by fetching `GET /api/v1/auth/me` and expose the resulting user (id, email) via an auth context. If validation fails, the token MUST be cleared and the app treated as unauthenticated.

#### Scenario: Valid token survives refresh
- **WHEN** the app loads with a valid token in storage
- **THEN** `/api/v1/auth/me` is fetched and the auth context exposes the current user as authenticated

#### Scenario: Invalid or expired token
- **WHEN** the app loads with a token that `/api/v1/auth/me` rejects
- **THEN** the token is cleared from storage and the app is unauthenticated

#### Scenario: No token
- **WHEN** the app loads with no stored token
- **THEN** the app is unauthenticated without calling `/api/v1/auth/me`

### Requirement: API client attaches bearer token
The API client SHALL attach `Authorization: Bearer <token>` to every request when a token is present in storage, and send no Authorization header otherwise. Tokens MUST never appear in URLs.

#### Scenario: Token attached
- **WHEN** a request is made while a token is stored
- **THEN** the request carries `Authorization: Bearer <token>`

#### Scenario: No token stored
- **WHEN** a request is made with no stored token
- **THEN** the request carries no Authorization header

### Requirement: Global 401 handling clears the session
When a request that carried a bearer token receives a `401` response, the frontend SHALL clear the stored token and session state, and the user SHALL be redirected to `/login`. A `401` on a request that carried no token (e.g. a failed login attempt) MUST NOT trigger this handling.

#### Scenario: Expired session on business call
- **WHEN** an authenticated request returns `401`
- **THEN** the token is cleared, the auth context becomes unauthenticated, and the user lands on `/login`

#### Scenario: Credential failure is not a session expiry
- **WHEN** a request without a bearer token returns `401`
- **THEN** no session clearing occurs

### Requirement: Logout ends the session
The frontend SHALL provide a logout action that calls `POST /api/v1/auth/logout`, clears the stored token and session state, and redirects to `/login`. Local session clearing MUST happen even if the logout request fails.

#### Scenario: Successful logout
- **WHEN** the user triggers logout
- **THEN** `POST /api/v1/auth/logout` is called, the token is cleared, and the user lands on `/login`

#### Scenario: Logout with network failure
- **WHEN** the logout request fails
- **THEN** the local token and session state are still cleared and the user lands on `/login`
