import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { boardPath, getBoard, type BoardView } from './board'
import { ApiError } from './problem'
import { jsonResponse, problemResponse } from '@/test/helpers'

const TEAM_ID = 't1'

const BOARD: BoardView = {
  columns: [
    {
      state: 'new',
      cards: [{ id: 'k1', title: 'A card', type: 'bug', epicId: 'e1', epicTitle: 'Onboarding' }],
    },
    { state: 'done', cards: [] },
  ],
}

describe('getBoard', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('requests the team board path with no query when no filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ columns: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await getBoard(TEAM_ID)

    expect(fetchMock.mock.calls[0][0]).toBe(boardPath(TEAM_ID))
  })

  it('returns the parsed board on a well-formed response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(BOARD)))

    await expect(getBoard(TEAM_ID)).resolves.toEqual(BOARD)
  })

  it('sends only non-blank filters as query params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ columns: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await getBoard(TEAM_ID, { type: 'bug', epicId: '', q: '  ' })

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.searchParams.get('type')).toBe('bug')
    expect(url.searchParams.has('epicId')).toBe(false)
    expect(url.searchParams.has('q')).toBe(false)
  })

  it('sends all three filters together when set', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ columns: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await getBoard(TEAM_ID, { type: 'bug', epicId: 'e1', q: 'login' })

    const url = new URL(fetchMock.mock.calls[0][0] as string, 'http://localhost')
    expect(url.searchParams.get('type')).toBe('bug')
    expect(url.searchParams.get('epicId')).toBe('e1')
    expect(url.searchParams.get('q')).toBe('login')
  })

  it('drops a non-string epicId/epicTitle to undefined', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          columns: [{ state: 'new', cards: [{ id: '1', title: 'T', type: 'fix', epicId: 42 }] }],
        }),
      ),
    )

    const board = await getBoard(TEAM_ID)

    expect(board.columns[0].cards[0].epicId).toBeUndefined()
    expect(board.columns[0].cards[0].epicTitle).toBeUndefined()
  })

  it('throws an ApiError carrying the problem detail on a non-success status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(404, 'Team not found.')))

    const error = await getBoard(TEAM_ID).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).message).toBe('Team not found.')
  })

  it('throws when the columns array is missing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ nope: true })))

    await expect(getBoard(TEAM_ID)).rejects.toThrow(/columns array/)
  })

  it('throws when a card is missing required fields', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ columns: [{ state: 'new', cards: [{ id: '1', title: 'T' }] }] }),
        ),
    )

    await expect(getBoard(TEAM_ID)).rejects.toThrow(/required fields/)
  })
})
