# Design: add-fe-team-epic-management

## Context

E11/E12 left the FE foundation ready: `apiFetch` attaches the bearer token and handles session-expiry 401s, `RequireAuth` guards business routes, TanStack Query is wired (`main.tsx`, used by `BoardPage`/`VerifyPage`), `api/auth.ts` established the typed-call + `ApiError` (RFC 9457 `detail`) pattern, and shadcn `button`/`input`/`label`/`dropdown-menu` exist with the documented token adaptation. The backend contracts are live and verified against the DTOs: `TeamResponse(id, name, createdAt, modifiedAt)` and `EpicResponse(id, teamId, title, description?, createdAt, modifiedAt)` — camelCase JSON, ISO-8601 UTC timestamps. Teams: `GET/POST /api/v1/teams`, `GET/PUT/DELETE /api/v1/teams/{id}` (400 validation, 409 duplicate-name / delete-guard, 404). Epics: `GET /api/v1/epics?teamId=`, `POST /api/v1/epics`, `GET/PUT/DELETE /api/v1/epics/{id}` (team immutable on PUT, 409 delete-guard).

Constraints: DESIGN.md token-only styling, shadcn primitives added on demand with type/shadow adaptation (`fe/CLAUDE.md`), loading/empty/success/error states everywhere (§11), Vitest for disabled-delete logic + create/rename flows (E13 DoD). Build order matters: E7 (tickets) is unbuilt, so no ticket API exists and no tickets can exist in any environment (fresh DB, no seed data).

## Goals / Non-Goals

**Goals:**
- Full team lifecycle (list, create, rename, delete) and epic lifecycle (list by team, create, edit, delete) through the UI against the real API.
- Reference-aware disabled delete for teams; backend `409` surfaced verbatim everywhere as the authoritative guard.
- Epic team fixed at creation and visibly immutable on edit.
- Backend problem `detail` messages shown, never raw status codes.

**Non-Goals:**
- Ticket-reference awareness in the UI (no ticket API until E7; revisit in E14 when tickets exist).
- Backend changes of any kind (no reference counts added to responses).
- Pagination/virtualization (hackathon scale; lists are small).
- Playwright coverage (Batch 11 integration per the epics catalog).

## Decisions

### D1 — `ApiError`/problem parsing extracted to a shared module, reused by `teams.ts`/`epics.ts`
`ApiError` and the `problemError(res)` helper currently live in `api/auth.ts`. Importing them from `auth.ts` into team/epic modules couples unrelated slices, and copying them duplicates the wire-format knowledge. Extract both into `fe/src/api/problem.ts`; `auth.ts` re-exports `ApiError` so existing imports and tests stay untouched. New modules follow the `auth.ts` shape exactly: path constants, typed responses mirroring the backend DTOs, runtime payload parsing, `@throws ApiError` on non-success.

### D2 — TanStack Query for reads, `useMutation` + invalidation for writes
Lists load via `useQuery({ queryKey: ['teams'] })` / `['epics', teamId ?? 'all']`. Create/rename/delete run through `useMutation` with `onSuccess: invalidateQueries` on the affected keys (epic mutations also invalidate `['teams']`-derived reference data, see D3). This is the pattern already in the codebase (BoardPage/VerifyPage); hand-rolled `useEffect` fetching would re-implement caching, dedupe, and refetch-on-focus for nothing.

### D3 — Team delete disabled from the epics list; backend `409` remains authoritative
The teams screen runs a second query, `GET /api/v1/epics` (all epics), and computes `hasEpics` per team by grouping on `teamId`. Delete is disabled with a clear message ("has epics") when true. Tickets cannot exist yet (E7 unbuilt, no seed data), so epics are the only possible reference — the catalog's build order makes this exact, not approximate. Races (an epic created after load) and future ticket references still surface as the backend `409` problem `detail` rendered in the confirm dialog. *Why not backend reference counts:* a response-shape change to two live specs plus BE code/tests, to duplicate information one existing GET already provides at this scale.

### D4 — Epic delete always enabled; `409` surfaced in the confirm dialog
No ticket API exists to compute references from, and no tickets can exist until E7 — every epic delete is currently deletable, so a disabled state is unreachable. The confirm AlertDialog renders the backend `409` `detail` inline if it ever fires (post-E7 data). E14 (which introduces the ticket API to the FE) is where epic-delete disabling becomes computable and gets added.

### D5 — Dialogs own their form state; plain `useState`, no form library
Create/rename team dialogs share one name-field form component; epic create (team Select + title + textarea description) and epic edit (same minus team — shown as read-only text) are separate small forms. Native `required` hints only; server validation authoritative (§9). Mutation errors (`400`/`409`) render inside the open dialog so input isn't lost. Dialogs unmount on close, resetting state — no manual reset plumbing. No react-hook-form/zod: 1–3 fields per form (same call as E12's D2).

### D6 — Epics screen: "All teams" default + team filter Select; create requires an explicit team
List defaults to `GET /api/v1/epics` (all epics, team name resolved from the cached teams query) with a Select narrowing to one team (`?teamId=`). The create dialog has its own required team Select — deliberately not pre-filled from the filter only when a specific team is filtered (pre-fill then; empty otherwise). With zero teams, the create path shows an empty-state prompt to create a team first instead of an unfillable Select.

### D7 — New shadcn primitives: `dialog`, `alert-dialog`, `select`, `textarea`
Added via `shadcn add`, then the documented adaptation: stock `text-sm`/`shadow-md` → `text-body-sm`/`shadow-card` DESIGN tokens (pattern in `components/ui/dropdown-menu.tsx`). Lists render as token-styled rows — no shadcn table component; it would be styled markup with the same effort.

## Risks / Trade-offs

- [Disabled-delete data goes stale (epic added in another session)] → TanStack refetch-on-focus plus the backend `409` rendered in the dialog; the guard is server-side, the disable is UX.
- [Epic delete enabled while tickets reference it, after E7 lands] → Backend `409` guard is authoritative and its message is surfaced; UI disabling is explicitly deferred to E14 where the ticket contract exists. Recorded here so it isn't forgotten.
- [Fetching all epics on the teams screen] → Single extra GET at hackathon scale (spec caps title 200 chars, no pagination anywhere yet); if it ever matters, that's the moment for backend counts.
- [Two dialogs (create/rename) sharing one form component] → Divergence pressure if future fields differ; acceptable because both are name-only by spec.

## Open Questions

None — contracts verified against `TeamController`/`EpicController` DTOs; build-order reasoning (no tickets before E7) is from the epics catalog itself.
