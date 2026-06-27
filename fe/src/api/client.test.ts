import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, REQUEST_TIMEOUT_MS } from './client'

describe('apiFetch', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
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
})
