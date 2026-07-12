# FE Ticket Views + Comments (E14)

## Why

The backend ticket CRUD (E7) and comments (E8) contracts are live, but the frontend has no way to create, view, edit, or discuss tickets — `/tickets/:id` is still a placeholder. E14 closes that gap and is the last prerequisite before the real board (E15).

## What Changes

- Ticket **details view** at `/tickets/:id`: all fields (type, team, epic, title, body, state, created-by, created-at, modified-at) plus the comment thread.
- Ticket **create/edit modal** (shadcn Dialog) with type/state/epic Selects, team select, title and body inputs. Changing team clears/replaces the selected epic (epic dropdown scoped to the chosen team). Server validation errors surfaced.
- Ticket **delete** behind a confirm AlertDialog; on success navigate away from details.
- **Comment thread** on the details view: list oldest-first, add-comment form; adding appends without reordering anything.
- A **tickets list screen** at `/tickets` (team-filterable) as the create/open entry point until the real board (E15) lands.
- `/tickets/:id` stops rendering the routing placeholder.

## Capabilities

### New Capabilities

- `fe-ticket-management`: ticket list, create/edit modal (team-scoped epic select, team-change clears epic), details view with metadata, delete with confirmation.
- `fe-comments`: comment thread on the ticket details view — chronological list, add-comment form, empty/error states.

### Modified Capabilities

- `fe-routing`: `/tickets/:id` renders the real details screen (no longer a placeholder); add `/tickets` list route to the guarded route table.
- `fe-epic-management`: epic delete control becomes reference-aware — disabled when tickets reference the epic (explicitly deferred to E14 by the E13 spec, now unblocked by the tickets API).
- `fe-team-management`: team delete disable now also accounts for tickets, not just epics (mirrors the full backend guard).

## Impact

- FE only — no backend changes; consumes existing `/api/v1/tickets*` and `/api/v1/tickets/{ticketId}/comments` contracts.
- New: `fe/src/api/tickets.ts`, `fe/src/api/comments.ts`, ticket pages/components + Vitest.
- Touched: `App.tsx` route table (replace placeholder), `AppShell` nav, `EpicsPage`/`TeamsPage` delete-disable logic.
- No new dependencies — shadcn primitives (Dialog, AlertDialog, Select) already in place from E13.
