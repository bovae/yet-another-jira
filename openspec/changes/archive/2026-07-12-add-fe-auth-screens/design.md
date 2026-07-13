# Design: add-fe-auth-screens

## Context

E11 left everything these screens need in place: `AuthProvider` exposes `login(credentials)` (stores the token, hydrates the user), `apiFetch` attaches the bearer token and handles token-carrying 401s, `RequireAuth` redirects unauthenticated visitors to `/login` with `state={{ from: location }}`, and `api/auth.ts` already types `login`/`me`/`logout`. The backend contracts (E1–E3) are live: `POST /signup` (201/400/409), `POST /verify` (200/400/410), `POST /verification/resend` (uniform 202/429), `POST /login` (200/400/401/403/429). Errors are RFC 9457 problem bodies with a human-readable `detail`. The emailed verification link points at the FE (`YAJ_VERIFICATION_LINK_BASE_URL` → `http://localhost:8081/verify?token=…`), and the backend's browser-flow error redirect targets `http://localhost:8081/verify-error`.

Constraints: DESIGN.md token-only styling (stock Tailwind palette/type scale reset out), shadcn primitives added on demand with type/shadow adaptation (`fe/CLAUDE.md`), loading/empty/success/error states everywhere (§11), Vitest per form (E12 DoD).

## Goals / Non-Goals

**Goals:**
- Working sign-up → verify → login flow through the UI against the real API.
- Backend `detail` messages shown verbatim on failures; no raw status codes.
- Post-login redirect to the guarded location the user originally requested.
- Resend action reachable from login and verification result/error screens.

**Non-Goals:**
- Password reset (stretch, §14). No "remember me", no OAuth.
- Playwright signup→verify→login happy-path (lands with Batch 11 integration per the epics catalog).
- Backend changes of any kind.
- Form library / schema validation (two-field forms; native constraint hints suffice).

## Decisions

### D1 — Typed `ApiError` in `api/auth.ts`, problem `detail` parsed at the API layer
New auth calls (`signup`, `verify`, `resend`) and the existing `login` throw an `ApiError extends Error` carrying `status` and the problem body's `detail` (fallback to a generic message when the body isn't parseable JSON). Screens branch on `status` (403 → show resend, 429 → retry-later) and render `message` directly.
*Why not per-screen parsing:* four screens would duplicate the same problem-body extraction; the API module is the single place that knows the wire format. `fetchMe`/`logout` keep their current generic throws — nothing renders their messages.

### D2 — Plain `useState` forms, native input hints, no form library
Email uses `<input type="email" required>`, password `minLength={8}` — client hints only, server authoritative (§3). Submit handler: prevent default, set pending, call API, branch on `ApiError`. No react-hook-form/zod: two fields per form doesn't justify a dependency (they're not in `package.json` today).

### D3 — Verification runs as a TanStack Query keyed by the token
`/verify` reads `token` from `useSearchParams` and runs the `POST /verify` call via `useQuery({ queryKey: ['verify', token], retry: false, staleTime: Infinity, gcTime: Infinity, enabled: !!token })`. The token is single-use, and React StrictMode double-mounts effects in dev — a bare `useEffect` fires the POST twice and the second call gets `410` for a token that just succeeded. Query-key deduplication makes the call fire exactly once per token with no manual ref guard. Missing token renders the error state without calling the API.

### D4 — `/verify-error` reuses the verify page's error state
Same page component with a `variant="error"` short-circuit (no API call, no token read). It exists only because `YAJ_VERIFICATION_ERROR_REDIRECT_URL` points browsers there after a failed `GET /api/v1/auth/verify` link; a dedicated component would duplicate the error/resend rendering.

### D5 — `ResendVerification` shared component
One component owning its own email field, pending/success/error state, calling `resend()`. Login embeds it when a `403` is shown; verify/verify-error embed it in the error state. Uniform 202 message rendered from the response body (backend already words it).

### D6 — Post-login redirect from router state
`LoginPage` reads `location.state?.from` (the shape `RequireAuth` already writes) and `navigate(from ?? '/', { replace: true })` after `login()` resolves. No storage of intended location anywhere else.

### D7 — Auth layout + shadcn `button`/`input`/`label`
A minimal centered-card `AuthLayout` (DESIGN.md surface/spacing tokens) wraps the three screens. Add shadcn `button`, `input`, `label` via `shadcn add` — first real form consumers — and apply the documented type/shadow token adaptation (`text-body-sm`, `shadow-card`) as done for `dropdown-menu`.

## Risks / Trade-offs

- [Backend `detail` wording rendered verbatim] → Backend already words messages for end users (uniform resend message, "Invalid email or password."); FE falls back to a generic message when `detail` is absent.
- [POST-as-`useQuery` for verify is unconventional] → It is deliberate for single-use-token dedupe (D3); documented in the page's comment. A `useMutation` + ref guard would be equally sized but hand-rolls what the query cache gives for free.
- [Signup success screen does not offer resend] → §3 requires resend only from login and verification-result screens, both of which the user reaches next; adding a third entry point is scope creep.
- [`/verify-error` drifts from the backend redirect config] → The route name is pinned by `docker-compose.yml` defaults; a task adds it to the route table and the spec so a rename would be a visible spec change.

## Open Questions

None — contracts verified against `AuthController` and its DTOs; no ambiguity left worth blocking on.
