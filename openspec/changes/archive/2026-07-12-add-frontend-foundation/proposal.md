# Proposal: add-frontend-foundation

## Why

The backend auth spine (E1–E4) and Teams CRUD (E5) are live, but the frontend is still the skeleton: a single mock board page with no routing, no session handling, and no accessible primitives. Every remaining FE epic (E12–E15) needs an app shell, guarded routes, an authed API client, and the shadcn/ui primitive layer before any real screen can be built. This is E11 in `requirements/epics-catalog.md` (Batch 3).

## What Changes

- Add client-side routing with a route defined for every minimum screen (§10): login, sign-up, verification result, board, team management, epic management, ticket view. Business routes render placeholders for now; auth screens land in E12.
- Guard all business routes behind authentication — unauthenticated users are redirected to login.
- Add an auth/session context: JWT held in memory with refresh-safe storage (localStorage mirror, not the system of record), exposing the current user (`GET /api/v1/auth/me`) and a logout action (`POST /api/v1/auth/logout`).
- Extend `apiFetch` usage with global `401` handling: clear the session and redirect to login.
- Introduce shadcn/ui (`shadcn init`) and bridge its semantic tokens (`--background`, `--foreground`, `--primary`, `--border`, `--ring`, `--radius`) onto the `DESIGN.md` tokens in `fe/src/index.css` — required because the stock palette reset (`--color-*: initial`) leaves un-bridged shadcn classes unstyled.
- Add an app shell with a header containing the collapsed user menu (shadcn DropdownMenu) including **Log out** — the first shadcn consumer.
- Add reusable loading / empty / error / success state components used by all future screens (§11 usability).
- The mock board stays as the board placeholder behind the guard (removed in E9/E15).

## Capabilities

### New Capabilities

- `fe-routing`: client-side route table for all minimum screens with an auth guard that redirects unauthenticated users to login.
- `fe-auth-session`: auth context (token + current user), bearer-authenticated API client, global 401 handling, logout.
- `fe-design-primitives`: shadcn/ui primitive layer bridged to `DESIGN.md` tokens, app shell with header user menu, reusable async-state components.

### Modified Capabilities

None — existing specs (`auth-resilience`, `teams-crud`) are backend; no requirement changes.

## Impact

- **Code:** `fe/` only — `App.tsx`, `main.tsx`, new `router`/`auth`/`components/ui` modules, `index.css` (shadcn bridge). No backend changes; consumes existing `/api/v1/auth/*` contracts (`LoginResponse.accessToken`, `MeResponse`).
- **Dependencies:** adds `react-router` (or `react-router-dom`) and shadcn/ui prerequisites (Radix primitives, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`).
- **Tests:** Vitest for the route guard (redirects when unauthenticated) and the API client (attaches bearer, 401 clears session); existing board tests keep passing.
- **Follow-up recorded, not in scope:** bundling Geist/Inter webfonts (falls back to `system-ui`).
