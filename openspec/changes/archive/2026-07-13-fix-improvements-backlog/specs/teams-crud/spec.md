# teams-crud

## ADDED Requirements

### Requirement: Vanished-row races return 404
When a team row vanishes between load and flush (concurrent delete), rename and delete requests SHALL return `404` with an RFC 9457 problem detail, never `500`.

#### Scenario: Team deleted during rename
- **WHEN** a team is deleted concurrently while a rename or delete for it is in flight
- **THEN** the losing request gets `404` with an RFC 9457 problem detail, not `500`

## MODIFIED Requirements

### Requirement: List teams
The system SHALL return all teams to any authenticated user via `GET /api/v1/teams`. No membership or ownership filtering applies — all verified users see all teams. Results SHALL be ordered deterministically by creation time, ties broken by id, so repeated requests return the same order.

#### Scenario: List returns all teams
- **WHEN** an authenticated user requests `GET /api/v1/teams`
- **THEN** the response is `200` with every team (id, name, created_at, modified_at), timestamps ISO-8601 UTC

#### Scenario: Empty list on fresh database
- **WHEN** no teams exist and an authenticated user requests `GET /api/v1/teams`
- **THEN** the response is `200` with an empty array

#### Scenario: Stable order across requests
- **WHEN** the same team list is requested twice with rows renamed in between
- **THEN** the teams appear in the same creation-time order both times
