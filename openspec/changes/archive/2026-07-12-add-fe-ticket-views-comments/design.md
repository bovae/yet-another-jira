# Design — FE Ticket Views + Comments (E14)

## Context

E7 (`/api/v1/tickets`) and E8 (`/api/v1/tickets/{ticketId}/comments`) contracts are live and spec'd (`openspec/specs/tickets-crud`, `openspec/specs/comments`). The FE has the full E13 toolkit: `apiFetch` + `problemError`, TanStack Query, shadcn Dialog/AlertDialog/Select re-themed to `DESIGN.md`, and the `EpicsPage` pattern (list + filter Select + create/edit Dialogs + delete AlertDialog). `/tickets/:id` is a routing placeholder; the board is still the mock (E15 replaces it).

## Goals / Non-Goals

**Goals:**
- Ticket list (entry point), create/edit modal, details view, delete-with-confirm.
- Comment thread (list oldest-first + add) on the details view.
- Vitest coverage per the E14 DoD: team-change clears epic, comment add appends, required-field validation surfaced.

**Non-Goals:**
- Board columns, filters, DnD (E15). No backend changes. No comment edit/delete (stretch). No user-lookup UI beyond what `/auth/me` gives.

## Decisions

**D1 — `/tickets` list page as the create/open entry point.** The board (E15) will be the real entry point, but it's still the mock. A team-filterable table mirroring `EpicsPage` (team Select filter → `listTickets(teamId?)`, row click → details, "New ticket" button) is the cheapest working entry and reuses the whole E13 pattern. Alternative — bolt a create button onto the mock board — rejected: mock gets deleted in E15, the list survives as a secondary view.

**D2 — one `TicketFormDialog` for create and edit.** Unlike epics, a ticket's team IS editable, so create and edit have identical fields (team, type, state, epic, title, body) — one form component, two thin wrappers wiring `createTicket`/`updateTicket`. Selects: type (3 codes), state (5 codes), team (from cached teams query), epic (scoped, D3).

**D3 — team-scoped epic select; team change resets epic.** Epic options come from `listEpics(selectedTeamId)` (existing API module), queried per selected team. Changing the team sets `epicId` back to "none" — satisfying "changing team clears/replaces the selected epic" — and the fresh options list makes cross-team selection impossible client-side (server still enforces). Epic is optional: a "None" item maps to omitting `epicId` (PUT full-replacement then clears it server-side).

**D4 — canonical codes + label map in `api/tickets.ts`.** The API speaks `ready_for_implementation`; the UI shows "Ready for implementation". One exported `TICKET_STATES` / `TICKET_TYPES` const (code → label, in workflow order) lives beside the API module so E15's board columns reuse it. No i18n machinery.

**D5 — details page owns two queries + three mutations.** `TicketDetailsPage` runs `ticket(id)` and `comments(ticketId)` queries; edit (opens D2's dialog), delete (AlertDialog → `deleteTicket` → navigate to `/tickets`), add-comment (form → `addComment` → invalidate comments query — server returns oldest-first, so invalidation appends naturally). Metadata row shows type, state, team, epic, created-at / modified-at (formatted from ISO-8601), created-by.

**D6 — created-by / comment authors render the user id, with the current user's email substituted.** The backend exposes only user UUIDs (`createdBy`, `authorId`) and there is no user-lookup endpoint; the auth context has `{id, email}` for the current user only. When an id matches `me`, show the email; otherwise show the raw id in mono. Known ceiling — a batch user-lookup endpoint is a backend change out of E14's scope.

**D7 — API modules mirror `epics.ts` exactly.** `fe/src/api/tickets.ts` (list/get/create/update/delete + `TicketResponse` runtime parse) and `fe/src/api/comments.ts` (list/add). Typed responses, runtime field checks, `problemError` on non-2xx, `ApiError` re-export. `PATCH` state-change is deferred to E15 (its only consumer is DnD).

**D8 — routing/nav.** `App.tsx`: `/tickets` → `TicketsPage`, `/tickets/:id` → `TicketDetailsPage` (placeholder removed), both inside `RequireAuth`/`AppShell`. AppShell nav gains a "Tickets" link.

**D9 — retrofit reference-aware delete-disable on the E13 screens.** The `fe-epic-management` spec explicitly deferred ticket-based disable to E14. With `listTickets()` available: `EpicsPage` disables an epic's delete when any ticket's `epicId` matches; `TeamsPage` extends its existing epic-based check with tickets grouped by `teamId`. Same pattern the team screen already uses (one list query, client-side grouping); the backend `409` stays authoritative for races.

## Risks / Trade-offs

- [Raw UUIDs for other users' identity (D6)] → acceptable for QA scope; upgrade path is a BE user-lookup endpoint, not FE work.
- [`/tickets` list unpaginated] → contract has no paging; 100+ tickets renders fine as rows. Board perf is E15's concern.
- [Epic options query per team switch] → tiny payloads, cached by TanStack Query per `teamId` key; no debounce needed.
