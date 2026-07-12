# Design — Comments

## Context

E5–E7 established the slice pattern this change copies: controller → `@Transactional` service → Spring Data repository, typed exceptions (`ValidationException`/`NotFoundException`) mapped centrally to RFC 9457 problems. Everything comment-shaped already exists from E0: the `Comment` entity (DB-generated id and `created_at`), `CommentRepository`, the `comments` table with `ticket_id … ON DELETE CASCADE`, and `CurrentUserProvider` for the author. Auth is E4's catch-all rule — no security change.

Comments are the simplest slice yet: two operations, no update, no delete, no delete guard, one validation rule beyond ticket existence.

## Goals / Non-Goals

**Goals:**
- Add + list endpoints nested under `/api/v1/tickets/{ticketId}/comments`.
- Server-authoritative body validation (trimmed non-empty, capped).
- Oldest-first ordering.
- Ticket `modified_at` provably untouched by commenting.

**Non-Goals:**
- Comment edit/delete (§14 stretch; immutability is the mandatory contract).
- Pagination (comment volume per ticket is small; the 100+-item surface is the E9 board).
- FE comment thread (E14).

## Decisions

### 1. Nested route, mirror the slice layout
`CommentService` in `com.bovae.yaj.comments`, `CommentController` in `web.controller` mapped at `/api/v1/tickets/{ticketId}/comments`, record DTOs in `web.dto`. The nested path (vs. a flat `/api/v1/comments?ticketId=`) matches the catalog's stated scope and makes the ticket-existence check natural on both operations. No new patterns.

### 2. Ticket existence checked on both add and list → `404`
`GET` on a deleted/unknown ticket must not return a misleading empty `200`, and `POST` must fail before the FK does. One `existsById` pre-check in the service, same convention as tickets' `requireTeam`.

### 3. `modified_at` untouched by construction
The service never loads or touches the `Ticket` entity beyond `existsById` — there is nothing to dirty, so `@UpdateTimestamp` cannot fire. No code is needed to *prevent* the bump; the BDD scenario pins the behavior against future regressions (e.g. someone adding a bidirectional `@OneToMany` with cascade later).

### 4. Body validation mirrors the ticket body
Trim, non-empty after trim, cap at 10000 characters — same constants and semantics as `TicketService.normalizeBody`, because the column is uncapped `text` and §9 requires rejecting oversized payloads. Bean Validation `@NotBlank`/`@Size` on the request DTO for structure, service-level trim for normalization.

### 5. Ordering in the query, not in memory
`findByTicketIdOrderByCreatedAtAsc(UUID)` on the existing `CommentRepository`. `created_at` is DB-set (`now()`), so insertion order and timestamp order agree per transaction; no secondary sort needed at this scale.

### 6. Immutability by omission
No `PUT`/`PATCH`/`DELETE` mappings exist for comments — the contract is the absence of the operations, not a runtime guard. Comment removal happens only via the ticket-delete cascade already specified in `tickets-crud`.

## Risks / Trade-offs

- [Two comments created in the same microsecond could tie on `created_at`, making order among them unstable] → Practically unreachable for human-authored comments; accepted. Add `id` as a tiebreaker only if it ever shows up.
- [Unpaginated list could grow large on a pathological ticket] → Same trade-off accepted for tickets/epics lists; revisit if E14 surfaces a need.

## Migration Plan

Additive endpoints only; no schema change, no data migration, no new dependencies. Deploy normally; rollback = revert the commit.

## Open Questions

None.
