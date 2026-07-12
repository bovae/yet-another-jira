import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EPICS_PATH, createEpic, deleteEpic, listEpics, updateEpic } from './epics'
import { ApiError } from './problem'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** RFC 9457 problem body — what the backend returns on failures. */
function problemResponse(detail: string, status: number): Response {
  return jsonResponse({ type: 'about:blank', title: 'Error', status, detail }, status)
}

const EPIC = {
  id: 'e1',
  teamId: 't1',
  title: 'Checkout',
  description: 'Redesign the checkout flow',
  createdAt: '2026-07-12T00:00:00Z',
  modifiedAt: '2026-07-12T00:00:00Z',
}

describe('epics API', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('listEpics_shouldGetPlainPath_whenNoTeamId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([EPIC]))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listEpics()

    expect(result).toEqual([EPIC])
    expect(fetchMock.mock.calls[0][0]).toBe(EPICS_PATH)
  })

  it('listEpics_shouldAppendTeamIdQueryParam_whenTeamIdGiven', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([EPIC]))
    vi.stubGlobal('fetch', fetchMock)

    await listEpics('t1')

    expect(fetchMock.mock.calls[0][0]).toBe(`${EPICS_PATH}?teamId=t1`)
  })

  it('listEpics_shouldParseMissingDescriptionAsUndefined', async () => {
    // description: undefined is dropped by JSON.stringify, so the wire payload has no description key.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse([{ ...EPIC, description: undefined }])),
    )

    const result = await listEpics()

    expect(result[0].description).toBeUndefined()
  })

  it('createEpic_shouldPostTeamIdTitleDescription_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(EPIC, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createEpic({ teamId: 't1', title: 'Checkout', description: 'desc' })

    expect(result).toEqual(EPIC)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(EPICS_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      teamId: 't1',
      title: 'Checkout',
      description: 'desc',
    })
  })

  it('createEpic_shouldOmitDescription_whenEmpty', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(EPIC, 201))
    vi.stubGlobal('fetch', fetchMock)

    await createEpic({ teamId: 't1', title: 'Checkout', description: '' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(JSON.parse(init.body as string)).toEqual({ teamId: 't1', title: 'Checkout' })
  })

  it('createEpic_shouldThrowApiErrorWithDetail_whenValidationFails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('Title is required.', 400)))

    const error = await createEpic({ teamId: 't1', title: '' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('Title is required.')
  })

  it('updateEpic_shouldPutTitleAndDescriptionWithoutTeam_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(EPIC))
    vi.stubGlobal('fetch', fetchMock)

    await updateEpic('e1', { title: 'New title', description: 'new' })

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${EPICS_PATH}/e1`)
    expect(init.method).toBe('PUT')
    const body = JSON.parse(init.body as string) as Record<string, unknown>
    expect(body).toEqual({ title: 'New title', description: 'new' })
    expect(body).not.toHaveProperty('teamId')
  })

  it('deleteEpic_shouldDeleteIdPath_whenNoContent', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteEpic('e1')).resolves.toBeUndefined()
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${EPICS_PATH}/e1`)
    expect(init.method).toBe('DELETE')
  })

  it('deleteEpic_shouldThrowApiErrorWithDetail_whenReferenced', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse('Epic is referenced by tickets.', 409)),
    )

    const error = await deleteEpic('e1').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Epic is referenced by tickets.')
  })
})
