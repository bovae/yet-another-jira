# epics-crud

## ADDED Requirements

### Requirement: List epics
The system SHALL return epics to any authenticated user via `GET /api/v1/epics`. When a `teamId` query parameter is provided, only epics belonging to that team SHALL be returned; otherwise all epics are returned. No membership or ownership filtering applies.

#### Scenario: List returns all epics
- **WHEN** an authenticated user requests `GET /api/v1/epics`
- **THEN** the response is `200` with every epic (id, team_id, title, description, created_at, modified_at), timestamps ISO-8601 UTC

#### Scenario: List filtered by team
- **WHEN** an authenticated user requests `GET /api/v1/epics?teamId={id}` and epics exist under several teams
- **THEN** the response is `200` containing only the epics whose `team_id` equals the filter

#### Scenario: Filter by team with no epics
- **WHEN** the `teamId` filter matches no epics (including a non-existent team id)
- **THEN** the response is `200` with an empty array

### Requirement: Create epic
The system SHALL create an epic via `POST /api/v1/epics` with a required `teamId`, a required `title`, and an optional `description`. The referenced team MUST exist (`404` otherwise). The title MUST be trimmed before validation and persistence, MUST be non-empty after trimming, and MUST NOT exceed 200 characters after trimming. The description, when present, MUST NOT exceed 10000 characters; a blank description is stored as absent. `created_at`/`modified_at` are server-set UTC. Epic titles are NOT required to be unique.

#### Scenario: Successful create
- **WHEN** an authenticated user posts `{"teamId": "<existing>", "title": "  Payments  ", "description": "Q3 scope"}`
- **THEN** the response is `201` with the epic whose title is `"Payments"` (trimmed), the given team id, a UUID id, and server-set timestamps

#### Scenario: Create without description
- **WHEN** an authenticated user posts a valid `teamId` and `title` with no description
- **THEN** the response is `201` and the epic's description is absent/null

#### Scenario: Unknown team rejected
- **WHEN** the posted `teamId` does not reference an existing team
- **THEN** the response is `404` with an RFC 9457 problem detail

#### Scenario: Blank title rejected
- **WHEN** the posted title is empty or whitespace-only
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Over-length title rejected
- **WHEN** the posted title is longer than 200 characters after trimming
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Duplicate titles allowed
- **WHEN** an epic titled `"Payments"` already exists under the team and a user posts the same title
- **THEN** the response is `201` — titles are not unique

### Requirement: Get epic
The system SHALL return a single epic via `GET /api/v1/epics/{id}`, or `404` when it does not exist.

#### Scenario: Existing epic
- **WHEN** an authenticated user requests an existing epic id
- **THEN** the response is `200` with that epic

#### Scenario: Unknown epic
- **WHEN** an authenticated user requests a non-existent epic id
- **THEN** the response is `404` with an RFC 9457 problem detail

### Requirement: Update epic with immutable team
The system SHALL update an epic's title and description via `PUT /api/v1/epics/{id}` applying the same title and description rules as create. The update contract SHALL NOT accept a team reference — the epic's team is fixed at creation and cannot change. PUT is a full replacement: an absent or blank description clears the stored one. A successful update MUST advance `modified_at` (server UTC), and the advanced value MUST be persisted — observable on a subsequent `GET`.

#### Scenario: Successful update
- **WHEN** an authenticated user puts a valid new title/description to an existing epic
- **THEN** the response is `200` with the updated fields, the unchanged `team_id`, and an advanced `modified_at`

#### Scenario: Advanced modified_at is persisted
- **WHEN** an epic is updated and then fetched via `GET /api/v1/epics/{id}`
- **THEN** the fetched `modified_at` is later than the epic's `modified_at` at creation

#### Scenario: Team cannot change on edit
- **WHEN** an update request is sent for an existing epic
- **THEN** the epic's `team_id` after the update equals its `team_id` at creation, regardless of any team value smuggled into the request body

#### Scenario: Omitted description clears it
- **WHEN** an epic has a description and an update is sent without one
- **THEN** the response is `200` and the stored description is absent/null

#### Scenario: Blank title rejected on update
- **WHEN** the new title is empty or whitespace-only
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Update unknown epic
- **WHEN** the epic id does not exist
- **THEN** the response is `404`

### Requirement: Delete epic with referential guard
The system SHALL delete an epic via `DELETE /api/v1/epics/{id}` only when no ticket references it. If any ticket references the epic, the system MUST reject with `409 Conflict` and a clear message; cascading deletion is not allowed. A referencing ticket inserted concurrently after the guard check (surfacing as a database foreign-key violation) MUST also produce `409`, not `500`.

#### Scenario: Clean delete
- **WHEN** an authenticated user deletes an epic no ticket references
- **THEN** the response is `204` and the epic no longer exists

#### Scenario: Delete blocked by referencing tickets
- **WHEN** at least one ticket references the epic
- **THEN** the response is `409` with a problem detail naming the reason, and the epic still exists

#### Scenario: Delete blocked by concurrent insert
- **WHEN** a ticket referencing the epic is inserted after the guard check but before the delete commits
- **THEN** the response is `409` with an RFC 9457 problem detail, not `500`

#### Scenario: Delete unknown epic
- **WHEN** the epic id does not exist
- **THEN** the response is `404`

### Requirement: Authentication required
All epic endpoints SHALL require a valid bearer token (enforced by the existing security configuration's authenticated-by-default rule for `/api/v1/**`).

#### Scenario: Unauthenticated request rejected
- **WHEN** any epic endpoint is called without a valid token
- **THEN** the response is `401` with an RFC 9457 problem detail
