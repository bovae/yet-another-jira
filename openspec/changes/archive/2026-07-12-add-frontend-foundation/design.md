# Design: add-frontend-foundation

## Context

The FE is the skeleton: one mock board page, no router, no session handling. `apiFetch` (`fe/src/api/client.ts`) already attaches a bearer token from `localStorage` (`TOKEN_KEY = 'accessToken'`) and enforces a 10s timeout. Tailwind v4 encodes the `DESIGN.md` tokens in `fe/src/index.css` via `@theme`, with the stock palette and type scale reset (`--color-*: initial`) — off-token utilities render nothing. Backend auth contracts are live: `POST /api/v1/auth/login` → `LoginResponse(accessToken, tokenType, expiresInSeconds)`, `GET /api/v1/auth/me` → `MeResponse(id, email, emailVerified)`, `POST /api/v1/auth/logout` → 204.

## Goals / Non-Goals

**Goals:**
- Route table for all §10 minimum screens; business routes guarded.
- Auth/session context: token + current user, logout, global 401 handling.
- shadcn/ui initialized and bridged to `DESIGN.md` tokens; first consumer is the header user menu.
- Reusable loading / empty / error state components.

**Non-Goals:**
- Auth screens (login/sign-up/verify forms) — E12; guarded routes render placeholders.
- Real board, team, epic, ticket screens — E13–E15; the mock board stays as the board placeholder.
- Bundling Geist/Inter webfonts (recorded follow-up; `system-ui` fallback stands).
- Token refresh — the backend issues single short-lived access tokens; expiry is handled by the global 401 path.

## Decisions

### D1 — Router: react-router v7, declarative mode
Single `react-router` package, `<BrowserRouter>` + `<Routes>`. No data-router loaders/actions — TanStack Query already owns data fetching, so loaders would duplicate it. Alternative: TanStack Router (typed routes) — heavier setup for no need at 8 routes. Verify v7 API against current docs at implementation time (v7 merged `react-router-dom` into `react-router`).

Routes: `/login`, `/signup`, `/verify` (public); `/` (board), `/teams`, `/epics`, `/tickets/:id` (guarded, inside the app-shell layout route). Ticket create/edit are modals per the catalog (E14) — no dedicated routes.

### D2 — Session: localStorage token + in-memory user, hydrated via `/auth/me`
Keep the existing `TOKEN_KEY` localStorage slot (refresh-safe; server remains the system of record). `AuthProvider` holds `user` in React state. On boot with a token present, fetch `/auth/me`: success → authenticated; failure → clear token, unauthenticated. Alternative — decode the JWT client-side for user info: rejected, `me` validates the token against the denylist and is the authoritative contract.

### D3 — Global 401: session-expiry only when a token was attached
`apiFetch` gains one rule: if a request **carried a bearer token** and the response is 401, clear the stored token and dispatch a `window` CustomEvent (`auth:unauthorized`). `AuthProvider` listens and clears `user`; the route guard then redirects to `/login` on re-render. The token-attached condition keeps credential failures (login 401 with no token) from looping into a session clear. Event over callback injection: the api module stays router- and React-free.

### D4 — Route guard preserves intended destination
Guard component checks auth state; unauthenticated → `<Navigate to="/login" state={{ from: location }} />`. E12's login form redirects back to `from`. One line now, saves a spec change later.

### D5 — shadcn/ui: init + bridge, add primitives on demand
Run `shadcn init` (Tailwind v4 aware; generates `components.json`, expects a `@/` path alias — add to `tsconfig` + `vite.config`). Bridge shadcn semantic variables (`--background`, `--foreground`, `--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--radius`) onto `DESIGN.md` tokens in `index.css` — mandatory because the palette reset leaves un-bridged shadcn classes unstyled. Add **only DropdownMenu** now (the user-menu consumer); E13–E15 run `shadcn add` for Dialog/Select/etc. when they consume them (YAGNI). New deps this pulls in: Radix primitives, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`.

### D6 — App shell: layout route with header + user menu
Guarded layout route renders a header (app name, nav links to board/teams/epics, collapsed user menu) and an `<Outlet/>`. User menu (shadcn DropdownMenu) shows the current user's email and **Log out**, which calls `POST /auth/logout`, clears the session, and lands on `/login`. Logout clears locally even if the network call fails — the server-side denylist entry just expires with the token.

### D7 — Async-state components: small and presentational
`LoadingState`, `EmptyState`, `ErrorState` (message + optional retry) in `fe/src/components/state/`, built from `DESIGN.md` token utilities. Consumers pair them with TanStack Query status. No abstraction over Query itself.

## Risks / Trade-offs

- [shadcn defaults reference unbridged vars → invisible UI] → the bridge lands in the same task as `init`; DoD includes visually verifying the dropdown renders with token colors.
- [Token in localStorage is XSS-readable] → accepted per epic ("refresh-safe storage that is not the system of record"); no third-party script injection surface in this SPA.
- [react-router v7 API drift vs training data] → confirm exact imports/APIs via Context7 docs before coding.
- [Global 401 event races the guard redirect] → state clears synchronously in the event handler; redirect is derived from state, no imperative navigation from the api layer.

## Open Questions

None — contracts confirmed against `AuthController` DTOs.
