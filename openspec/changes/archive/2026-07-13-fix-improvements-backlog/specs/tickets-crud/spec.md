# tickets-crud

## ADDED Requirements

### Requirement: Write races return truthful statuses
Concurrent-write interleavings SHALL surface as the same client error the sequential order would produce, never as `500`. When a referenced team or epic is deleted between the service's existence pre-check and the insert/update flush (database FK violation), the response SHALL be the `404` the pre-check would have returned. When the ticket row itself vanishes between load and flush (concurrent delete), update, state-change, and delete requests SHALL return `404`.

#### Scenario: Referenced row deleted during create
- **WHEN** the team or epic referenced by a create request is deleted concurrently after the pre-check but before the insert commits
- **THEN** the response is `404` with an RFC 9457 problem detail, not `500`

#### Scenario: Ticket deleted during update
- **WHEN** a ticket is deleted concurrently while an update, state change, or delete for it is in flight
- **THEN** the losing request gets `404` with an RFC 9457 problem detail, not `500`

### Requirement: Ticket responses carry the creator's email
Ticket responses (list, get, create, update, state change) SHALL include `createdByEmail` — the email of the `created_by` user — alongside the raw user id, so clients can display authorship without a user-lookup endpoint. List responses MUST resolve emails without issuing one query per ticket.

#### Scenario: Ticket response includes creator email
- **WHEN** an authenticated user fetches a ticket created by any user
- **THEN** the response carries both `createdBy` (id) and `createdByEmail` (that user's email)

## MODIFIED Requirements

### Requirement: List tickets
The system SHALL return tickets to any authenticated user via `GET /api/v1/tickets`. When a `teamId` query parameter is provided, only tickets belonging to that team SHALL be returned; otherwise all tickets are returned. Results SHALL be ordered deterministically by creation time, ties broken by id, so repeated requests return the same order.

#### Scenario: List returns all tickets
- **WHEN** an authenticated user requests `GET /api/v1/tickets`
- **THEN** the response is `200` with every ticket (id, team_id, epic_id, type, state, title, body, created_by, created_at, modified_at), timestamps ISO-8601 UTC

#### Scenario: List filtered by team
- **WHEN** an authenticated user requests `GET /api/v1/tickets?teamId={id}` and tickets exist under several teams
- **THEN** the response is `200` containing only the tickets whose `team_id` equals the filter

#### Scenario: Filter by team with no tickets
- **WHEN** the `teamId` filter matches no tickets (including a non-existent team id)
- **THEN** the response is `200` with an empty array

#### Scenario: Stable order across requests
- **WHEN** the same ticket list is requested twice with rows updated in between
- **THEN** the tickets appear in the same creation-time order both times
