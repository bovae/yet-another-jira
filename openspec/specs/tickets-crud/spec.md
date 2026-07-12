# tickets-crud

## Purpose
CRUD management of tickets, the work items tracked on the board. All endpoints live under `/api/v1/tickets`, require authentication, trim and cap title/body, validate `type` and `state` enums, optionally link an epic that must belong to the ticket's team, advance `modified_at` only on an actual change, and cascade comment deletion when a ticket is removed.

## Requirements

### Requirement: List tickets
The system SHALL return tickets to any authenticated user via `GET /api/v1/tickets`. When a `teamId` query parameter is provided, only tickets belonging to that team SHALL be returned; otherwise all tickets are returned.

#### Scenario: List returns all tickets
- **WHEN** an authenticated user requests `GET /api/v1/tickets`
- **THEN** the response is `200` with every ticket (id, team_id, epic_id, type, state, title, body, created_by, created_at, modified_at), timestamps ISO-8601 UTC

#### Scenario: List filtered by team
- **WHEN** an authenticated user requests `GET /api/v1/tickets?teamId={id}` and tickets exist under several teams
- **THEN** the response is `200` containing only the tickets whose `team_id` equals the filter

#### Scenario: Filter by team with no tickets
- **WHEN** the `teamId` filter matches no tickets (including a non-existent team id)
- **THEN** the response is `200` with an empty array

### Requirement: Create ticket
The system SHALL create a ticket via `POST /api/v1/tickets` with a required `teamId`, `type`, `state`, `title`, `body`, and an optional `epicId`. The referenced team MUST exist (`404` otherwise). `type` MUST be one of `bug|feature|fix` and `state` one of `new|ready_for_implementation|in_progress|ready_for_acceptance|done` (`400` otherwise). The title MUST be trimmed before validation and persistence, MUST be non-empty after trimming, and MUST NOT exceed 200 characters after trimming. The body MUST be non-empty after trimming and MUST NOT exceed 10000 characters. `created_by` is set from the authenticated user; `created_at`/`modified_at` are server-set UTC.

#### Scenario: Successful create
- **WHEN** an authenticated user posts `{"teamId": "<existing>", "type": "bug", "state": "new", "title": "  Fix login  ", "body": "Steps..."}`
- **THEN** the response is `201` with the ticket whose title is `"Fix login"` (trimmed), a UUID id, `created_by` equal to the caller's user id, and server-set timestamps

#### Scenario: Create with epic of the same team
- **WHEN** the posted `epicId` references an epic whose team equals the posted `teamId`
- **THEN** the response is `201` and the ticket's `epic_id` is set

#### Scenario: Create without epic
- **WHEN** an authenticated user posts a valid ticket with no `epicId`
- **THEN** the response is `201` and the ticket's `epic_id` is absent/null

#### Scenario: Epic from a different team rejected
- **WHEN** the posted `epicId` references an epic belonging to a different team than the posted `teamId`
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Unknown team rejected
- **WHEN** the posted `teamId` does not reference an existing team
- **THEN** the response is `404` with an RFC 9457 problem detail

#### Scenario: Unknown epic rejected
- **WHEN** the posted `epicId` does not reference an existing epic
- **THEN** the response is `404` with an RFC 9457 problem detail

#### Scenario: Invalid enum values rejected
- **WHEN** the posted `type` is not one of `bug|feature|fix`, or the posted `state` is not one of the 5 canonical codes
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Blank title or body rejected
- **WHEN** the posted title or body is empty or whitespace-only
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Over-length title or body rejected
- **WHEN** the posted title exceeds 200 characters after trimming, or the body exceeds 10000 characters
- **THEN** the response is `400` with an RFC 9457 problem detail

### Requirement: Get ticket
The system SHALL return a single ticket with all fields (including created_by, created_at, modified_at) via `GET /api/v1/tickets/{id}`, or `404` when it does not exist.

#### Scenario: Existing ticket
- **WHEN** an authenticated user requests an existing ticket id
- **THEN** the response is `200` with that ticket including `created_by`, `created_at`, and `modified_at`

#### Scenario: Unknown ticket
- **WHEN** an authenticated user requests a non-existent ticket id
- **THEN** the response is `404` with an RFC 9457 problem detail

