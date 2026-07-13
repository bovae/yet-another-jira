# fe-board — Delta

## MODIFIED Requirements

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
