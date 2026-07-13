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
The board SHALL render exactly 5 columns in workflow order (`new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`), each titled with the human-readable state label and showing its card count. Every column SHALL render even when empty. Columns SHALL render at an equal fixed height that extends to the bottom of the viewport, regardless of how many cards they contain; a column whose cards exceed that height SHALL scroll its card list internally. Each card SHALL show the ticket title, its type as a label, and the epic title when the ticket has one. Cards SHALL render in the server-provided order (most recently modified first).

#### Scenario: Columns in workflow order
- **WHEN** the board renders for a team with tickets across several states
- **THEN** 5 columns render in workflow order with human-readable titles and each ticket appears in its state's column

#### Scenario: Empty columns still render
- **WHEN** a team has no tickets in some state
- **THEN** that state's column still renders, empty, in its workflow position

#### Scenario: Columns fill the viewport height
- **WHEN** the board renders with columns holding differing numbers of cards, including empty ones
- **THEN** all columns render at the same height, reaching the bottom of the viewport, and a column with more cards than fit scrolls internally

#### Scenario: Card content
- **WHEN** a ticket referencing an epic renders on the board
- **THEN** its card shows the title, the type label, and the epic title

### Requirement: Board filters
The board SHALL provide a ticket type filter, an epic filter scoped to the selected team, and a case-insensitive title search input. Filters SHALL be passed to the backend as `type`, `epicId`, and `q` query parameters (combined AND server-side); a cleared or blank control SHALL omit its parameter. Changing the selected team SHALL reset the epic filter. The 5-column structure SHALL be preserved under any filter combination. While a filter or search change is refetching the board, the previously rendered board SHALL remain visible until the new data arrives — the full-board loading state SHALL appear only on the initial board load, never on a refetch.

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

#### Scenario: Refetch keeps the board visible
- **WHEN** the user types in the title search (or changes any filter) while a board is already rendered
- **THEN** the current board stays visible during the refetch and is replaced by the filtered board when the response arrives, with no intermediate loading state

### Requirement: Drag-and-drop persists the state change
The board SHALL let the user drag a card from one column to another using `@dnd-kit`. While a drag is active, the dragged card SHALL be rendered in a `DragOverlay` so it remains fully visible anywhere over the board — including outside its source column — and the source card SHALL remain in place as a visually dimmed placeholder. The column currently hovered as a drop target SHALL show a highlight that is fully visible on all four sides, not clipped by surrounding layout. Dropping on a different column SHALL immediately move the card to the top of the target column (optimistic) and persist the change via `PATCH /api/v1/tickets/{id}` with the target state; on release the card SHALL appear in the target column directly, without an animation returning it toward its source column. When the request fails, the card SHALL return to its previous column and an error SHALL be shown. Dropping a card on its own column SHALL issue no request.

#### Scenario: Dragged card stays visible outside its source column
- **WHEN** the user drags a card and moves the pointer beyond the source column's bounds
- **THEN** the card preview follows the pointer unclipped, rendered above all columns, while the source card shows as a dimmed placeholder

#### Scenario: Hovered drop target is fully highlighted
- **WHEN** the user drags a card over any column, including while the board is scrolled
- **THEN** that column shows its drop-target highlight on all four sides, including the top edge

#### Scenario: Drop lands without a return animation
- **WHEN** the user releases a card over a different column
- **THEN** the card renders at the top of the target column immediately, with no transitional animation moving it back toward the source column

#### Scenario: Cancelled drag leaves the board unchanged
- **WHEN** the user cancels a drag (e.g. Escape) or drops outside any column
- **THEN** the overlay disappears, the source card returns to full visibility, and no state-change request is issued

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

### Requirement: A persisted move updates every cached view
After a drag-and-drop state change settles, the frontend SHALL invalidate all cached board variants of the affected team (every filter/search combination), the ticket list cache, and the moved ticket's detail cache — so no view can serve the pre-move state afterwards.

#### Scenario: Other filter combos reflect the move
- **WHEN** a card is moved while another filter combination of the same team's board sits in the cache, and the user then switches to that combination
- **THEN** the board shown places the card in its new column, not the pre-move one

#### Scenario: Ticket views reflect the move
- **WHEN** a card is moved and the user then opens the ticket list or the ticket's details
- **THEN** the displayed state is the post-move state

### Requirement: Cards are keyboard-openable
Each board card SHALL offer a keyboard-focusable control (the card title as a link) that opens the ticket details at `/tickets/{id}` with Enter — independent of the drag-and-drop keyboard sensor. Keyboard and screen-reader users MUST be able to open a ticket from the board without using the ticket list as a detour.

#### Scenario: Enter opens the ticket
- **WHEN** a keyboard user tabs to a card's title and presses Enter
- **THEN** the app navigates to that ticket's details view

### Requirement: Column card counts are screen-reader accessible
Each column's card count SHALL be exposed to assistive technology (e.g. folded into the column's accessible name), not hidden behind `aria-hidden` with no alternative.

#### Scenario: Count in the accessible name
- **WHEN** a screen reader reads a board column heading
- **THEN** the announced name includes the column label and its card count

### Requirement: Search input follows the URL
The board's title-search input SHALL stay in sync with the `q` URL parameter when it changes outside the input — browser back/forward or navigation links. The input MUST NOT show a stale term over results filtered by a different one.

#### Scenario: Back navigation updates the input
- **WHEN** the user searches, navigates, then uses browser back so the URL's `q` changes
- **THEN** the input shows the `q` value from the URL and the results match it

### Requirement: Move errors clear on context change
The board's move-failure error SHALL clear when the user switches team or changes any filter — it MUST NOT persist over a fresh, unrelated board.

#### Scenario: Team switch clears the error
- **WHEN** a move fails and its error shows, and the user then switches to another team
- **THEN** the error is no longer rendered
