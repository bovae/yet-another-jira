# comments

## Purpose
Ticket comments: an append-only discussion thread on each ticket. All endpoints live under `/api/v1/tickets/{ticketId}/comments`, require authentication, trim and cap the body, and are immutable after creation (no update or delete). Adding a comment never advances the ticket's `modified_at`, so commenting does not affect board ordering.

## Requirements

### Requirement: List comments
The system SHALL return the comments of a ticket to any authenticated user via `GET /api/v1/tickets/{ticketId}/comments`, ordered chronologically oldest first, with ties on `created_at` broken by id so the order is stable across reads. The referenced ticket MUST exist (`404` otherwise).

#### Scenario: Comments returned oldest first
- **WHEN** an authenticated user requests the comments of a ticket that has several comments
- **THEN** the response is `200` with every comment (id, ticket_id, author_id, body, created_at), ordered by `created_at` ascending, timestamps ISO-8601 UTC

#### Scenario: Equal timestamps keep a stable order
- **WHEN** two comments of a ticket share the same `created_at`
- **THEN** repeated requests return them in the same order

#### Scenario: Ticket without comments
- **WHEN** an authenticated user requests the comments of an existing ticket that has none
- **THEN** the response is `200` with an empty array

#### Scenario: Unknown ticket rejected
- **WHEN** an authenticated user requests the comments of a non-existent ticket id
- **THEN** the response is `404` with an RFC 9457 problem detail

### Requirement: Insert races return truthful statuses
When the referenced ticket is deleted between the add-comment pre-check and the insert flush (database FK violation), the response SHALL be the `404` the pre-check would have returned, never `500`.

#### Scenario: Ticket deleted during comment add
- **WHEN** the ticket referenced by an add-comment request is deleted concurrently after the pre-check but before the insert commits
- **THEN** the response is `404` with an RFC 9457 problem detail, not `500`

### Requirement: Comment responses carry the author's email
Comment responses SHALL include `authorEmail` — the email of the author user — alongside the raw author id, so clients can display authorship without a user-lookup endpoint. List responses MUST resolve emails without issuing one query per comment.

#### Scenario: Comment response includes author email
- **WHEN** an authenticated user fetches the comments of a ticket commented on by several users
- **THEN** every comment carries both `authorId` and `authorEmail`

### Requirement: Add comment
The system SHALL create a comment via `POST /api/v1/tickets/{ticketId}/comments` with a required `body`. The referenced ticket MUST exist (`404` otherwise). The body MUST be trimmed before validation and persistence, MUST be non-empty after trimming, and MUST NOT exceed 10000 characters after trimming. The author is set from the authenticated user; `created_at` is server-set UTC. Comments are immutable after creation: the API SHALL NOT expose update or delete operations for comments.

#### Scenario: Successful add
- **WHEN** an authenticated user posts `{"body": "  Looks good  "}` to an existing ticket
- **THEN** the response is `201` with the comment whose body is `"Looks good"` (trimmed), a UUID id, `author_id` equal to the caller's user id, and a server-set `created_at`

#### Scenario: Blank body rejected
- **WHEN** the posted body is empty or whitespace-only
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Over-length body rejected
- **WHEN** the posted body exceeds 10000 characters after trimming
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Unknown ticket rejected
- **WHEN** the `ticketId` path segment does not reference an existing ticket
- **THEN** the response is `404` with an RFC 9457 problem detail

### Requirement: Adding a comment does not advance ticket `modified_at`
Adding a comment SHALL NOT change the ticket's `modified_at`, so commenting never affects board ordering.

#### Scenario: Ticket `modified_at` unchanged after comment
- **WHEN** an authenticated user adds a comment to a ticket
- **THEN** a subsequent `GET /api/v1/tickets/{id}` returns the same `modified_at` the ticket had before the comment

### Requirement: Authentication required
All comment endpoints SHALL require a valid bearer token (enforced by the existing security configuration's authenticated-by-default rule for `/api/v1/**`).

#### Scenario: Unauthenticated request rejected
- **WHEN** any comment endpoint is called without a valid token
- **THEN** the response is `401` with an RFC 9457 problem detail
