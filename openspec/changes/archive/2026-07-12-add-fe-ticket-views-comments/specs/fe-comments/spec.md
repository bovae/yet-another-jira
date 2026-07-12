# fe-comments

## ADDED Requirements

### Requirement: Comment thread on ticket details
The frontend SHALL render the ticket's comment thread on the details view, fetched via `GET /api/v1/tickets/{ticketId}/comments` and displayed chronologically oldest first, each comment showing its author and created-at (formatted from ISO-8601 UTC). The author SHALL render the current user's email when the `authorId` matches the authenticated user, otherwise the raw user id. The thread SHALL show the shared loading, empty, and error states independently of the ticket fields.

#### Scenario: Comments render oldest first
- **WHEN** the ticket has several comments
- **THEN** they render in `createdAt` ascending order with author and timestamp

#### Scenario: Own comment shows email
- **WHEN** a comment's `authorId` equals the authenticated user's id
- **THEN** the author renders as the user's email

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
