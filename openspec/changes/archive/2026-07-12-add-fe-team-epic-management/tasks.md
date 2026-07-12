# Tasks: add-fe-team-epic-management

## 1. API layer

- [x] 1.1 Extract `ApiError` + `problemError` from `fe/src/api/auth.ts` into `fe/src/api/problem.ts`; re-export `ApiError` from `auth.ts` so existing imports/tests stay green (D1)
- [x] 1.2 Add `fe/src/api/teams.ts`: typed `listTeams`, `createTeam`, `renameTeam`, `deleteTeam` mirroring `TeamResponse(id, name, createdAt, modifiedAt)`, throwing `ApiError` on non-success (D1)
- [x] 1.3 Add `fe/src/api/epics.ts`: typed `listEpics(teamId?)`, `createEpic`, `updateEpic` (title/description only), `deleteEpic` mirroring `EpicResponse(id, teamId, title, description?, createdAt, modifiedAt)` (D1)
- [x] 1.4 Vitest for both modules: right path/method/query param, response parsing, `ApiError` carries problem `detail`

## 2. Primitives

- [x] 2.1 `shadcn add dialog alert-dialog select textarea`; apply the DESIGN.md type/shadow token adaptation per `fe/CLAUDE.md` (D7)

## 3. Teams screen

- [x] 3.1 `TeamsPage`: teams query + all-epics query, `hasEpics` per team, token-styled list rows (name + timestamps), shared loading/empty/error states (D2, D3)
- [x] 3.2 Create/rename dialogs sharing one name-field form; mutations with invalidation; in-dialog `400`/`409` `detail` rendering, pending disables submit (D5)
- [x] 3.3 Delete confirm AlertDialog; delete control disabled with referenced-team message when `hasEpics`; `409` `detail` rendered in dialog, team stays (D3)
- [x] 3.4 Vitest `TeamsPage`: delete disabled iff team has epics; create/rename success updates list; 409 renders `detail` in open dialog; confirm-delete removes row; cancel issues no request

## 4. Epics screen

- [x] 4.1 `EpicsPage`: epics query keyed by team filter, team-name resolution from teams query, filter Select with "All teams" default, shared loading/empty/error states (D2, D6)
- [x] 4.2 Create dialog: required team Select (pre-filled from an active filter), title, optional description textarea; no-teams case prompts to create a team first (D5, D6)
- [x] 4.3 Edit dialog: title/description pre-filled, team as read-only text, `PUT` without team (D5)
- [x] 4.4 Delete confirm AlertDialog; `409` `detail` rendered in dialog, epic stays (D4)
- [x] 4.5 Vitest `EpicsPage`: filter narrows list; create sends chosen `teamId`; edit sends no team and team is not editable; no-teams prompt; delete 409 renders `detail`

## 5. Routing

- [x] 5.1 Swap the `/teams` and `/epics` placeholders for the real pages in `fe/src/App.tsx`
- [x] 5.2 Update routing tests: `/teams` and `/epics` render the real screens; `/tickets/:id` still a placeholder

## 6. Verify

- [x] 6.1 `make fe-lint` and FE test suite green
- [x] 6.2 Manual flow against docker compose: create team → rename → create epic under it → team delete disabled → edit epic → delete epic → delete team succeeds
