# Comments (E8)

## Why

Tickets (E7) landed, making comments the next batch on the plan (`requirements/epics-catalog.md`, Batch 8). The comment API is the last backend contract E14 (FE ticket views + comments) needs before the ticket-details screen can be built.

## What Changes

- New endpoints: `GET/POST /api/v1/tickets/{ticketId}/comments` (authenticated, like all `/api/v1/**`).
- Add comment: non-empty `body` (server-authoritative validation), `author` set from the authenticated user, `created_at` server-set UTC. Comments are immutable after creation — no update/delete endpoints (edit/delete are §14 stretch, out of scope).
- List comments: chronological, oldest first.
- **Adding a comment does NOT advance the ticket's `modified_at`** — so commenting never reorders the board (§7).

## Capabilities

### New Capabilities

- `comments`: comment lifecycle — add and list under `/api/v1/tickets/{ticketId}/comments`, body validation, author from the authenticated user, oldest-first ordering, ticket `modified_at` untouched.

### Modified Capabilities

None — `tickets-crud` already specifies the comment-cascading delete; this change only starts creating the rows that cascade.

## Impact

- **Backend:** new `com.bovae.yaj.comments` slice (service), `web.controller`/`web.dto` additions, `CommentRepository` query addition (list by ticket, ordered). Entity `Comment`, `CommentRepository`, the `comments` table with its `ON DELETE CASCADE` FK, and `CurrentUserProvider` all exist from E0 — no schema change, no new dependencies.
- **Tests:** service unit tests (JaCoCo 90/90) + Cucumber BDD flow (add two comments → oldest-first → ticket `modified_at` unchanged).
- **Downstream:** unblocks E14 (FE ticket views + comments).
