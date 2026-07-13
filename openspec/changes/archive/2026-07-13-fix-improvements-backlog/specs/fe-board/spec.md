# fe-board

## ADDED Requirements

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
