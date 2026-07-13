# fe-epic-management

## MODIFIED Requirements

### Requirement: Delete epic with confirmation
The frontend SHALL provide a delete action per epic behind a confirmation dialog calling `DELETE /api/v1/epics/{id}`. The delete control SHALL be disabled, with a clear message that the epic is referenced, when any ticket references the epic (computed from `GET /api/v1/tickets` grouped by `epicId`). When enabled and confirmed, a `204` removes the epic from the list; a `409` (reference created concurrently) SHALL render the backend problem `detail` inside the dialog without removing the epic — the backend guard stays authoritative.

#### Scenario: Delete disabled for referenced epic
- **WHEN** the tickets list contains at least one ticket whose `epicId` is the epic's id
- **THEN** the epic's delete control is disabled and a clear referenced-epic message is available

#### Scenario: Delete enabled for unreferenced epic
- **WHEN** no ticket references the epic
- **THEN** the delete control is enabled

#### Scenario: Confirmed delete removes epic
- **WHEN** the user confirms deletion and the backend returns `204`
- **THEN** the dialog closes and the epic disappears from the list

#### Scenario: Reference conflict surfaced
- **WHEN** the confirmed delete returns `409`
- **THEN** the problem `detail` renders and the epic remains in the list

#### Scenario: Cancel keeps epic
- **WHEN** the user cancels the confirmation dialog
- **THEN** no delete request is issued and the epic remains
