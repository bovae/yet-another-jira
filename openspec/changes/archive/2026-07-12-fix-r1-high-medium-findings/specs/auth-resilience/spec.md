# auth-resilience

Failure-mode behavior of the auth stack: verification email dispatch timing, token-denylist store outages, and rate-limit accounting integrity.

## ADDED Requirements

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
Fixed-window rate-limit counters SHALL be incremented and given their window TTL atomically. A counter key MUST never exist without a TTL, regardless of process crashes or timeouts between operations.

#### Scenario: Counter always carries a TTL
- **WHEN** a rate-limited operation increments its counter for the first time in a window
- **THEN** the increment and the window expiry are applied as one atomic operation, so the key cannot be observed without a TTL

#### Scenario: Limit exceeded still reports retry window
- **WHEN** the counter exceeds the configured limit
- **THEN** the response is `429` with a `Retry-After` no greater than the window length
