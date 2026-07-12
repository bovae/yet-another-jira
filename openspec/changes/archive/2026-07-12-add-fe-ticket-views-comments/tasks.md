# Tasks — FE Ticket Views + Comments (E14)

## 1. API modules

- [x] 1.1 `fe/src/api/tickets.ts`: `TicketResponse` + runtime parse, `listTickets(teamId?)`, `getTicket(id)`, `createTicket`, `updateTicket`, `deleteTicket`, exported `TICKET_TYPES` / `TICKET_STATES` code→label consts in workflow order (mirror `epics.ts` shape; D4, D7)
- [x] 1.2 `fe/src/api/tickets.test.ts`: Vitest per the `epics.test.ts` pattern (happy paths, problem-detail errors, parse rejection)
- [x] 1.3 `fe/src/api/comments.ts`: `CommentResponse` + runtime parse, `listComments(ticketId)`, `addComment(ticketId, body)` (D7)
- [x] 1.4 `fe/src/api/comments.test.ts`: Vitest coverage as above

## 2. Ticket form dialog

- [x] 2.1 `TicketFormDialog` shared component: team/type/state Selects (labels from D4), team-scoped epic Select with "None" (epics query keyed by selected team), title + body fields, problem `detail` rendering, values preserved on error, no-teams prompt (D2, D3)
- [x] 2.2 Team-change behavior: switching team resets epic to "None" and reloads epic options for the new team
- [x] 2.3 Vitest: team change clears epic; epic options match selected team; validation error surfaced with values preserved; "None" epic omits `epicId`

## 3. Tickets list page

- [x] 3.1 `TicketsPage` at `/tickets`: table with title/type/state/team/epic, team filter Select, row → details navigation, "New ticket" button opening the create dialog, shared loading/empty/error states (D1)
- [x] 3.2 Vitest: default list renders, team filter narrows, empty state, error+retry, create flow appends

## 4. Ticket details page

- [x] 4.1 `TicketDetailsPage` at `/tickets/:id`: all fields + metadata (created-by via me-email-else-id rule D6, formatted timestamps), not-found state on 404, shared loading/error states (D5)
- [x] 4.2 Edit action reusing `TicketFormDialog` pre-filled (team editable); success updates the view
- [x] 4.3 Delete action behind AlertDialog → `DELETE` → navigate to `/tickets`; cancel issues no request
- [x] 4.4 Vitest: fields render, own-ticket shows email, unknown ticket → not-found, edit updates, confirmed delete navigates, cancel keeps

## 5. Comment thread

- [x] 5.1 `CommentThread` on the details page: oldest-first list with author (D6 rule) + timestamp, independent loading/empty/error states, no edit/delete controls
- [x] 5.2 Add-comment form: required body (blank not submitted), POST → invalidate comments query → form clears, problem `detail` near form with body preserved
- [x] 5.3 Vitest: oldest-first order, comment add appends, blank body blocked, server error surfaced with body preserved

## 6. Routing + nav

- [x] 6.1 `App.tsx`: add `/tickets` route, replace the `/tickets/:id` placeholder with `TicketDetailsPage`; drop the `Placeholder` import if now unused (D8)
- [x] 6.2 `AppShell`: add "Tickets" NavLink
- [x] 6.3 Update `App.test.tsx` / `AppShell.test.tsx` for the new routes and nav link

## 7. E13 delete-disable retrofit

- [x] 7.1 `EpicsPage`: disable epic delete when any ticket's `epicId` matches, with referenced message (D9)
- [x] 7.2 `TeamsPage`: extend delete-disable to also check tickets grouped by `teamId` (D9)
- [x] 7.3 Vitest: epic delete disabled/enabled by ticket references; team delete disabled by tickets-only reference

## 8. Gate

- [x] 8.1 `make fmt && make fe-lint && make fe-test` green
