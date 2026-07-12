# fe-team-management

## MODIFIED Requirements

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
