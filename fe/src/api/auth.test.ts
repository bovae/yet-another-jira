import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTH_LOGIN_PATH, AUTH_LOGOUT_PATH, AUTH_ME_PATH, fetchMe, login, logout } from './auth'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('auth API', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  // --- login ---

  it('login_shouldPostCredentialsAndReturnTypedResponse_whenSuccess', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({ accessToken: 'jwt', tokenType: 'Bearer', expiresInSeconds: 900 }),
      )
    vi.stubGlobal('fetch', fetchMock)

    const result = await login({ email: 'a@b.com', password: 'pw' })

    expect(result).toEqual({ accessToken: 'jwt', tokenType: 'Bearer', expiresInSeconds: 900 })
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(AUTH_LOGIN_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ email: 'a@b.com', password: 'pw' })
  })

  it('login_shouldThrow_whenNonSuccessStatus', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await expect(login({ email: 'a@b.com', password: 'bad' })).rejects.toThrow('login failed: 401')
  })

  it('login_shouldThrow_whenPayloadMalformed', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ tokenType: 'Bearer' })))

    await expect(login({ email: 'a@b.com', password: 'pw' })).rejects.toThrow(
      'login response is missing required fields',
    )
  })

  // --- me ---

  it('fetchMe_shouldReturnUser_whenSuccess', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'u1', email: 'a@b.com', emailVerified: true }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await fetchMe()

    expect(result).toEqual({ id: 'u1', email: 'a@b.com', emailVerified: true })
    expect(fetchMock.mock.calls[0][0]).toBe(AUTH_ME_PATH)
  })

  it('fetchMe_shouldThrow_whenNonSuccessStatus', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await expect(fetchMe()).rejects.toThrow('me fetch failed: 401')
  })

  // --- logout ---

  it('logout_shouldResolve_whenNoContent', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(logout()).resolves.toBeUndefined()
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(AUTH_LOGOUT_PATH)
    expect(init.method).toBe('POST')
  })

  it('logout_shouldThrow_whenNonSuccessStatus', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })))

    await expect(logout()).rejects.toThrow('logout failed: 500')
  })
})
