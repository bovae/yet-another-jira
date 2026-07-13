# fix-board-ux-polish — Tasks

## 1. Board drag-and-drop fixes

- [x] 1.1 Add `ring-inset` to the `isOver` highlight in `fe/src/components/Column.tsx` so the drop-target ring is fully visible on all four sides (D1)
- [x] 1.2 Set `dropAnimation={null}` on the `DragOverlay` in `fe/src/pages/BoardPage.tsx` so a released card lands in the target column without a return-to-source animation (D2)

## 2. Board layout & refetch behavior

- [x] 2.1 Change `Column.tsx` sizing from `max-h-[calc(100vh-16rem)] min-h-30` to a fixed `h-[calc(100vh-16rem)]`; visually verify columns reach the viewport bottom and tune the offset if needed (D4)
- [x] 2.2 Add `placeholderData: keepPreviousData` to the board query in `BoardPage.tsx` so filter/search refetches keep the previous board visible; verify `LoadingState` still shows on first load (D6)
- [x] 2.3 Update/extend `BoardPage.test.tsx` for the new behavior: no loading flash on search/filter change, board data swaps in after refetch

## 3. Modal backdrop

- [x] 3.1 Replace `bg-black/50` with `bg-primary/50` in the overlay of `fe/src/components/ui/dialog.tsx` and `fe/src/components/ui/alert-dialog.tsx` (D3)

## 4. Seed data timestamps

- [x] 4.1 Rewrite timestamps in `be/src/main/resources/db/changelog/sql/0009-local-seed-data.sql`: staggered deterministic values — user/teams mid-June 2026, epics days later, tickets spread with distinct `created_at`, `modified_at` ≥ `created_at`, comments at/after their ticket's creation (D5); UUIDs and all other columns unchanged
- [x] 4.2 Reset the local DB (`docker compose down -v`), start the stack, and verify the seed applies cleanly and board cards show a meaningful modified-first order

## 5. Validation

- [x] 5.1 Run `make fe-lint` and `make fe-test`
- [ ] 5.2 Manually verify in the running stack: drop highlight visible on top edge, instant drop landing, darkened dialog backdrop, full-height columns, no search flicker
