# fe-comments

## MODIFIED Requirements

### Requirement: Comment thread on ticket details
The frontend SHALL render the ticket's comment thread on the details view, fetched via `GET /api/v1/tickets/{ticketId}/comments` and displayed chronologically oldest first, each comment showing its author and created-at (formatted from ISO-8601 UTC). The author SHALL render the email from the API's `authorEmail` field for every comment, regardless of who authored it. The thread SHALL show the shared loading, empty, and error states independently of the ticket fields.

#### Scenario: Comments render oldest first
- **WHEN** the ticket has several comments
- **THEN** they render in `createdAt` ascending order with author and timestamp

#### Scenario: Any author shows email
- **WHEN** a comment was authored by a different user than the one viewing it
- **THEN** the author renders as that user's email, not a raw UUID

#### Scenario: No comments yet
- **WHEN** the ticket has no comments
- **THEN** an empty-thread state renders while the ticket fields still display
