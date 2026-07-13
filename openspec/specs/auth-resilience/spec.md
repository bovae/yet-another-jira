# auth-resilience

## Purpose
Failure-mode behavior of the auth stack: verification email dispatch timing, token-denylist store outages, and rate-limit accounting integrity.

## Requirements

### Requirement: Verification email dispatch is asynchronous
The system SHALL dispatch verification emails off the HTTP request thread. Response latency of signup and resend-verification MUST NOT depend on whether an account exists or on the SMTP round-trip, preserving the deliberately uniform `202` response as a timing-safe non-oracle. A dispatch failure after commit MUST NOT change the HTTP response and MUST be logged without recipient PII.

#### Scenario: Resend latency independent of account existence
- **WHEN** resend-verification is called for an existing unverified account
- **THEN** the `202` response returns without waiting for the SMTP send to complete

#### Scenario: Dispatch failure does not surface to the client
- **WHEN** the asynchronous email send fails after the transaction has committed
- **THEN** the client still receives the success response and the failure is logged without the recipient address

### Requirement: Token denylist store outage fails closed
When the token denylist store (Valkey) is unreachable during JWT validation, the system SHALL reject the request with a deliberate `503` RFC 9457 problem detail. The request MUST NOT be authenticated (fail-closed), and the response MUST NOT leak connection or infrastructure details.

#### Scenario: Denylist store unreachable
- **WHEN** an authenticated request arrives while Valkey is down and the denylist check cannot complete
- **THEN** the response is `503` with an RFC 9457 problem detail and no stack trace or infrastructure detail in the body

#### Scenario: Store recovery restores normal validation
- **WHEN** Valkey becomes reachable again
- **THEN** subsequent requests with valid tokens authenticate normally

### Requirement: Atomic rate-limit accounting
Fixed-window rate-limit counters SHALL be incremented and given their window TTL atomically. A counter key MUST never exist without a TTL, regardless of process crashes or timeouts between operations. The reported `Retry-After` MUST reflect the actual remaining window: a full window SHALL be re-imposed only when a key is genuinely found without a TTL, never when the window is in its final sub-second or has just expired.

#### Scenario: Counter always carries a TTL
- **WHEN** a rate-limited operation increments its counter for the first time in a window
- **THEN** the increment and the window expiry are applied as one atomic operation, so the key cannot be observed without a TTL

#### Scenario: Limit exceeded still reports retry window
- **WHEN** the counter exceeds the configured limit
- **THEN** the response is `429` with a `Retry-After` no greater than the window length

#### Scenario: Expiring window is not re-armed
- **WHEN** a rate-limited key is observed in the final sub-second of its window or immediately after expiry
- **THEN** the reported `Retry-After` is at most one second — the user is not locked into a fresh full window

### Requirement: Soft-deleted users are rejected at authentication
JWT authentication SHALL verify that the token's user still exists and is not soft-deleted, on every authenticated request — not only on `/me`. A request bearing an otherwise-valid token for a soft-deleted or missing user MUST be rejected with `401`, so deleted accounts cannot author tickets or comments through any endpoint.

#### Scenario: Soft-deleted user's valid token rejected
- **WHEN** a request arrives with an unexpired, non-denylisted token whose user has `deleted_at` set
- **THEN** the response is `401` with an RFC 9457 problem detail and no business logic runs

### Requirement: Successful login resets the rate-limit window
A successful authentication SHALL clear the login rate-limit counter for that key. Legitimate consecutive logins MUST NOT be locked out by their own successes; only failed attempts accumulate toward the limit.

#### Scenario: Sixth successful login is not locked out
- **WHEN** a user logs in successfully five times within the window and attempts a sixth valid login
- **THEN** the sixth login succeeds — the counter was reset on each success

### Requirement: Logout is idempotent for unusable tokens
Logout with an already-expired token SHALL succeed with `204` — the token is already unusable, so revocation is a no-op, not an error.

#### Scenario: Logout with expired token
- **WHEN** a user calls logout with a token that has already expired
- **THEN** the response is `204`, not `401`

### Requirement: Rate-limit store outage returns the deliberate 503
When the rate-limit store (Valkey) is unreachable during login or resend-verification rate limiting, the system SHALL return the same deliberate `503` RFC 9457 problem detail as the token-denylist outage path — one outage, one client answer, no generic `500`.

#### Scenario: Login during store outage
- **WHEN** a login or resend request arrives while Valkey is down and the rate-limit check cannot complete
- **THEN** the response is `503` with an RFC 9457 problem detail, matching the denylist-outage contract
