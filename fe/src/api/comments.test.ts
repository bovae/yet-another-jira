import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { addComment, commentsPath, listComments } from './comments'
import { ApiError } from './problem'
import { jsonResponse, problemResponse } from '@/test/helpers'

const COMMENT = {
  id: 'c1',
  ticketId: 'k1',
  authorId: 'u1',
  authorEmail: null,
  body: 'First!',
  createdAt: '2026-07-12T00:00:00Z',
}

describe('comments API', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('listComments_shouldGetTicketScopedPath', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([COMMENT]))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listComments('k1')

    expect(result).toEqual([COMMENT])
    expect(fetchMock.mock.calls[0][0]).toBe(commentsPath('k1'))
  })

  it('listComments_shouldReject_whenResponseMissingFields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([{ id: 'c1' }])))

    await expect(listComments('k1')).rejects.toThrow(/missing required fields/)
  })

  it('addComment_shouldPostBody_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(COMMENT, { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await addComment('k1', 'First!')

    expect(result).toEqual(COMMENT)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(commentsPath('k1'))
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ body: 'First!' })
  })

  it('addComment_shouldThrowApiErrorWithDetail_whenRejected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(400, 'Body is required.')))

    const error = await addComment('k1', '').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('Body is required.')
  })
})
