import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  AUTH_LOGIN_PATH,
  AUTH_LOGOUT_PATH,
  AUTH_ME_PATH,
  AUTH_RESEND_PATH,
  AUTH_SIGNUP_PATH,
  AUTH_VERIFY_PATH,
  GENERIC_ERROR_MESSAGE,
  fetchMe,
  login,
  logout,
  resend,
  signup,
  verify,
} from './auth'
import { jsonResponse, problemResponse } from '@/test/helpers'

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

  it('login_shouldThrowApiErrorWithDetailAndStatus_whenNonSuccessStatus', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse(401, 'Invalid email or password.')),
    )

    const error = await login({ email: 'a@b.com', password: 'bad' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).message).toBe('Invalid email or password.')
  })

  it('login_shouldThrow_whenPayloadMalformed', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ tokenType: 'Bearer' })))

    await expect(login({ email: 'a@b.com', password: 'pw' })).rejects.toThrow(
      'login response is missing required fields',
    )
  })

  // --- ApiError problem-detail parsing ---

  it('problemError_shouldCarryDetail_whenBodyIsProblemJson', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse(409, 'Email already registered.')),
    )

    const error = await signup({ email: 'a@b.com', password: 'password1' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Email already registered.')
  })

  it('problemError_shouldFallBackToGenericMessage_whenBodyUnparseable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not json', { status: 500 })))

    const error = await signup({ email: 'a@b.com', password: 'password1' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(500)
    expect((error as ApiError).message).toBe(GENERIC_ERROR_MESSAGE)
  })

  // --- signup ---

  it('signup_shouldPostCredentialsAndReturnTypedResponse_whenSuccess', async () => {
    const body = {
      id: 'u1',
      email: 'a@b.com',
      emailVerified: false,
      createdAt: '2026-07-12T00:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body, { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await signup({ email: 'a@b.com', password: 'password1' })

    expect(result).toEqual(body)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(AUTH_SIGNUP_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ email: 'a@b.com', password: 'password1' })
  })

  // --- verify ---

  it('verify_shouldPostTokenAndReturnTypedResponse_whenSuccess', async () => {
    const body = {
      verified: true,
      message: 'Your email is verified. Please log in.',
      next: 'login',
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
    vi.stubGlobal('fetch', fetchMock)

    const result = await verify('tok-123')

    expect(result).toEqual(body)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(AUTH_VERIFY_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ token: 'tok-123' })
  })

  it('verify_shouldThrowApiErrorWithStatus_whenGone', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse(410, 'This link has expired.')),
    )

    const error = await verify('stale').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(410)
    expect((error as ApiError).message).toBe('This link has expired.')
  })

  // --- resend ---

  it('resend_shouldPostEmailAndReturnMessage_whenAccepted', async () => {
    const body = { message: 'If an unverified account exists, a new email has been sent.' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await resend('a@b.com')

    expect(result).toEqual(body)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe(AUTH_RESEND_PATH)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ email: 'a@b.com' })
  })

  it('resend_shouldThrowApiErrorWithStatus_whenRateLimited', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse(429, 'Too many requests. Try again later.')),
    )

    const error = await resend('a@b.com').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(429)
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
