# fe-team-management

## Purpose
Frontend team management screen: list teams, create, rename, and delete teams. The screen talks to the backend teams API, surfaces RFC 9457 problem details in dialogs, and disables deletion for teams that are still referenced by epics or tickets.

## Requirements

### Requirement: Team list screen
The frontend SHALL render the team management screen at `/teams` inside the app shell, listing every team from `GET /api/v1/teams` with its name and created/modified timestamps. The screen SHALL show the shared loading state while fetching, the shared empty state when no teams exist, and the shared error state (with retry) when the fetch fails.

#### Scenario: Teams listed
- **WHEN** an authenticated user opens `/teams` and teams exist
- **THEN** every team renders with its name and timestamps

#### Scenario: Empty state
- **WHEN** an authenticated user opens `/teams` and no teams exist
- **THEN** the shared empty state renders with a prompt to create the first team

#### Scenario: Fetch error with retry
- **WHEN** the teams fetch fails
- **THEN** the shared error state renders and its retry control re-triggers the fetch

### Requirement: Create team
The frontend SHALL provide a create-team action opening a dialog with a name field. Submitting SHALL call `POST /api/v1/teams`; on success the dialog closes and the list reflects the new team. On failure (`400` invalid name, `409` duplicate) the backend problem `detail` SHALL render inside the open dialog with the entered value preserved.

#### Scenario: Successful create
- **WHEN** the user submits a valid name in the create dialog
- **THEN** the dialog closes and the new team appears in the list

#### Scenario: Duplicate name surfaced
- **WHEN** the backend rejects the create with `409`
- **THEN** the dialog stays open showing the problem `detail` and the entered name

#### Scenario: Pending state on submit
- **WHEN** the create request is in flight
- **THEN** the submit control is disabled/indicates progress, preventing duplicate submits

### Requirement: Rename team
The frontend SHALL provide a rename action per team opening a dialog pre-filled with the current name. Submitting SHALL call `PUT /api/v1/teams/{id}`; on success the dialog closes and the list shows the new name. On failure (`400`/`409`) the problem `detail` SHALL render inside the open dialog.

#### Scenario: Successful rename
- **WHEN** the user submits a valid new name in the rename dialog
- **THEN** the dialog closes and the list shows the renamed team

#### Scenario: Rename conflict surfaced
- **WHEN** the backend rejects the rename with `409`
- **THEN** the dialog stays open showing the problem `detail`

### Requirement: Delete team with confirmation and reference-aware disable
The frontend SHALL provide a delete action per team behind a confirmation dialog calling `DELETE /api/v1/teams/{id}`. The delete control SHALL be disabled, with a clear message that the team is referenced, when the team has epics (computed from `GET /api/v1/epics` grouped by `teamId`) or tickets (computed from `GET /api/v1/tickets` grouped by `teamId`). When enabled and confirmed, a `204` removes the team from the list; a `409` (reference created concurrently) SHALL render the backend problem `detail` without removing the team.

#### Scenario: Delete disabled for team with epics
- **WHEN** the epics list contains at least one epic whose `teamId` is the team's id
- **THEN** the team's delete control is disabled and a clear referenced-team message is available

#### Scenario: Delete disabled for team with tickets
- **WHEN** the tickets list contains at least one ticket whose `teamId` is the team's id
- **THEN** the team's delete control is disabled and a clear referenced-team message is available

#### Scenario: Delete enabled for unreferenced team
- **WHEN** no epic and no ticket references the team
- **THEN** the delete control is enabled

#### Scenario: Confirmed delete removes team
- **WHEN** the user confirms deletion of an unreferenced team and the backend returns `204`
- **THEN** the confirmation dialog closes and the team disappears from the list

#### Scenario: Concurrent-reference conflict surfaced
- **WHEN** the confirmed delete returns `409`
- **THEN** the problem `detail` renders and the team remains in the list

#### Scenario: Cancel keeps team
- **WHEN** the user cancels the confirmation dialog
- **THEN** no delete request is issued and the team remains

### Requirement: Whitespace-only names are not submitted
The create and rename forms SHALL trim the name before the required-check and SHALL NOT submit a name that is empty after trimming, matching the ticket form's behavior.

#### Scenario: Whitespace-only name blocked client-side
- **WHEN** the user enters a name consisting only of whitespace and submits
- **THEN** no request is issued and the form indicates the name is required
