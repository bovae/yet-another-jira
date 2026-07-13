# comments

## ADDED Requirements

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

## MODIFIED Requirements

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
