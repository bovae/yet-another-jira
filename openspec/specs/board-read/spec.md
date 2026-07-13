# board-read

## Purpose
Read-only Kanban board view for a team via `GET /api/v1/teams/{teamId}/board`. Returns the team's tickets grouped into exactly 5 state columns in workflow order, each card carrying ticket and optional epic summary data, ordered most-recently-modified first. Supports optional server-side filters (`type`, `epicId`, `q`) combined with AND, and requires authentication.

## Requirements

### Requirement: Board returns 5 state columns in workflow order
The system SHALL return the Kanban board for a team via `GET /api/v1/teams/{teamId}/board`. The response SHALL contain exactly 5 columns, one per ticket state, in workflow order: `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`. Every column SHALL be present even when it has no cards. The team MUST exist (`404` otherwise).

#### Scenario: Board with tickets in several states
- **WHEN** an authenticated user requests the board of a team having tickets across several states
- **THEN** the response is `200` with exactly 5 columns in workflow order, each ticket appearing as a card in the column matching its state

#### Scenario: Empty board
- **WHEN** an authenticated user requests the board of a team with no tickets
- **THEN** the response is `200` with exactly 5 columns in workflow order, all with empty card lists

#### Scenario: Unknown team
- **WHEN** the `teamId` does not reference an existing team
- **THEN** the response is `404` with an RFC 9457 problem detail

### Requirement: Card content
Each board card SHALL carry the ticket's id, title, and type. When the ticket references an epic, the card SHALL also carry the epic's id and title; otherwise both are absent/null.

#### Scenario: Card for a ticket with an epic
- **WHEN** a board ticket references an epic
- **THEN** its card contains the ticket id, title, type, and the epic's id and title

#### Scenario: Card for a ticket without an epic
- **WHEN** a board ticket references no epic
- **THEN** its card contains the ticket id, title, and type, with no epic id or title

### Requirement: Cards ordered most-recently-modified first
Within each column, cards SHALL be ordered by `modified_at` descending (most recently modified first), with ties on `modified_at` broken by id so the order is stable across reads.

#### Scenario: Recently modified ticket floats to the top
- **WHEN** a column holds several tickets and one of them is then updated (advancing its `modified_at`)
- **THEN** a subsequent board request returns that ticket as the first card of its column

#### Scenario: Equal timestamps keep a stable order
- **WHEN** two tickets in the same column share the same `modified_at`
- **THEN** repeated board requests return their cards in the same order

### Requirement: Board filters combined with AND
The system SHALL apply optional query parameters as filters, all combined with AND logic: `type` (ticket type code), `epicId` (epic reference), and `q` (case-insensitive substring match over ticket title). Filtering SHALL be performed server-side. The `type` value MUST be one of `bug|feature|fix` (`400` otherwise). An `epicId` matching no tickets SHALL yield empty columns, not an error. Wildcard characters in `q` (`%`, `_`, `\`) SHALL be treated as literal text. A blank or absent parameter SHALL NOT filter. Case folding for the title match SHALL be applied by a single engine to both the column and the search term (folded in the database), so non-ASCII titles match case-insensitively. The 5-column structure and per-column ordering are preserved under any filter combination.

#### Scenario: Filter by type
- **WHEN** the board is requested with `?type=bug`
- **THEN** the response contains only cards of type `bug`, still grouped into 5 ordered columns

#### Scenario: Filter by epic
- **WHEN** the board is requested with `?epicId={id}` of an epic referenced by some tickets
- **THEN** the response contains only cards of tickets referencing that epic

#### Scenario: Title search is case-insensitive substring
- **WHEN** the board is requested with `?q=LoGin` and a ticket titled `"Fix login flow"` exists on the team
- **THEN** that ticket's card is included

#### Scenario: Non-ASCII title matches case-insensitively
- **WHEN** the board is requested with `?q=übersicht` and a ticket titled `"Übersicht bauen"` exists on the team
- **THEN** that ticket's card is included

#### Scenario: Combined filters use AND
- **WHEN** the board is requested with `?type=bug&epicId={id}&q=login`
- **THEN** only cards matching the type AND the epic AND the title substring are returned

#### Scenario: Invalid type code rejected
- **WHEN** the board is requested with a `type` that is not one of `bug|feature|fix`
- **THEN** the response is `400` with an RFC 9457 problem detail

#### Scenario: Unknown epic filter yields empty board
- **WHEN** the board is requested with an `epicId` that matches no tickets of the team
- **THEN** the response is `200` with 5 columns, all empty

#### Scenario: Wildcards in search are literal
- **WHEN** the board is requested with `?q=100%` and tickets titled `"Reach 100% coverage"` and `"Reach 100 users"` exist
- **THEN** only the `"Reach 100% coverage"` card is returned

### Requirement: Authentication required
The board endpoint SHALL require a valid bearer token (enforced by the existing security configuration's authenticated-by-default rule for `/api/v1/**`).

#### Scenario: Unauthenticated request rejected
- **WHEN** the board endpoint is called without a valid token
- **THEN** the response is `401` with an RFC 9457 problem detail
