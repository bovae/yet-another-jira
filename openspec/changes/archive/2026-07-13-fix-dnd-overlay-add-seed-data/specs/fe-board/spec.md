# fe-board — Delta

## MODIFIED Requirements

### Requirement: Drag-and-drop persists the state change
The board SHALL let the user drag a card from one column to another using `@dnd-kit`. While a drag is active, the dragged card SHALL be rendered in a `DragOverlay` so it remains fully visible anywhere over the board — including outside its source column — and the source card SHALL remain in place as a visually dimmed placeholder. Dropping on a different column SHALL immediately move the card to the top of the target column (optimistic) and persist the change via `PATCH /api/v1/tickets/{id}` with the target state. When the request fails, the card SHALL return to its previous column and an error SHALL be shown. Dropping a card on its own column SHALL issue no request.

#### Scenario: Dragged card stays visible outside its source column
- **WHEN** the user drags a card and moves the pointer beyond the source column's bounds
- **THEN** the card preview follows the pointer unclipped, rendered above all columns, while the source card shows as a dimmed placeholder

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
