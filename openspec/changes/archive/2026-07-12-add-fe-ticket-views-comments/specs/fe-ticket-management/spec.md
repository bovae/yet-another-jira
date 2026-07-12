# fe-ticket-management

## ADDED Requirements

### Requirement: Ticket list screen with team filter
The frontend SHALL render a ticket list screen at `/tickets` inside the app shell, listing tickets with title, type label, state label, team name (resolved from the teams list), and epic title when set (resolved from the epics list). By default all tickets load via `GET /api/v1/tickets`; a team filter Select narrows the list via `GET /api/v1/tickets?teamId={id}`. Each row SHALL open the ticket's details view. The screen SHALL show the shared loading, empty, and error (with retry) states.

#### Scenario: All tickets listed by default
- **WHEN** an authenticated user opens `/tickets` and tickets exist under several teams
- **THEN** every ticket renders with its title, type label, state label, and team name

#### Scenario: Filter narrows to one team
- **WHEN** the user selects a team in the filter
- **THEN** only tickets whose `teamId` matches render

#### Scenario: Row opens details
- **WHEN** the user activates a ticket row
- **THEN** the app navigates to `/tickets/{id}` for that ticket

#### Scenario: Empty state
- **WHEN** the (possibly filtered) list contains no tickets
- **THEN** the shared empty state renders

#### Scenario: Fetch error with retry
- **WHEN** the tickets fetch fails
- **THEN** the shared error state renders and its retry control re-triggers the fetch

### Requirement: Human-readable enum labels
The frontend SHALL display ticket types and states as human-readable labels while sending only the canonical API codes (`bug|feature|fix`; `new|ready_for_implementation|in_progress|ready_for_acceptance|done`). State labels SHALL replace underscores with spaces (e.g. `ready_for_implementation` → "Ready for implementation"), and states SHALL be presented in workflow order.

#### Scenario: State code rendered as label
- **WHEN** a ticket in state `ready_for_implementation` renders anywhere (list, details, form Select)
- **THEN** the UI shows a spaced human-readable label, never the raw code

#### Scenario: Canonical code submitted
- **WHEN** the user picks a type or state label in a form and submits
- **THEN** the request body carries the canonical API code

### Requirement: Create ticket
The frontend SHALL provide a create-ticket action on the ticket list opening a dialog with required team, type, and state Selects, an epic Select scoped to the chosen team (with a "None" choice), a required title field, and a required body field. Submitting SHALL call `POST /api/v1/tickets`; "None" epic omits `epicId`. On success the dialog closes and the list reflects the new ticket. On failure the problem `detail` SHALL render inside the open dialog with entered values preserved. When no teams exist, the create path SHALL instead prompt the user to create a team first.

#### Scenario: Successful create
- **WHEN** the user picks a team, type, and state, enters a title and body, and submits
- **THEN** the dialog closes and the new ticket appears in the list

#### Scenario: Create with epic
- **WHEN** the user picks an epic from the selected team's epic list and submits
- **THEN** the request carries that `epicId` and the created ticket shows the epic

#### Scenario: Validation error surfaced
- **WHEN** the backend rejects the create with `400`
- **THEN** the dialog stays open showing the problem `detail` with entered values preserved

#### Scenario: No teams yet
- **WHEN** the user invokes create-ticket while no teams exist
- **THEN** a prompt to create a team first renders instead of the ticket form

### Requirement: Epic select scoped to the chosen team
The ticket form's epic Select SHALL offer only epics belonging to the currently selected team (fetched via `GET /api/v1/epics?teamId={id}`), plus a "None" choice. When the user changes the ticket's team, any selected epic SHALL be reset to "None", and the epic options SHALL reload for the new team.

#### Scenario: Options match the selected team
- **WHEN** a team is selected in the ticket form
- **THEN** the epic Select offers exactly that team's epics plus "None"

#### Scenario: Team change clears the selected epic
- **WHEN** an epic is selected and the user switches the form's team
- **THEN** the epic selection resets to "None" and the options show the new team's epics

#### Scenario: Team without epics
- **WHEN** the selected team has no epics
- **THEN** the epic Select offers only "None"

### Requirement: Ticket details view
The frontend SHALL render a details view at `/tickets/{id}` showing all ticket fields: title, body, type label, state label, team name, epic title (or none), created-by, created-at, and modified-at (timestamps formatted from ISO-8601 UTC). Created-by SHALL render the current user's email when the id matches the authenticated user, otherwise the raw user id. The view SHALL show the shared loading and error states, and a not-found state for an unknown ticket id.

#### Scenario: All fields displayed
- **WHEN** an authenticated user opens `/tickets/{id}` for an existing ticket
- **THEN** the title, body, type, state, team, epic, created-by, created-at, and modified-at all render

#### Scenario: Own ticket shows email
- **WHEN** the ticket's `createdBy` equals the authenticated user's id
- **THEN** created-by renders the user's email

#### Scenario: Unknown ticket
- **WHEN** the ticket fetch returns `404`
- **THEN** a not-found state renders (no crash)

### Requirement: Edit ticket
The frontend SHALL provide an edit action on the details view opening the ticket form dialog pre-filled with current values, including the team (editable). Submitting SHALL call `PUT /api/v1/tickets/{id}` with the full field set ("None" epic omits `epicId`, clearing any stored epic). On success the dialog closes and the details view shows the updated values. On failure the problem `detail` SHALL render inside the open dialog.

#### Scenario: Successful edit
- **WHEN** the user changes fields and submits
- **THEN** the dialog closes and the details view shows the updated values

#### Scenario: Team change on edit clears epic
- **WHEN** the user switches the ticket's team in the edit form
- **THEN** the epic selection resets to "None" and submitting persists the new team with no epic

#### Scenario: Validation error surfaced
- **WHEN** the backend rejects the update with `400`
- **THEN** the dialog stays open showing the problem `detail`

### Requirement: Delete ticket with confirmation
The frontend SHALL provide a delete action on the details view behind a confirmation dialog calling `DELETE /api/v1/tickets/{id}`. On `204` the app SHALL navigate back to the ticket list. Cancelling SHALL issue no request.

#### Scenario: Confirmed delete navigates to list
- **WHEN** the user confirms deletion and the backend returns `204`
- **THEN** the app navigates to `/tickets` and the ticket no longer appears

#### Scenario: Cancel keeps ticket
- **WHEN** the user cancels the confirmation dialog
- **THEN** no delete request is issued and the details view remains
