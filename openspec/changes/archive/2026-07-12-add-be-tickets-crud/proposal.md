# Tickets CRUD + validation + `modified_at` (E7)

## Why

Tickets are the core entity of the tracker — every remaining epic (comments E8, board read E9, drag-and-drop E10, FE ticket views E14, FE board E15) depends on their API. Teams (E5) and epics (E6) are done, so E7 is the next batch on the critical path (`requirements/epics-catalog.md`, Batch 7).

## What Changes

- New endpoints: `GET/POST /api/v1/tickets`, `GET/PUT/PATCH/DELETE /api/v1/tickets/{id}` (authenticated, like all `/api/v1/**`).
- Create/edit with server-authoritative validation: required existing `team`, `type` (`bug|feature|fix`), `state` (5 canonical codes), optional `epic`, trimmed non-empty `title`, non-empty `body`.
- **Same-team epic rule:** a ticket's epic must belong to the ticket's team; rejected server-side on create and update (including team changes that would orphan the epic reference).
- `created_by` set from the authenticated user; `created_at`/`modified_at` server-set UTC.
- **`modified_at` semantics:** advances only on an actual field/state change; saving unchanged values does not advance it.
- `PATCH /api/v1/tickets/{id}` as the lightweight state-change endpoint (drag-and-drop contract for E10) — idempotent, persists immediately.
- Delete removes the ticket and its comments (existing DB `ON DELETE CASCADE`).

## Capabilities

### New Capabilities

- `tickets-crud`: ticket lifecycle — list/create/get/update/state-change/delete under `/api/v1/tickets`, field and reference validation, same-team epic rule, `modified_at` change-only semantics, comment-cascading delete.

### Modified Capabilities

None — `teams-crud` and `epics-crud` delete guards already specify the `409`-when-referenced behavior; this change only starts creating the referencing rows.

## Impact

- **Backend:** new `com.bovae.yaj.tickets` slice (service), `web.controller`/`web.dto` additions, `TicketRepository` query additions (list by team). Entity `Ticket`, enums `TicketType`/`TicketState`, exception→RFC 9457 mapping, and `CurrentUserProvider` all exist — no schema change, no new dependencies.
- **Tests:** service unit tests (JaCoCo 90/90) + Cucumber BDD flow (create → edit → state change → `modified_at` semantics → delete).
- **Downstream:** unblocks E8 (comments), E9 (board read), E10 (DnD persistence), E14 (FE ticket views).
