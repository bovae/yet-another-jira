import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, AUTH_UNAUTHORIZED_EVENT, REQUEST_TIMEOUT_MS, TOKEN_KEY } from './client'

describe('apiFetch', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    localStorage.clear()
  })

  it('does not send an X-Correlation-Id header (the backend owns correlation ids)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/v1/mock/board')

    const init = (fetchMock.mock.calls[0][1] ?? {}) as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.has('X-Correlation-Id')).toBe(false)
  })

  it('passes caller-supplied headers through to fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/v1/mock/board', { headers: { 'X-Test': 'yes' } })

    const init = (fetchMock.mock.calls[0][1] ?? {}) as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.get('X-Test')).toBe('yes')
  })

  it('attaches Authorization header when token exists in localStorage', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    localStorage.setItem(TOKEN_KEY, 'my-jwt-token')

    await apiFetch('/api/v1/mock/board')

    const init = (fetchMock.mock.calls[0][1] ?? {}) as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer my-jwt-token')
  })

  it('does not attach Authorization header when no token in localStorage', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/v1/mock/board')

    const init = (fetchMock.mock.calls[0][1] ?? {}) as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.has('Authorization')).toBe(false)
  })

  it('clears the token and dispatches auth:unauthorized on a 401 that carried a token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    localStorage.setItem(TOKEN_KEY, 'expired-token')
    const listener = vi.fn()
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, listener)

    try {
      await apiFetch('/api/v1/mock/board')

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
      expect(listener).toHaveBeenCalledTimes(1)
    } finally {
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, listener)
    }
  })

  it('does not clear or dispatch on a 401 that carried no token (credential failure)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const listener = vi.fn()
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, listener)

    try {
      await apiFetch('/api/v1/auth/login', { method: 'POST' })

      expect(listener).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, listener)
    }
  })

  it('aborts the request after the timeout elapses', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn((_path: string, init?: RequestInit) => {
      return new Promise<Response>((_resolve, reject) => {
        const signal = init?.signal
        signal?.addEventListener('abort', () => reject(new Error('aborted')))
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const promise = apiFetch('/api/v1/mock/board')
    const signal = (fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal
    expect(signal.aborted).toBe(false)

    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS)
    expect(signal.aborted).toBe(true)
    await expect(promise).rejects.toThrow()
  })

  it('propagates a caller-supplied abort signal to the fetch request', async () => {
    const fetchMock = vi.fn((_path: string, init?: RequestInit) => {
      return new Promise<Response>((_resolve, reject) => {
        const signal = init?.signal
        signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const controller = new AbortController()
    const promise = apiFetch('/api/v1/mock/board', { signal: controller.signal })
    const passedSignal = (fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal
    expect(passedSignal.aborted).toBe(false)

    controller.abort()
    expect(passedSignal.aborted).toBe(true)
    await expect(promise).rejects.toThrow()
  })
})
