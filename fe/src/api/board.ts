/** Mock board API: getMockBoard() fetches GET /api/v1/mock/board via apiFetch (correlation id + timeout). */
import { apiFetch } from './client'

/** A single ticket card on the board. */
export interface BoardCard {
  id: string
  title: string
  type: 'bug' | 'feature' | 'fix'
  epic?: string
}

/** A board column mapped to one canonical ticket state. */
export interface BoardColumn {
  state: string
  label: string
  cards: BoardCard[]
}

/** The full board: an ordered collection of columns. */
export interface BoardView {
  columns: BoardColumn[]
}

/** Backend route serving the hardcoded sample board. */
export const MOCK_BOARD_PATH = '/api/v1/mock/board'

/**
 * Fetch the mock board from the backend.
 *
 * @param options.signal cancellation signal (e.g. from TanStack Query's `queryFn` context) so an
 *   unmounted or superseded query aborts the in-flight request instead of leaking it
 * @throws Error when the response is not a success status or the payload is malformed
 * @throws DOMException (name `AbortError`) when the request times out or is aborted
 */
export async function getMockBoard(options: { signal?: AbortSignal } = {}): Promise<BoardView> {
  const res = await apiFetch(MOCK_BOARD_PATH, { signal: options.signal })
  if (!res.ok) {
    throw new Error(`board fetch failed: ${res.status}`)
  }
  return parseBoardView(await res.json())
}

const CARD_TYPES: ReadonlySet<string> = new Set<BoardCard['type']>(['bug', 'feature', 'fix'])

/**
 * Narrow an untyped payload to a {@link BoardView}, so a malformed body fails fast here with a
 * clear message instead of surfacing later as an opaque render-time error.
 */
function parseBoardView(payload: unknown): BoardView {
  if (!isObject(payload) || !Array.isArray(payload.columns)) {
    throw new Error('board response is missing a columns array')
  }
  return { columns: payload.columns.map(parseColumn) }
}

function parseColumn(value: unknown): BoardColumn {
  if (
    !isObject(value) ||
    typeof value.state !== 'string' ||
    typeof value.label !== 'string' ||
    !Array.isArray(value.cards)
  ) {
    throw new Error('board column is missing required fields')
  }
  return { state: value.state, label: value.label, cards: value.cards.map(parseCard) }
}

function parseCard(value: unknown): BoardCard {
  if (
    !isObject(value) ||
    typeof value.id !== 'string' ||
    typeof value.title !== 'string' ||
    !isCardType(value.type)
  ) {
    throw new Error('board card is missing or has invalid required fields')
  }
  return {
    id: value.id,
    title: value.title,
    type: value.type,
    epic: typeof value.epic === 'string' ? value.epic : undefined,
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isCardType(value: unknown): value is BoardCard['type'] {
  return typeof value === 'string' && CARD_TYPES.has(value)
}
