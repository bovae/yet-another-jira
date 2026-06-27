import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMockBoard, MOCK_BOARD_PATH, type BoardView } from './board'

function stubFetch(body: unknown, init?: ResponseInit) {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(new Response(JSON.stringify(body), { status: 200, ...init }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('getMockBoard', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('requests the mock board path', async () => {
    const fetchMock = stubFetch({ columns: [] })

    await getMockBoard()

    expect(fetchMock.mock.calls[0][0]).toBe(MOCK_BOARD_PATH)
  })

  it('returns the parsed board on a well-formed response', async () => {
    const board: BoardView = {
      columns: [
        {
          state: 'new',
          label: 'New',
          cards: [{ id: 'YAJ-1', title: 'A card', type: 'bug', epic: 'Onboarding' }],
        },
        { state: 'done', label: 'Done', cards: [] },
      ],
    }
    stubFetch(board)

    await expect(getMockBoard()).resolves.toEqual(board)
  })

  it('drops a non-string epic to undefined', async () => {
    stubFetch({
      columns: [
        { state: 'new', label: 'New', cards: [{ id: '1', title: 'T', type: 'fix', epic: 42 }] },
      ],
    })

    const board = await getMockBoard()

    expect(board.columns[0].cards[0].epic).toBeUndefined()
  })

  it('throws on a non-success status', async () => {
    stubFetch({}, { status: 500 })

    await expect(getMockBoard()).rejects.toThrow(/board fetch failed: 500/)
  })

  it('throws when the columns array is missing', async () => {
    stubFetch({ nope: true })

    await expect(getMockBoard()).rejects.toThrow(/columns array/)
  })

  it('throws when a card has an unknown type', async () => {
    stubFetch({
      columns: [{ state: 'new', label: 'New', cards: [{ id: '1', title: 'T', type: 'epic' }] }],
    })

    await expect(getMockBoard()).rejects.toThrow(/required fields/)
  })
})
