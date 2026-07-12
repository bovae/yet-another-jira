# fe-board

## Purpose
Frontend Kanban board: render a selected team's board at `/` with five ordered workflow columns of labelled cards, offer a team selector reflected in the URL, filter cards by type, epic, and title search, drag-and-drop cards between columns to persist state changes optimistically, and create or open tickets from the board.

## Requirements

### Requirement: Board loads for a selected team
The frontend SHALL render the Kanban board at `/` for one selected team, fetched via `GET /api/v1/teams/{teamId}/board`. A team selector SHALL offer all teams; the selection SHALL be reflected in the URL so a reload restores the same board. When no team is explicitly selected, the first team SHALL be used. When no teams exist, the board SHALL prompt the user to create a team instead of rendering columns. The screen SHALL show the shared loading and error (with retry) states.

#### Scenario: Board renders for the selected team
- **WHEN** an authenticated user selects a team in the board's team selector
- **THEN** the board fetches and renders that team's board

#### Scenario: Selection survives reload
- **WHEN** the user selects a team and reloads the page
- **THEN** the same team's board renders without reselecting

#### Scenario: No teams yet
- **WHEN** the board opens while no teams exist
- **THEN** a prompt to create a team renders instead of board columns

#### Scenario: Fetch error with retry
- **WHEN** the board fetch fails
- **THEN** the shared error state renders and its retry control re-triggers the fetch

### Requirement: Five ordered columns with labelled cards
The board SHALL render exactly 5 columns in workflow order (`new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`), each titled with the human-readable state label and showing its card count. Every column SHALL render even when empty. Each card SHALL show the ticket title, its type as a label, and the epic title when the ticket has one. Cards SHALL render in the server-provided order (most recently modified first).

#### Scenario: Columns in workflow order
- **WHEN** the board renders for a team with tickets across several states
- **THEN** 5 columns render in workflow order with human-readable titles and each ticket appears in its state's column

#### Scenario: Empty columns still render
- **WHEN** a team has no tickets in some state
- **THEN** that state's column still renders, empty, in its workflow position

#### Scenario: Card content
- **WHEN** a ticket referencing an epic renders on the board
- **THEN** its card shows the title, the type label, and the epic title

### Requirement: Board filters
The board SHALL provide a ticket type filter, an epic filter scoped to the selected team, and a case-insensitive title search input. Filters SHALL be passed to the backend as `type`, `epicId`, and `q` query parameters (combined AND server-side); a cleared or blank control SHALL omit its parameter. Changing the selected team SHALL reset the epic filter. The 5-column structure SHALL be preserved under any filter combination.

#### Scenario: Type filter narrows the board
- **WHEN** the user picks a type in the type filter
- **THEN** the board refetches with `type` set and only matching cards render, still in 5 columns

#### Scenario: Combined filters
- **WHEN** the user sets a type, an epic, and a search term
- **THEN** the board request carries `type`, `epicId`, and `q` together

#### Scenario: Clearing a filter removes its parameter
- **WHEN** the user resets a filter to "All" or clears the search input
- **THEN** the subsequent board request omits that parameter

#### Scenario: Team change resets the epic filter
- **WHEN** an epic filter is active and the user switches teams
- **THEN** the epic filter resets and its options show the new team's epics

### Requirement: Drag-and-drop persists the state change
The board SHALL let the user drag a card from one column to another using `@dnd-kit`. Dropping on a different column SHALL immediately move the card to the top of the target column (optimistic) and persist the change via `PATCH /api/v1/tickets/{id}` with the target state. When the request fails, the card SHALL return to its previous column and an error SHALL be shown. Dropping a card on its own column SHALL issue no request.

#### Scenario: Successful drop persists
- **WHEN** the user drags a card to a different column and the PATCH succeeds
- **THEN** the card stays at the top of the target column and a board refetch reflects the new state

#### Scenario: Failed drop reverts
- **WHEN** the user drags a card to a different column and the PATCH fails
- **THEN** the card returns to its previous column and an error message renders

#### Scenario: Same-column drop is a no-op
- **WHEN** the user drops a card back on its own column
- **THEN** no state-change request is issued

### Requirement: Create and open tickets from the board
The board SHALL provide a create-ticket action opening the ticket form dialog with the team preset to the selected team; on success the board SHALL reflect the new ticket. Activating a card (click, not drag) SHALL navigate to that ticket's details view at `/tickets/{id}`.

#### Scenario: Create from the board
- **WHEN** the user creates a ticket from the board's create action
- **THEN** the dialog closes and the new ticket's card appears in its state's column

#### Scenario: Card opens details
- **WHEN** the user clicks a card without dragging it
- **THEN** the app navigates to `/tickets/{id}` for that ticket
