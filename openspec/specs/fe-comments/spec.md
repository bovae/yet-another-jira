# fe-comments

## Purpose
Frontend comment thread on the ticket details view: render a ticket's comments chronologically (oldest first) with author and timestamp, and add new comments. Comments are immutable in the mandatory scope (no edit or delete). The thread resolves the current user's email for own comments and surfaces RFC 9457 problem details near the add-comment form.

## Requirements

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

### Requirement: Add comment
The frontend SHALL provide an add-comment form under the thread with a required body field, submitting via `POST /api/v1/tickets/{ticketId}/comments`. On success the form clears and the new comment appears at the end of the thread. On failure the problem `detail` SHALL render near the form with the entered body preserved. The form SHALL NOT submit an empty or whitespace-only body.

#### Scenario: Comment appended
- **WHEN** the user enters a body and submits
- **THEN** the form clears and the comment appears at the end of the thread

#### Scenario: Blank body not submitted
- **WHEN** the body is empty or whitespace-only
- **THEN** no request is issued and the form indicates the body is required

#### Scenario: Server error surfaced
- **WHEN** the backend rejects the comment
- **THEN** the problem `detail` renders and the entered body is preserved

### Requirement: No comment edit or delete controls
The frontend SHALL NOT render edit or delete controls for comments — comments are immutable after creation in the mandatory scope.

#### Scenario: Thread is append-only
- **WHEN** the comment thread renders
- **THEN** no edit or delete control appears on any comment
