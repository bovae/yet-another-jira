/**
 * Typed fetch wrapper for the backend API: enforces a 10s timeout via AbortController so a slow or
 * unreachable backend errors instead of hanging.
 *
 * The frontend intentionally does NOT send an X-Correlation-Id. The backend generates a correlation
 * id for every request (and still honors a valid id supplied by other clients), so the SPA has none
 * of its own to contribute.
 */

export const REQUEST_TIMEOUT_MS = 10_000

/**
 * Fetch against the API with a hard timeout.
 *
 * @throws DOMException (name `AbortError`) when the request exceeds the timeout
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    return await fetch(path, {
      ...init,
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timeout)
  }
}
