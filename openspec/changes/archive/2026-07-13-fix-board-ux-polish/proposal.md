# fix-board-ux-polish

## Why

Six small UX defects ship with the current board and dialogs: the drop-target highlight is clipped at the top, a dropped card animates back to its source column before snapping to the target, modal backdrops render fully transparent (the `bg-black/50` utility resolves to nothing under the Tailwind palette reset), columns stop short of the viewport bottom, the board flickers to a full-page loader on every search keystroke, and the local seed data timestamps are all the identical instant, which looks fake and makes "recently modified" ordering meaningless.

## What Changes

- Board column drop-target highlight renders fully on all four sides (today the top edge is clipped by the board container's overflow).
- Dropping a card lands it in the target column instantly — no return-to-source drop animation.
- Dialog and alert-dialog backdrops actually dim the page behind the modal.
- Board columns extend to the bottom of the viewport at a fixed equal height; long columns keep scrolling internally (confirmed with user: viewport-fixed, not min-height).
- Board refetches triggered by filter/search changes keep the previous board rendered until fresh data arrives — no loading-state flash.
- Local seed migration uses varied, realistic timestamps (staggered creation dates, `modified_at` ≥ `created_at`, comments after their tickets) instead of one hardcoded instant for every row.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `fe-board`: drop-target highlight fully visible; drop lands without a return-to-source animation; columns fill viewport height; filter/search refetches keep the previous board visible instead of flashing the loading state.
- `fe-design-primitives`: modal overlays must visibly dim the underlying page.
- `local-seed-data`: seeded timestamps must be varied and internally consistent (not one identical instant).

## Impact

- `fe/src/pages/BoardPage.tsx` — board query placeholder data, `DragOverlay` drop animation, board container height.
- `fe/src/components/Column.tsx` — ring/highlight rendering, column height.
- `fe/src/components/ui/dialog.tsx`, `fe/src/components/ui/alert-dialog.tsx` — overlay background token.
- `be/src/main/resources/db/changelog/sql/0009-local-seed-data.sql` — timestamp values only (fixed UUIDs unchanged). Anyone with an already-seeded local DB must reset it (`docker compose down -v`) because the changeset checksum changes.
- No API, schema, or dependency changes.
