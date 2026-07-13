import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './problem'
import { TEAMS_PATH, createTeam, deleteTeam, listTeams, renameTeam } from './teams'
import { jsonResponse, problemResponse } from '@/test/helpers'

const TEAM = {
  id: 't1',
  name: 'Platform',
  createdAt: '2026-07-12T00:00:00Z',
  modifiedAt: '2026-07-12T00:00:00Z',
}

describe('teams API', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('listTeams_shouldGetAndParseArray_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([TEAM]))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listTeams()

    expect(result).toEqual([TEAM])
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(TEAMS_PATH)
    expect(init.method).toBeUndefined()
  })

  it('listTeams_shouldThrow_whenPayloadNotArray', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({})))

    await expect(listTeams()).rejects.toThrow('teams response is not an array')
  })

  it('createTeam_shouldPostNameAndReturnTeam_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TEAM, { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createTeam('Platform')

    expect(result).toEqual(TEAM)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(TEAMS_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ name: 'Platform' })
  })

  it('createTeam_shouldThrowApiErrorWithDetail_whenDuplicate', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse(409, 'A team with this name already exists.')),
    )

    const error = await createTeam('Platform').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('A team with this name already exists.')
  })

  it('renameTeam_shouldPutNameToIdPath_whenSuccess', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...TEAM, name: 'Core' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await renameTeam('t1', 'Core')

    expect(result.name).toBe('Core')
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${TEAMS_PATH}/t1`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ name: 'Core' })
  })

  it('deleteTeam_shouldDeleteIdPath_whenNoContent', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteTeam('t1')).resolves.toBeUndefined()
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(`${TEAMS_PATH}/t1`)
    expect(init.method).toBe('DELETE')
  })

  it('deleteTeam_shouldThrowApiErrorWithDetail_whenReferenced', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(409, 'Team still has epics.')))

    const error = await deleteTeam('t1').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Team still has epics.')
  })
})
