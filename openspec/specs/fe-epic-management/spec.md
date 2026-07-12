# fe-epic-management

## Purpose
Frontend epic management screen: list epics with a team filter, create epics (team fixed at creation), edit epics (without changing the team), and delete epics. The screen talks to the backend epics API, resolves team names from the teams list, and surfaces RFC 9457 problem details in dialogs.

## Requirements

### Requirement: Epic list screen with team filter
The frontend SHALL render the epic management screen at `/epics` inside the app shell, listing epics with title, team name (resolved from the teams list), and created/modified timestamps. By default all epics load via `GET /api/v1/epics`; a team filter Select narrows the list via `GET /api/v1/epics?teamId={id}`. The screen SHALL show the shared loading, empty, and error (with retry) states.

#### Scenario: All epics listed by default
- **WHEN** an authenticated user opens `/epics` and epics exist under several teams
- **THEN** every epic renders with its title and its team's name

#### Scenario: Filter narrows to one team
- **WHEN** the user selects a team in the filter
- **THEN** only epics whose `teamId` matches render

#### Scenario: Empty state
- **WHEN** the (possibly filtered) list contains no epics
- **THEN** the shared empty state renders

#### Scenario: Fetch error with retry
- **WHEN** the epics fetch fails
- **THEN** the shared error state renders and its retry control re-triggers the fetch

### Requirement: Create epic with team fixed at creation
The frontend SHALL provide a create-epic action opening a dialog with a required team Select, a required title field, and an optional description field. Submitting SHALL call `POST /api/v1/epics` with the chosen `teamId`; on success the dialog closes and the list reflects the new epic. On failure (`400` invalid title/description, `404` unknown team) the problem `detail` SHALL render inside the open dialog. When no teams exist, the create path SHALL instead prompt the user to create a team first.

#### Scenario: Successful create
- **WHEN** the user picks a team, enters a title, and submits
- **THEN** the dialog closes and the new epic appears in the list under that team

#### Scenario: Validation error surfaced
- **WHEN** the backend rejects the create with `400`
- **THEN** the dialog stays open showing the problem `detail` with entered values preserved

#### Scenario: No teams yet
- **WHEN** the user invokes create-epic while no teams exist
- **THEN** a prompt to create a team first renders instead of the epic form

### Requirement: Edit epic without team change
The frontend SHALL provide an edit action per epic opening a dialog pre-filled with the current title and description, showing the epic's team as read-only (not an input). Submitting SHALL call `PUT /api/v1/epics/{id}` with title and description only; on success the dialog closes and the list shows the updated values. On failure (`400`) the problem `detail` SHALL render inside the open dialog.

#### Scenario: Successful edit
- **WHEN** the user changes the title and/or description and submits
- **THEN** the dialog closes and the list shows the updated epic under its unchanged team

#### Scenario: Team is not editable
- **WHEN** the edit dialog is open
- **THEN** the team renders as read-only text with no control to change it

### Requirement: Delete epic with confirmation
The frontend SHALL provide a delete action per epic behind a confirmation dialog calling `DELETE /api/v1/epics/{id}`. A `204` removes the epic from the list; a `409` (tickets reference the epic) SHALL render the backend problem `detail` inside the dialog without removing the epic. (UI-side disabling for ticket references is deferred to E14, when a ticket API exists to compute them from; the backend guard is authoritative.)

#### Scenario: Confirmed delete removes epic
- **WHEN** the user confirms deletion and the backend returns `204`
- **THEN** the dialog closes and the epic disappears from the list

#### Scenario: Reference conflict surfaced
- **WHEN** the confirmed delete returns `409`
- **THEN** the problem `detail` renders and the epic remains in the list

#### Scenario: Cancel keeps epic
- **WHEN** the user cancels the confirmation dialog
- **THEN** no delete request is issued and the epic remains
