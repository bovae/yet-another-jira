/**
 * Auth API: typed calls against `/api/v1/auth/*`. Mirrors the backend `AuthController` contracts —
 * `login` → `LoginResponse`, `me` → `MeResponse`, `logout` → 204. Token storage and session state
 * live in the auth context, not here; this module only speaks HTTP.
 */
import { apiFetch } from './client'

export const AUTH_LOGIN_PATH = '/api/v1/auth/login'
export const AUTH_LOGOUT_PATH = '/api/v1/auth/logout'
export const AUTH_ME_PATH = '/api/v1/auth/me'

/** Backend `LoginResponse(accessToken, tokenType, expiresInSeconds)`. */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
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
 * @throws Error when the response is not a success status (e.g. 401 invalid credentials) or the
 *   payload is malformed. The 401 here carries no bearer token, so it does NOT trigger the global
 *   session-expiry handler.
 */
export async function login(credentials: Credentials): Promise<LoginResponse> {
  const res = await apiFetch(AUTH_LOGIN_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials),
  })
  if (!res.ok) {
    throw new Error(`login failed: ${res.status}`)
  }
  return parseLoginResponse(await res.json())
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

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
