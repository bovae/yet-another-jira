# Proposal: add-fe-team-epic-management

## Why

E13 (FE team + epic management screens) is the next epic in the catalog: the backend contracts it consumes (E5 teams-crud, E6 epics-crud) and the FE foundation/auth screens (E11/E12) are done, but `/teams` and `/epics` still render placeholders — users cannot manage teams or epics through the UI. This blocks E14 (FE ticket views), which needs teams and epics to exist for ticket creation.

## What Changes

- Replace the `/teams` placeholder with the team management screen: list all teams (name + timestamps), create and rename via shadcn **Dialog** forms calling `POST /api/v1/teams` / `PUT /api/v1/teams/{id}`, delete behind a confirm **AlertDialog** calling `DELETE /api/v1/teams/{id}`.
- **Reference-aware disabled delete for teams:** a team's delete control is disabled with a clear message when the team has epics (computed from `GET /api/v1/epics` grouped by team — tickets cannot exist yet, E7 is unbuilt). A backend `409` (race: epic created after load) is still surfaced as a clear error.
- Replace the `/epics` placeholder with the epic management screen: list epics filtered by a team **Select**, create via Dialog (team chosen at creation, **fixed** after — edit dialog shows team read-only), edit title/description, delete behind a confirm AlertDialog. Backend `409` (ticket references — possible only after E7) surfaces as a clear error.
- Add typed API modules `fe/src/api/teams.ts` and `fe/src/api/epics.ts` following the existing `auth.ts` pattern (RFC 9457 problem `detail` surfaced, not generic status text).
- Add the shadcn primitives these screens consume (`dialog`, `alert-dialog`, `select`, `textarea`), re-themed to `DESIGN.md` tokens per `fe/CLAUDE.md`.
- All screens show loading / empty / error states (§11) via the shared state components; Vitest for disabled-delete logic + create/rename flows per the E13 DoD.

## Capabilities

### New Capabilities

- `fe-team-management`: the team management screen — list, create, rename, confirm-delete, reference-aware disabled delete, error surfacing, async states.
- `fe-epic-management`: the epic management screen — team-filtered list, create with fixed team, edit title/description, confirm-delete, `409` surfacing, async states.

### Modified Capabilities

- `fe-routing`: `/teams` and `/epics` stop being placeholders and render the real management screens (placeholder set shrinks to `/tickets/:id`).

## Impact

- **Code:** `fe/src/pages/` (new `TeamsPage`, `EpicsPage`), `fe/src/App.tsx` (route swaps), `fe/src/api/` (new `teams.ts`, `epics.ts`), `fe/src/components/ui/` (new shadcn primitives with token adaptation).
- **APIs consumed:** existing `/api/v1/teams` and `/api/v1/epics` CRUD — no backend changes.
- **Specs:** new `fe-team-management`, `fe-epic-management`; delta to `fe-routing`.
- **Out of scope:** ticket-reference counts in delete disabling (no ticket API until E7; backend `409` remains the authoritative guard and is always surfaced), backend response-shape changes, Playwright flows (Batch 11).
