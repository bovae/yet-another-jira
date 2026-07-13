# fe-auth-session

## Purpose
Frontend authentication session management: hydrating the session from a stored JWT on load, attaching the bearer token to API requests, handling global 401 session expiry, and logout.

## Requirements

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
When a request that carried a bearer token receives a `401` response, the frontend SHALL clear the stored token, the session state, and all cached server data, and the user SHALL be redirected to `/login`. A `401` on a request that carried no token (e.g. a failed login attempt) MUST NOT trigger this handling.

#### Scenario: Expired session on business call
- **WHEN** an authenticated request returns `401`
- **THEN** the token is cleared, the auth context becomes unauthenticated, cached server data is cleared, and the user lands on `/login`

#### Scenario: Credential failure is not a session expiry
- **WHEN** a request without a bearer token returns `401`
- **THEN** no session clearing occurs

### Requirement: Logout ends the session
The frontend SHALL provide a logout action that calls `POST /api/v1/auth/logout`, clears the stored token, the session state, and all cached server data, and redirects to `/login`. Local session clearing MUST happen even if the logout request fails. The next session on the same browser MUST NOT be served the previous session's cached data.

#### Scenario: Successful logout
- **WHEN** the user triggers logout
- **THEN** `POST /api/v1/auth/logout` is called, the token and cached server data are cleared, and the user lands on `/login`

#### Scenario: Logout with network failure
- **WHEN** the logout request fails
- **THEN** the local token, session state, and cached server data are still cleared and the user lands on `/login`

#### Scenario: Next account sees no stale data
- **WHEN** a user logs out and a different account logs in on the same browser
- **THEN** no screen renders data cached during the previous session

### Requirement: Failed login leaves no stored token
When the login flow stores a token but the follow-up user fetch fails (for any reason, not only `401`), the frontend SHALL remove the stored token before surfacing the error. A login that reports failure MUST NOT leave a valid token in storage that would silently log the user in on refresh.

#### Scenario: User fetch failure cleans up
- **WHEN** login receives a token but the subsequent `/api/v1/auth/me` call fails with a non-401 error
- **THEN** the stored token is removed, the app stays unauthenticated, and a refresh does not silently log in
