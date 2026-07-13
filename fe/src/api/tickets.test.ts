import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  TICKETS_PATH,
  createTicket,
  deleteTicket,
  getTicket,
  listTickets,
  patchTicketState,
  ticketStateLabel,
  ticketTypeLabel,
  updateTicket,
} from './tickets'
import { ApiError } from './problem'
import { jsonResponse, problemResponse } from '@/test/helpers'

const TICKET = {
  id: 'k1',
  teamId: 't1',
  epicId: 'e1',
  type: 'bug',
  state: 'new',
  title: 'Login broken',
  body: 'Steps to reproduce…',
  createdBy: 'u1',
  createdByEmail: null,
  createdAt: '2026-07-12T00:00:00Z',
  modifiedAt: '2026-07-12T00:00:00Z',
}

const INPUT = {
  teamId: 't1',
  type: 'bug',
  state: 'new',
  epicId: 'e1',
  title: 'Login broken',
  body: 'Steps to reproduce…',
}

describe('tickets API', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('ticketTypeLabel_shouldMapKnownCode_andFallBackToRaw', () => {
    expect(ticketTypeLabel('feature')).toBe('Feature')
    expect(ticketTypeLabel('mystery')).toBe('mystery')
  })

  it('ticketStateLabel_shouldSpaceUnderscoredCode', () => {
    expect(ticketStateLabel('ready_for_implementation')).toBe('Ready for implementation')
  })

  it('listTickets_shouldGetPlainPath_whenNoTeamId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([TICKET]))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listTickets()

    expect(result).toEqual([TICKET])
    expect(fetchMock.mock.calls[0][0]).toBe(TICKETS_PATH)
  })

  it('listTickets_shouldAppendTeamIdQueryParam_whenTeamIdGiven', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([TICKET]))
    vi.stubGlobal('fetch', fetchMock)

    await listTickets('t1')

    expect(fetchMock.mock.calls[0][0]).toBe(`${TICKETS_PATH}?teamId=t1`)
  })

  it('listTickets_shouldParseMissingEpicIdAsUndefined', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse([{ ...TICKET, epicId: undefined }])),
    )

    const result = await listTickets()

    expect(result[0].epicId).toBeUndefined()
  })

  it('listTickets_shouldReject_whenResponseMissingFields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([{ id: 'k1' }])))

    await expect(listTickets()).rejects.toThrow(/missing required fields/)
  })

  it('getTicket_shouldGetIdPath', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TICKET))
    vi.stubGlobal('fetch', fetchMock)

    const result = await getTicket('k1')

    expect(result).toEqual(TICKET)
    expect(fetchMock.mock.calls[0][0]).toBe(`${TICKETS_PATH}/k1`)
  })

  it('getTicket_shouldThrowApiErrorWithStatus_whenNotFound', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(404, 'Ticket not found.')))

    const error = await getTicket('nope').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
  })

  it('createTicket_shouldPostFullBody_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TICKET, { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createTicket(INPUT)

    expect(result).toEqual(TICKET)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(TICKETS_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      teamId: 't1',
      type: 'bug',
      state: 'new',
      epicId: 'e1',
      title: 'Login broken',
      body: 'Steps to reproduce…',
    })
  })

  it('createTicket_shouldOmitEpicId_whenEmpty', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TICKET, { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await createTicket({ ...INPUT, epicId: '' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(JSON.parse(init.body as string)).not.toHaveProperty('epicId')
  })

  it('createTicket_shouldThrowApiErrorWithDetail_whenValidationFails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(400, 'Title is required.')))

    const error = await createTicket({ ...INPUT, title: '' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('Title is required.')
  })

  it('updateTicket_shouldPutFullBodyToIdPath_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TICKET))
    vi.stubGlobal('fetch', fetchMock)

    await updateTicket('k1', { ...INPUT, epicId: '' })

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${TICKETS_PATH}/k1`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).not.toHaveProperty('epicId')
  })

  it('patchTicketState_shouldPatchStateOnlyToIdPath_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...TICKET, state: 'in_progress' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await patchTicketState('k1', 'in_progress')

    expect(result.state).toBe('in_progress')
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${TICKETS_PATH}/k1`)
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body as string)).toEqual({ state: 'in_progress' })
  })

  it('patchTicketState_shouldThrowApiErrorWithStatus_whenRejected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(400, 'Invalid state.')))

    const error = await patchTicketState('k1', 'nope').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
  })

  it('deleteTicket_shouldDeleteIdPath_whenNoContent', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteTicket('k1')).resolves.toBeUndefined()
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${TICKETS_PATH}/k1`)
    expect(init.method).toBe('DELETE')
  })
})
