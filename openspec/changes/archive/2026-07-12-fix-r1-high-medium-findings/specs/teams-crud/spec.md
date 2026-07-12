# teams-crud — delta

## MODIFIED Requirements

### Requirement: Create team
The system SHALL create a team via `POST /api/v1/teams`. The name MUST be trimmed before validation and persistence, MUST be non-empty after trimming, MUST NOT exceed 100 characters after trimming, and MUST be unique case-insensitively. Uniqueness violations MUST surface as `409` even when the duplicate is detected by the database constraint rather than the pre-check (concurrent creates). `created_at`/`modified_at` are server-set UTC.

#### Scenario: Successful create
- **WHEN** an authenticated user posts `{"name": "  Platform  "}`
- **THEN** the response is `201` with the team whose name is `"Platform"` (trimmed), a UUID id, and server-set timestamps

#### Scenario: Blank name rejected
- **WHEN** an authenticated user posts a name that is empty or whitespace-only
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Over-length name rejected
- **WHEN** an authenticated user posts a name longer than 100 characters after trimming
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Duplicate name rejected case-insensitively
- **WHEN** a team named `"Platform"` exists and a user posts `{"name": "platform"}`
- **THEN** the response is `409` with an RFC 9457 problem detail

#### Scenario: Concurrent duplicate create rejected
- **WHEN** two concurrent creates with the same name both pass the pre-check and the database unique constraint rejects the second insert
- **THEN** the losing request gets `409` with an RFC 9457 problem detail, not `500`

### Requirement: Rename team
The system SHALL rename a team via `PUT /api/v1/teams/{id}` applying the same name rules as create (trim, non-empty, at most 100 characters, case-insensitively unique — including constraint-detected races, which MUST surface as `409`). A successful rename MUST advance `modified_at` (server UTC), and the advanced value MUST be persisted — observable on a subsequent `GET`, not only in the rename response body. Renaming a team to its own current name SHALL succeed.

#### Scenario: Successful rename
- **WHEN** an authenticated user puts a valid new name to an existing team
- **THEN** the response is `200` with the updated name and an advanced `modified_at`

#### Scenario: Advanced modified_at is persisted
- **WHEN** a team is renamed and then fetched via `GET /api/v1/teams/{id}`
- **THEN** the fetched `modified_at` is later than the team's `modified_at` at creation

#### Scenario: Rename to name of another team rejected
- **WHEN** the new name equals another team's name ignoring case
- **THEN** the response is `409`

#### Scenario: Over-length rename rejected
- **WHEN** the new name is longer than 100 characters after trimming
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Rename unknown team
- **WHEN** the team id does not exist
- **THEN** the response is `404`

### Requirement: Delete team with referential guard
The system SHALL delete a team via `DELETE /api/v1/teams/{id}` only when it contains no epics and no tickets. If any epic or ticket references the team, the system MUST reject with `409 Conflict` and a clear message; cascading deletion is not allowed. A referencing row inserted concurrently after the guard check (surfacing as a database foreign-key violation) MUST also produce `409`, not `500`.

#### Scenario: Clean delete
- **WHEN** an authenticated user deletes a team with no epics and no tickets
- **THEN** the response is `204` and the team no longer exists

#### Scenario: Delete blocked by references
- **WHEN** the team has at least one epic or at least one ticket
- **THEN** the response is `409` with a problem detail naming the reason, and the team still exists

#### Scenario: Delete blocked by concurrent insert
- **WHEN** an epic or ticket referencing the team is inserted after the guard check but before the delete commits
- **THEN** the response is `409` with an RFC 9457 problem detail, not `500`

#### Scenario: Delete unknown team
- **WHEN** the team id does not exist
- **THEN** the response is `404`
