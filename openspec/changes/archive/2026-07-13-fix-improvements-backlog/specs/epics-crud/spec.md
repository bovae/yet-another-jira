# epics-crud

## ADDED Requirements

### Requirement: Write races return truthful statuses
Concurrent-write interleavings SHALL surface as the same client error the sequential order would produce, never as `500`. When the referenced team is deleted between the create pre-check and the insert flush (database FK violation), the response SHALL be `404`. When the epic row vanishes between load and flush (concurrent delete), update and delete requests SHALL return `404`.

#### Scenario: Team deleted during epic create
- **WHEN** the team referenced by an epic create request is deleted concurrently after the pre-check but before the insert commits
- **THEN** the response is `404` with an RFC 9457 problem detail, not `500`

#### Scenario: Epic deleted during update
- **WHEN** an epic is deleted concurrently while an update or delete for it is in flight
- **THEN** the losing request gets `404` with an RFC 9457 problem detail, not `500`

## MODIFIED Requirements

### Requirement: List epics
The system SHALL return epics to any authenticated user via `GET /api/v1/epics`. When a `teamId` query parameter is provided, only epics belonging to that team SHALL be returned; otherwise all epics are returned. No membership or ownership filtering applies. Results SHALL be ordered deterministically by creation time, ties broken by id, so repeated requests return the same order.

#### Scenario: List returns all epics
- **WHEN** an authenticated user requests `GET /api/v1/epics`
- **THEN** the response is `200` with every epic (id, team_id, title, description, created_at, modified_at), timestamps ISO-8601 UTC

#### Scenario: List filtered by team
- **WHEN** an authenticated user requests `GET /api/v1/epics?teamId={id}` and epics exist under several teams
- **THEN** the response is `200` containing only the epics whose `team_id` equals the filter

#### Scenario: Filter by team with no epics
- **WHEN** the `teamId` filter matches no epics (including a non-existent team id)
- **THEN** the response is `200` with an empty array

#### Scenario: Stable order across requests
- **WHEN** the same epic list is requested twice with rows updated in between
- **THEN** the epics appear in the same creation-time order both times
