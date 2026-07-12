/**
 * Comments API: typed calls against `/api/v1/tickets/{ticketId}/comments`, mirroring the backend
 * `CommentController` contracts — `list` → `CommentResponse[]` (oldest first), `add` → `CommentResponse`.
 * Comments are immutable, so there is no update or delete. Follows the `epics.ts` shape (D7).
 */
import { apiFetch } from './client'
import { ApiError, isObject, problemError } from './problem'

/** Path to a ticket's comment collection. */
export function commentsPath(ticketId: string): string {
  return `/api/v1/tickets/${ticketId}/comments`
}

/**
 * Backend `CommentResponse(id, ticketId, authorId, body, createdAt)` — camelCase JSON, ISO-8601 UTC
 * timestamp.
 */
export interface CommentResponse {
  id: string
  ticketId: string
  authorId: string
  body: string
  createdAt: string
}

/**
 * List a ticket's comments (backend returns them oldest first).
 *
 * @param options.signal cancellation signal from TanStack Query's `queryFn`
 * @throws ApiError on a non-success status (404 unknown ticket)
 */
export async function listComments(
  ticketId: string,
  options: { signal?: AbortSignal } = {},
): Promise<CommentResponse[]> {
  const res = await apiFetch(commentsPath(ticketId), { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  const payload: unknown = await res.json()
  if (!Array.isArray(payload)) {
    throw new Error('comments response is not an array')
  }
  return payload.map(parseComment)
}

/**
 * Add a comment to a ticket. The author and timestamp are server-set.
 *
 * @throws ApiError on a non-success status (400 blank/oversized body, 404 unknown ticket)
 */
export async function addComment(ticketId: string, body: string): Promise<CommentResponse> {
  const res = await apiFetch(commentsPath(ticketId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ body }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseComment(await res.json())
}

function parseComment(payload: unknown): CommentResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.ticketId !== 'string' ||
    typeof payload.authorId !== 'string' ||
    typeof payload.body !== 'string' ||
    typeof payload.createdAt !== 'string'
  ) {
    throw new Error('comment response is missing required fields')
  }
  return {
    id: payload.id,
    ticketId: payload.ticketId,
    authorId: payload.authorId,
    body: payload.body,
    createdAt: payload.createdAt,
  }
}

// Re-exported so screens can catch it without importing the problem module directly.
export { ApiError }
