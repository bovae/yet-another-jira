/**
 * Typed fetch wrapper for the backend API: enforces a 10s timeout via AbortController so a slow or
 * unreachable backend errors instead of hanging.
 *
 * The frontend intentionally does NOT send an X-Correlation-Id. The backend generates a correlation
 * id for every request (and still honors a valid id supplied by other clients), so the SPA has none
 * of its own to contribute.
 */

export const REQUEST_TIMEOUT_MS = 10_000

/** localStorage key where the JWT access token is stored after login. */
export const TOKEN_KEY = 'accessToken'

/**
 * Fetch against the API with a hard timeout. Attaches a Bearer token from localStorage if present.
 * A caller-supplied `init.signal` (e.g. TanStack Query's cancellation signal) is honored alongside
 * the timeout — whichever fires first aborts the request.
 *
 * @throws DOMException (name `AbortError`) when the timeout elapses or the caller aborts
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  const headers = new Headers(init.headers)
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const signal = init.signal ? AbortSignal.any([controller.signal, init.signal]) : controller.signal

  try {
    return await fetch(path, {
      ...init,
      headers,
      signal,
    })
  } finally {
    clearTimeout(timeout)
  }
}