### Requirement: Update ticket
The system SHALL update a ticket's team, type, state, epic, title, and body via `PUT /api/v1/tickets/{id}` applying the same validation rules as create. PUT is a full replacement: an omitted `epicId` clears the stored epic reference. When the team changes, any `epicId` in the request MUST belong to the new team (`400` otherwise) — a stale epic from the previous team is rejected, never silently kept.

#### Scenario: Successful update
- **WHEN** an authenticated user puts valid new field values to an existing ticket
- **THEN** the response is `200` with the updated fields

#### Scenario: Team change with cleared epic
- **WHEN** a ticket with an epic is updated to a different team with no `epicId` in the request
- **THEN** the response is `200`, the ticket's `team_id` is the new team, and its `epic_id` is absent/null

#### Scenario: Team change keeping old-team epic rejected
- **WHEN** a ticket is updated to a different team while the request's `epicId` still references an epic of the previous team
- **THEN** the response is `400` with an RFC 9457 problem detail and the ticket is unchanged

#### Scenario: Validation rules match create
- **WHEN** an update carries a blank title, blank body, invalid enum code, over-length title/body, unknown team, or unknown epic
- **THEN** the response is `400` (validation) or `404` (unknown reference), matching the create contract

#### Scenario: Update unknown ticket
- **WHEN** the ticket id does not exist
- **THEN** the response is `404`

### Requirement: `modified_at` advances only on actual change
The system SHALL advance `modified_at` (server UTC) when an update or state change alters any persisted field, and the advanced value MUST be persisted — observable on a subsequent `GET`. Saving unchanged values MUST NOT advance `modified_at`.

#### Scenario: Actual change advances modified_at
- **WHEN** a ticket is updated with a different title and then fetched via `GET /api/v1/tickets/{id}`
- **THEN** the fetched `modified_at` is later than its value before the update

#### Scenario: No-op save does not advance modified_at
- **WHEN** a ticket is updated via PUT with exactly its current field values
- **THEN** the response is `200` and `modified_at` equals its value before the request, including on a subsequent `GET`

### Requirement: State change endpoint
The system SHALL change only a ticket's state via `PATCH /api/v1/tickets/{id}` with body `{"state": "<code>"}`, persisting immediately. Any of the 5 canonical states SHALL be reachable from any other — no transition sequence is enforced. The operation MUST be idempotent: repeating the same state change succeeds and does not advance `modified_at` again.

#### Scenario: State change persists and advances modified_at
- **WHEN** an authenticated user patches an existing ticket with a different valid state
- **THEN** the response is `200` with the new state, `modified_at` is advanced, and a subsequent `GET` returns the new state

#### Scenario: Any state reachable from any other
- **WHEN** a ticket in state `done` is patched to state `new`
- **THEN** the response is `200` — no sequential workflow is enforced

#### Scenario: Same-state patch is an idempotent no-op
- **WHEN** a ticket is patched with its current state
- **THEN** the response is `200` and `modified_at` is unchanged

#### Scenario: Invalid state code rejected
- **WHEN** the patched state is not one of the 5 canonical codes
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Patch unknown ticket
- **WHEN** the ticket id does not exist
- **THEN** the response is `404`

### Requirement: Delete ticket cascades comments
The system SHALL delete a ticket via `DELETE /api/v1/tickets/{id}`, removing its comments through the existing database cascade. No referential guard applies to tickets.

#### Scenario: Successful delete
- **WHEN** an authenticated user deletes an existing ticket
- **THEN** the response is `204` and the ticket no longer exists

#### Scenario: Delete removes comments
- **WHEN** a ticket with comments is deleted
- **THEN** its comments no longer exist in the database

#### Scenario: Delete unknown ticket
- **WHEN** the ticket id does not exist
- **THEN** the response is `404`

### Requirement: Authentication required
All ticket endpoints SHALL require a valid bearer token (enforced by the existing security configuration's authenticated-by-default rule for `/api/v1/**`).

#### Scenario: Unauthenticated request rejected
- **WHEN** any ticket endpoint is called without a valid token
- **THEN** the response is `401` with an RFC 9457 problem detail
