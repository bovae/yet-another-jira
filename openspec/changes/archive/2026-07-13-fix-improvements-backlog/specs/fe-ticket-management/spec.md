# fe-ticket-management

## ADDED Requirements

### Requirement: Ticket deletion propagates to cached views
After a confirmed ticket delete succeeds, the frontend SHALL invalidate the ticket list and all cached board views and remove the deleted ticket's detail cache — a previously viewed board MUST NOT keep showing the deleted card.

#### Scenario: Deleted card gone from a warm board
- **WHEN** a user views the board, opens a card, deletes the ticket, and navigates back to the board within the cache staleness window
- **THEN** the deleted ticket's card no longer renders

## MODIFIED Requirements

### Requirement: Ticket details view
The frontend SHALL render a details view at `/tickets/{id}` showing all ticket fields: title, body, type label, state label, team name, epic title (or none), created-by, created-at, and modified-at (timestamps formatted from ISO-8601 UTC). Created-by SHALL render the creator's email from the API's `createdByEmail` field for every ticket, regardless of who created it. While the epics lookup is still pending, the epic SHALL show a neutral placeholder; "Unknown epic" SHALL appear only after the lookup resolves without a match. The view SHALL show the shared loading and error states, and a not-found state for an unknown ticket id.

#### Scenario: All fields displayed
- **WHEN** an authenticated user opens `/tickets/{id}` for an existing ticket
- **THEN** the title, body, type, state, team, epic, created-by, created-at, and modified-at all render

#### Scenario: Creator shown as email for any user
- **WHEN** the ticket was created by a different user than the one viewing it
- **THEN** created-by renders that user's email, not a raw UUID

#### Scenario: Epic label while epics load
- **WHEN** the ticket references an epic and the epics lookup is still pending
- **THEN** a neutral placeholder renders instead of "Unknown epic"

#### Scenario: Unknown ticket
- **WHEN** the ticket fetch returns `404`
- **THEN** a not-found state renders (no crash)
