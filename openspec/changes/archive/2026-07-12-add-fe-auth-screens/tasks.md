# Tasks: add-fe-auth-screens

## 1. API layer

- [x] 1.1 Add `ApiError` (status + problem `detail`, generic fallback) to `fe/src/api/auth.ts`; switch `login()` to throw it (D1)
- [x] 1.2 Add typed `signup`, `verify`, `resend` calls mirroring the backend DTOs (`SignupResponse`, `VerifyResponse`, `ResendResponse`)
- [x] 1.3 Vitest: `ApiError` carries `detail` from a problem body, falls back on unparseable bodies; each new call hits the right path/method and parses its response

## 2. Primitives and layout

- [x] 2.1 `shadcn add button input label`; apply the DESIGN.md type/shadow token adaptation per `fe/CLAUDE.md` (D7)
- [x] 2.2 Add centered-card `AuthLayout` for public screens using DESIGN.md tokens

## 3. Resend action

- [x] 3.1 Build `ResendVerification` component (email field, pending/success/error states, uniform 202 message, 429 message) (D5)
- [x] 3.2 Vitest: submit calls resend and renders the returned message; 429 renders rate-limit message; pending disables submit

## 4. Screens

- [x] 4.1 `SignupPage`: form with native email/minLength hints, pending state, success → check-your-email state with login link, 400/409 `detail` rendering (D2)
- [x] 4.2 Vitest `SignupPage`: successful submit swaps to confirmation; 409 and 400 render backend `detail`; pending disables submit
- [x] 4.3 `LoginPage`: form calling auth-context `login`, redirect to `location.state.from` or `/` (D6), 401/429 `detail` rendering, 403 additionally reveals `ResendVerification`, link to `/signup`
- [x] 4.4 Vitest `LoginPage`: success redirects to preserved location and to `/` without one; 401 message renders; 403 reveals resend; pending disables submit
- [x] 4.5 `VerifyPage`: token from `useSearchParams`, one-shot verify via `useQuery` keyed by token (D3), loading → success-with-login-link / error-with-resend; missing token → error without API call; `variant="error"` short-circuit for `/verify-error` (D4)
- [x] 4.6 Vitest `VerifyPage`: valid token renders success + login link; 410 renders error + resend; missing token renders error with no fetch; error variant renders without fetch

## 5. Routing

- [x] 5.1 Swap the `/login`, `/signup`, `/verify` placeholders for the real pages in `fe/src/App.tsx` and add the public `/verify-error` route
- [x] 5.2 Update routing tests to cover the four public routes rendering the real screens

## 6. Verify

- [x] 6.1 `make fe-lint` and FE test suite green
- [x] 6.2 Manual flow against docker compose (Mailpit profile): sign up → follow emailed link → verified → log in → land on board; bad token lands on error + resend
