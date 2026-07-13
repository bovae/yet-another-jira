# fix-board-ux-polish — Design

## Context

Six small defects across the board page, the shared dialog primitives, and the local seed migration. All are cosmetic/UX; none touch the API or schema. Root causes are already located:

1. **Clipped drop highlight** — `Column.tsx` renders the drop-target highlight as `ring-2 ring-link`. Rings draw *outside* the border box, and the board grid container (`overflow-x-auto` in `BoardPage.tsx`) clips vertical overflow, so the ring's top edge disappears for every column.
2. **Return-to-source drop animation** — the board's `DragOverlay` uses `@dnd-kit`'s default drop animation, which animates the overlay back to the *source* element's rect. Because the card has already been optimistically moved, the overlay flies to the old column, then the card "teleports" to the new one.
3. **Transparent modal backdrop** — `dialog.tsx` and `alert-dialog.tsx` overlays use shadcn's stock `bg-black/50`. The project resets the stock palette (`--color-*: initial` in `index.css`), so `bg-black` resolves to nothing and the overlay renders fully transparent.
4. **Columns stop short of viewport bottom** — `Column.tsx` uses `max-h-[calc(100vh-16rem)] min-h-30`: a cap, not a height, so columns shrink to content. User confirmed the intent: equal fixed height reaching the viewport bottom, with internal card scroll.
5. **Identical seed timestamps** — every row in `0009-local-seed-data.sql` uses `2026-07-01 09:00:00+00`, which looks fake and makes the board's modified-first card ordering meaningless.
6. **Search flicker** — each search/filter change produces a new React Query key with no cached data, so `boardQuery.isPending` becomes true and the whole board is replaced by `LoadingState` for the duration of the fetch.

## Goals / Non-Goals

**Goals:**
- Fix all six defects with the smallest diff that addresses the root cause of each.
- Stay entirely within existing DESIGN.md tokens and existing dependencies.

**Non-Goals:**
- No new drop-animation choreography (e.g. animating the overlay to its landing position) — instant landing is the accepted behavior.
- No fetching indicator during background board refetches — just keep the previous board visible.
- No general board-layout refactor (AppShell/main flex-height restructuring); reuse the existing viewport-offset approach.
- No evergreen (relative-to-now) seed dates.

## Decisions

### D1. Ring clipping → `ring-inset`
Add `ring-inset` to the column's `isOver` highlight so the ring draws inside the border box and cannot be clipped by any ancestor overflow. Alternative — padding the board container to leave room for the outset ring — changes layout on all four sides for a 2px decoration and still breaks if offsets change.

### D2. Drop animation → `dropAnimation={null}`
Set `dropAnimation={null}` on the board's `DragOverlay`. With optimistic column moves, the default animation's target rect (the source card) is always wrong; dnd-kit has no cheap way to learn the landing rect in the new column. Instant drop is the standard pattern for optimistic kanban boards. Alternative — custom `dropAnimation` measuring the landed card — significant code for a decorative effect.

### D3. Overlay backdrop → `bg-primary/50`
Replace `bg-black/50` with `bg-primary/50` in both `dialog.tsx` and `alert-dialog.tsx` overlays. `--color-primary` is the DESIGN.md ink-near-black token, so at 50% opacity it is visually equivalent to the shadcn intent while staying on-token. Alternative — re-adding `--color-black` to the theme — reintroduces an off-token color the palette reset deliberately removed.

### D4. Column height → fixed `h-[calc(100vh-16rem)]`
Change the column's `max-h-[calc(100vh-16rem)] min-h-30` to a fixed `h-[calc(100vh-16rem)]`. All columns become equal height and reach (approximately) the viewport bottom; the existing `overflow-y-auto` card list keeps long columns scrolling internally. The `16rem` offset already exists in the code and accounts for the shell header + board header; tune it during implementation if the visual gap at the bottom is off. Alternative — restructuring AppShell/BoardPage into a full-height flex chain (`h-screen` + `min-h-0`) — is the "correct" general solution but a cross-page layout refactor for a one-page need.

### D5. Seed timestamps → varied hardcoded values
Keep fixed UUIDs and hardcoded (deterministic) timestamps, but stagger them realistically: user/teams created earliest (mid-June 2026), epics a few days later, tickets spread over the following weeks with distinct `created_at` per row, `modified_at` ≥ `created_at` (further along the workflow ⇒ more recently modified reads naturally), and each comment after its ticket's creation. Distinct `modified_at` values also make the board's modified-first ordering visibly meaningful. Alternative — `now() - interval` expressions for evergreen freshness — makes the seed non-deterministic, which the local-seed-data spec explicitly requires.

**Checksum consequence:** the changeset already ran against existing local DBs; editing it fails Liquibase checksum validation on next start. Acceptable for a local-only seed — reset with `docker compose down -v`. Do **not** add `runOnChange` (re-running inserts would duplicate rows).

### D6. Search flicker → `placeholderData: keepPreviousData`
Add `placeholderData: keepPreviousData` (from `@tanstack/react-query`) to the board query. On a key change the previous board stays rendered and `isPending` stays false, so `LoadingState` only appears on the first-ever load. Trade-off: switching *teams* also shows the previous team's board for the fetch duration; accepted — fetches are fast, and scoping placeholder data to same-team key changes is complexity the problem doesn't warrant.

## Risks / Trade-offs

- [Stale board flash on team switch (D6)] → Accepted; noted above. Revisit only if users report confusion.
- [`16rem` offset drifts if header layout changes (D4)] → Single arbitrary value in one place; visually verify at implementation time.
- [Existing local DBs fail checksum validation after D5] → Documented reset (`docker compose down -v`); seed is local-only by context gate.
- [`ring-inset` overlaps 1px of column padding (D1)] → Cosmetically invisible at 2px on a 12px-padded column.
