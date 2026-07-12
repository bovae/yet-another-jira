/**
 * Shared RFC 9457 problem handling for the typed API modules. `ApiError` carries the HTTP status and
 * the backend problem `detail`, so screens can branch on `status` and render `message` verbatim;
 * `problemError` builds one from a non-success `Response`. Extracted from `auth.ts` (D1) so the
 * team/epic modules reuse this wire-format knowledge without coupling to the auth slice.
 */

/** Shown when a failure response carries no parseable problem `detail` (network error, timeout, …). */
export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.'

/**
 * A non-success API response. `message` is the backend problem `detail` when present, otherwise
 * {@link GENERIC_ERROR_MESSAGE}; `status` is the HTTP status so callers can branch (403, 409, 429, …)
 * without re-parsing the body.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * Build an {@link ApiError} from a non-success response, surfacing the RFC 9457 problem `detail`.
 * Falls back to {@link GENERIC_ERROR_MESSAGE} when the body is empty or not parseable problem JSON.
 */
export async function problemError(res: Response): Promise<ApiError> {
  let detail: string | null = null
  try {
    const body: unknown = await res.json()
    if (isObject(body) && typeof body.detail === 'string' && body.detail.length > 0) {
      detail = body.detail
    }
  } catch {
    // Non-JSON or empty body (network error, timeout) — fall back to the generic message.
  }
  return new ApiError(res.status, detail ?? GENERIC_ERROR_MESSAGE)
}

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
