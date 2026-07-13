# Tasks: add-frontend-foundation

## 1. Dependencies & shadcn setup

- [x] 1.1 Add `react-router` v7 to `fe/package.json` (confirm current v7 API/imports via Context7 docs first)
- [x] 1.2 Add `@/*` path alias to `fe/tsconfig.app.json` (+ test/e2e tsconfigs as needed) and `vite.config.ts`
- [x] 1.3 Run `shadcn init` (Tailwind v4); commit `components.json` and generated `lib/utils`
- [x] 1.4 Bridge shadcn semantic variables (`--background`, `--foreground`, `--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--radius`) onto `DESIGN.md` tokens in `fe/src/index.css`
- [x] 1.5 `shadcn add dropdown-menu`; render it once against the bridge and visually verify token colors (not unstyled)

## 2. Auth session

- [x] 2.1 Auth API module: typed `login`/`logout`/`me` calls against `/api/v1/auth/*` (`LoginResponse`, `MeResponse`)
- [x] 2.2 `AuthProvider` + `useAuth`: token read/write via existing `TOKEN_KEY`, user state, boot hydration via `me` (no token → skip; reject → clear)
- [x] 2.3 Extend `apiFetch`: on `401` where a bearer token was attached, clear token and dispatch `auth:unauthorized` CustomEvent; `AuthProvider` listens and clears user
- [x] 2.4 `logout()` action: call `POST /auth/logout`, clear token + user even on network failure
- [x] 2.5 Vitest: client attaches bearer / no header without token; 401-with-token clears session; 401-without-token does not; hydration paths; logout clears on failure

## 3. Routing & app shell

- [x] 3.1 Route table in `App.tsx`: public `/login`, `/signup`, `/verify` (placeholders for E12); guarded layout route with `/` (existing `BoardPage`), `/teams`, `/epics`, `/tickets/:id` placeholders; not-found route
- [x] 3.2 `RequireAuth` guard: unauthenticated → `<Navigate to="/login" state={{ from }} />`; loading state while hydrating
- [x] 3.3 App shell layout: header with app name, nav links (board/teams/epics), user menu (DropdownMenu) showing email + **Log out** wired to `logout()`, `<Outlet/>` below
- [x] 3.4 Async-state components `LoadingState` / `EmptyState` / `ErrorState` in `fe/src/components/state/`; use them in the shell/board where statuses already exist
- [x] 3.5 Vitest: guard redirects unauthenticated and preserves `from`; authenticated renders; user menu opens and Log out fires logout

## 4. Verify

- [x] 4.1 `npm run lint`, `format:check`, `typecheck`, `test` green in `fe/`
- [x] 4.2 Manual smoke: fresh load → redirected to `/login`; with token → board renders in shell; user menu Log out returns to `/login`
- [x] 4.3 Update `fe/CLAUDE.md` shadcn section (it says "not in the repo yet" — record init + bridge location)
