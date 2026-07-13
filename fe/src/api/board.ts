/**
 * Board API: getBoard() fetches GET /api/v1/teams/{teamId}/board — the real per-team Kanban board
 * (E9 contract). Mirrors the backend DTOs: 5 columns in workflow order (no label; the label comes
 * from ticketStateLabel), each card most-recently-modified first with an optional epic id + title.
 * Server-side filters (type, epicId, q) combine AND; a blank value is omitted so it does not filter.
 * Follows the epics.ts shape: typed response, runtime parse, {@link ApiError} on non-success.
 */
import { apiFetch } from './client'
import { ApiError, isObject, problemError } from './problem'

/** A single ticket card on the board. `type` is the raw code — labelled via ticketTypeLabel. */
export interface BoardCard {
  id: string
  title: string
  type: string
  epicId?: string
  epicTitle?: string
}

/** A board column mapped to one canonical ticket state; the label is derived via ticketStateLabel. */
export interface BoardColumn {
  state: string
  cards: BoardCard[]
}

/** The full board: an ordered collection of columns. */
export interface BoardView {
  columns: BoardColumn[]
}

/** Server-side board filters; a blank value is omitted so it does not filter (board-read contract). */
export interface BoardFilters {
  type?: string
  epicId?: string
  q?: string
}

/** Path to one team's board. */
export function boardPath(teamId: string): string {
  return `/api/v1/teams/${encodeURIComponent(teamId)}/board`
}

/**
 * Fetch a team's board, optionally filtered. Only non-blank filters are sent as query params.
 *
 * @param options.signal cancellation signal (e.g. from TanStack Query's `queryFn` context) so a
 *   superseded query aborts the in-flight request instead of leaking it
 * @throws ApiError on a non-success status (400 invalid type, 404 unknown team)
 * @throws DOMException (name `AbortError`) when the request times out or is aborted
 */
export async function getBoard(
  teamId: string,
  filters: BoardFilters = {},
  options: { signal?: AbortSignal } = {},
): Promise<BoardView> {
  const params = new URLSearchParams()
  const append = (key: string, value?: string) => {
    if (value && value.trim() !== '') {
      params.set(key, value)
    }
  }
  append('type', filters.type)
  append('epicId', filters.epicId)
  append('q', filters.q)
  const query = params.toString()
  const path = query ? `${boardPath(teamId)}?${query}` : boardPath(teamId)
  const res = await apiFetch(path, { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseBoardView(await res.json())
}

/**
 * Narrow an untyped payload to a {@link BoardView}, so a malformed body fails fast here with a clear
 * message instead of surfacing later as an opaque render-time error.
 */
function parseBoardView(payload: unknown): BoardView {
  if (!isObject(payload) || !Array.isArray(payload.columns)) {
    throw new Error('board response is missing a columns array')
  }
  return { columns: payload.columns.map(parseColumn) }
}

function parseColumn(value: unknown): BoardColumn {
  if (!isObject(value) || typeof value.state !== 'string' || !Array.isArray(value.cards)) {
    throw new Error('board column is missing required fields')
  }
  return { state: value.state, cards: value.cards.map(parseCard) }
}

function parseCard(value: unknown): BoardCard {
  if (
    !isObject(value) ||
    typeof value.id !== 'string' ||
    typeof value.title !== 'string' ||
    typeof value.type !== 'string'
  ) {
    throw new Error('board card is missing required fields')
  }
  return {
    id: value.id,
    title: value.title,
    type: value.type,
    epicId: typeof value.epicId === 'string' ? value.epicId : undefined,
    epicTitle: typeof value.epicTitle === 'string' ? value.epicTitle : undefined,
  }
}

// Re-exported so screens can catch it without importing the problem module directly.
export { ApiError }
