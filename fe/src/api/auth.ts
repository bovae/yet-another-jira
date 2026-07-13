/**
 * Auth API: typed calls against `/api/v1/auth/*`. Mirrors the backend `AuthController` contracts —
 * `login` → `LoginResponse`, `signup` → `SignupResponse`, `verify` → `VerifyResponse`,
 * `resend` → `ResendResponse`, `me` → `MeResponse`, `logout` → 204. Token storage and session state
 * live in the auth context, not here; this module only speaks HTTP.
 *
 * Failure responses from the endpoints the auth screens render (`login`, `signup`, `verify`,
 * `resend`) throw an {@link ApiError} carrying the HTTP status and the RFC 9457 problem `detail`, so
 * screens can branch on `status` and show `message` verbatim. `fetchMe`/`logout` keep generic throws
 * — nothing renders their messages.
 */
import { apiFetch } from './client'
import { isObject, problemError } from './problem'

// `ApiError` and problem parsing moved to `problem.ts` (D1); re-exported so existing auth imports and
// tests keep resolving them from here.
export { ApiError, GENERIC_ERROR_MESSAGE } from './problem'

export const AUTH_LOGIN_PATH = '/api/v1/auth/login'
export const AUTH_SIGNUP_PATH = '/api/v1/auth/signup'
export const AUTH_VERIFY_PATH = '/api/v1/auth/verify'
export const AUTH_RESEND_PATH = '/api/v1/auth/verification/resend'
export const AUTH_LOGOUT_PATH = '/api/v1/auth/logout'
export const AUTH_ME_PATH = '/api/v1/auth/me'

/** Backend `LoginResponse(accessToken, tokenType, expiresInSeconds)`. */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

/** Backend `SignupResponse(id, email, emailVerified, createdAt)`. */
export interface SignupResponse {
  id: string
  email: string
  emailVerified: boolean
  createdAt: string
}

/** Backend `VerifyResponse(verified, message, next)`. */
export interface VerifyResponse {
  verified: boolean
  message: string
  next: string
}

/** Backend `ResendResponse(message)` — the uniform 202 confirmation. */
export interface ResendResponse {
  message: string
}

/** Backend `MeResponse(id, email, emailVerified)`. */
export interface MeResponse {
  id: string
  email: string
  emailVerified: boolean
}

export interface Credentials {
  email: string
  password: string
}

/**
 * Authenticate with email + password.
 *
 * @throws ApiError on a non-success status (e.g. 401 invalid credentials, 403 unverified, 429 rate
 *   limited). The 401 here carries no bearer token, so it does NOT trigger the global session-expiry
 *   handler.
 */
export async function login(credentials: Credentials): Promise<LoginResponse> {
  const res = await apiFetch(AUTH_LOGIN_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseLoginResponse(await res.json())
}

/**
 * Register a new account. The backend sends a verification email; the returned account is unverified
 * until the emailed link is followed.
 *
 * @throws ApiError on a non-success status (400 malformed email / weak password, 409 duplicate)
 */
export async function signup(credentials: Credentials): Promise<SignupResponse> {
  const res = await apiFetch(AUTH_SIGNUP_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseSignupResponse(await res.json())
}

/**
 * Redeem an email-verification token. Single-use: a second call for the same token gets `410`.
 *
 * @throws ApiError on a non-success status (400 malformed, 410 expired / already used)
 */
export async function verify(token: string): Promise<VerifyResponse> {
  const res = await apiFetch(AUTH_VERIFY_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseVerifyResponse(await res.json())
}

/**
 * Request a fresh verification email. The backend responds with a uniform `202` message whether or
 * not an unverified account exists (no account enumeration), so the caller renders the returned
 * `message` as-is.
 *
 * @throws ApiError on a non-success status (429 rate limited)
 */
export async function resend(email: string): Promise<ResendResponse> {
  const res = await apiFetch(AUTH_RESEND_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseResendResponse(await res.json())
}

/**
 * Fetch the current user. Used to validate a stored token on boot.
 *
 * @param options.signal cancellation signal, so an unmounted hydration aborts the in-flight request
 * @throws Error when the response is not a success status (a 401 clears the session via apiFetch)
 *   or the payload is malformed
 */
export async function fetchMe(options: { signal?: AbortSignal } = {}): Promise<MeResponse> {
  const res = await apiFetch(AUTH_ME_PATH, { signal: options.signal })
  if (!res.ok) {
    throw new Error(`me fetch failed: ${res.status}`)
  }
  return parseMeResponse(await res.json())
}

/**
 * End the server-side session. The backend expires the token's denylist entry on its own, so a
 * failed request is not fatal — the caller still clears local state.
 *
 * @throws Error when the response is not a success status, so the caller can log the failure while
 *   still clearing the local session
 */
export async function logout(): Promise<void> {
  const res = await apiFetch(AUTH_LOGOUT_PATH, { method: 'POST' })
  if (!res.ok) {
    throw new Error(`logout failed: ${res.status}`)
  }
}

function parseLoginResponse(payload: unknown): LoginResponse {
  if (
    !isObject(payload) ||
    typeof payload.accessToken !== 'string' ||
    typeof payload.tokenType !== 'string' ||
    typeof payload.expiresInSeconds !== 'number'
  ) {
    throw new Error('login response is missing required fields')
  }
  return {
    accessToken: payload.accessToken,
    tokenType: payload.tokenType,
    expiresInSeconds: payload.expiresInSeconds,
  }
}

function parseSignupResponse(payload: unknown): SignupResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.email !== 'string' ||
    typeof payload.emailVerified !== 'boolean' ||
    typeof payload.createdAt !== 'string'
  ) {
    throw new Error('signup response is missing required fields')
  }
  return {
    id: payload.id,
    email: payload.email,
    emailVerified: payload.emailVerified,
    createdAt: payload.createdAt,
  }
}

function parseVerifyResponse(payload: unknown): VerifyResponse {
  if (
    !isObject(payload) ||
    typeof payload.verified !== 'boolean' ||
    typeof payload.message !== 'string' ||
    typeof payload.next !== 'string'
  ) {
    throw new Error('verify response is missing required fields')
  }
  return { verified: payload.verified, message: payload.message, next: payload.next }
}

function parseResendResponse(payload: unknown): ResendResponse {
  if (!isObject(payload) || typeof payload.message !== 'string') {
    throw new Error('resend response is missing required fields')
  }
  return { message: payload.message }
}

function parseMeResponse(payload: unknown): MeResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.email !== 'string' ||
    typeof payload.emailVerified !== 'boolean'
  ) {
    throw new Error('me response is missing required fields')
  }
  return { id: payload.id, email: payload.email, emailVerified: payload.emailVerified }
}
