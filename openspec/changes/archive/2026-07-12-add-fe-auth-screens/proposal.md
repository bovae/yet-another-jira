# Proposal: add-fe-auth-screens

## Why

E12 (FE auth screens) is the next epic in the catalog: the backend auth spine (E1–E4) and the FE foundation (E11) are done, but `/login`, `/signup`, and `/verify` still render placeholders — users cannot actually sign up, verify their email, or log in through the UI. This blocks every subsequent FE epic (E13–E15), which assume a signed-in session created through these screens.

## What Changes

- Replace the `/signup` placeholder with a real sign-up form (email + password) calling `POST /api/v1/auth/signup`; success shows a "check your email" state, errors surface backend validation messages (400 weak password / malformed email, 409 duplicate).
- Replace the `/login` placeholder with a real login form using the existing auth-context `login()`; on success redirect to the originally requested location (navigation state from the route guard) or `/`. Surface 401 invalid credentials, 403 unverified (with a resend path), and 429 rate-limit messages.
- Replace the `/verify` placeholder with the email-verification result screen: reads `?token=`, calls `POST /api/v1/auth/verify`, renders loading → success (link to login) or error (invalid/expired → resend path).
- Add a `/verify-error` public route rendering the verification-error state — it is the configured backend redirect target (`YAJ_VERIFICATION_ERROR_REDIRECT_URL`) for browser-followed `GET /api/v1/auth/verify` links.
- Add a resend-verification action (email input → `POST /api/v1/auth/verification/resend`, uniform 202 message, 429 handled) reachable from **both** the login and verification-result/error screens (§3).
- Extend `fe/src/api/auth.ts` with typed `signup`, `verify`, and `resend` calls, and surface RFC 9457 problem `detail` messages from auth error responses instead of generic status-code errors.
- All screens show loading / success / error states (§11); Vitest per form (submit, error rendering) per the E12 DoD.

## Capabilities

### New Capabilities

- `fe-auth-screens`: the sign-up, login, and email-verification-result screens plus the resend-verification action — form behavior, API calls, state rendering, error surfacing, and post-login redirect.

### Modified Capabilities

- `fe-routing`: the public route table gains `/verify-error` (backend verification-error redirect target); `/login`, `/signup`, `/verify` stop being placeholders.

## Impact

- **Code:** `fe/src/pages/` (new `LoginPage`, `SignupPage`, `VerifyPage`), `fe/src/App.tsx` (route swaps + `/verify-error`), `fe/src/api/auth.ts` (new calls + problem-detail parsing), possibly new shadcn primitives (`button`, `input`, `label`) added on demand per `fe/CLAUDE.md`.
- **APIs consumed:** existing `POST /api/v1/auth/{signup,login,verify,verification/resend}` — no backend changes.
- **Specs:** new `fe-auth-screens`; delta to `fe-routing`.
- **Out of scope:** password reset (stretch), Playwright happy-path (lands with E15/Batch 11 integration per the catalog).
