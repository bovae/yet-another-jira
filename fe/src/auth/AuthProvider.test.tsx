import { useState } from 'react'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { AUTH_UNAUTHORIZED_EVENT, TOKEN_KEY } from '@/api/client'
import { renderWithClient } from '@/test/helpers'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './auth-context'

vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

import { fetchMe, login, logout } from '@/api/auth'
const fetchMeMock = fetchMe as Mock
const loginMock = login as Mock
const logoutMock = logout as Mock

function Consumer() {
  const { status, user, login: doLogin, logout: doLogout } = useAuth()
  const [loginError, setLoginError] = useState('')
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="email">{user?.email ?? ''}</span>
      <span data-testid="login-error">{loginError}</span>
      <button
        type="button"
        onClick={() =>
          void doLogin({ email: 'a@b.com', password: 'pw' }).catch(() => setLoginError('rejected'))
        }
      >
        Log in
      </button>
      <button type="button" onClick={() => void doLogout()}>
        Log out
      </button>
    </div>
  )
}

function renderProvider() {
  return renderWithClient(
    <AuthProvider>
      <Consumer />
    </AuthProvider>,
  )
}

const ME = { id: 'u1', email: 'user@example.com', emailVerified: true }

describe('AuthProvider', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
  })

  // --- boot hydration ---

  it('hydration_shouldBeUnauthenticatedWithoutCallingMe_whenNoToken', async () => {
    renderProvider()

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(fetchMeMock).not.toHaveBeenCalled()
  })

  it('hydration_shouldBeAuthenticated_whenTokenValid', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    fetchMeMock.mockResolvedValue(ME)

    renderProvider()

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    expect(screen.getByTestId('email')).toHaveTextContent('user@example.com')
  })

  it('hydration_shouldClearTokenAndBeUnauthenticated_whenTokenRejected', async () => {
    localStorage.setItem(TOKEN_KEY, 'bad-token')
    fetchMeMock.mockRejectedValue(new Error('me fetch failed: 401'))

    renderProvider()

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  // --- login ---

  it('login_shouldClearStoredToken_whenMeFailsAfterTokenStored', async () => {
    loginMock.mockResolvedValue({ accessToken: 'jwt', tokenType: 'Bearer', expiresInSeconds: 900 })
    fetchMeMock.mockRejectedValue(new Error('me fetch failed: 500'))
    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    // login rethrows the /me failure, and the half-stored token is cleared so no stranded session.
    await waitFor(() => expect(screen.getByTestId('login-error')).toHaveTextContent('rejected'))
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  // --- logout ---

  it('logout_shouldClearSessionAndCache_evenWhenRequestFails', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    fetchMeMock.mockResolvedValue(ME)
    logoutMock.mockRejectedValue(new Error('logout failed: 500'))

    const { client } = renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    client.setQueryData(['board', 't1'], { columns: [] })

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(screen.getByTestId('email')).toHaveTextContent('')
    // Cached query data is purged so the next account never sees the previous session's board.
    expect(client.getQueryData(['board', 't1'])).toBeUndefined()
  })

  // --- global 401 ---

  it('unauthorizedEvent_shouldClearSessionAndCache_whenDispatched', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    fetchMeMock.mockResolvedValue(ME)

    const { client } = renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    client.setQueryData(['board', 't1'], { columns: [] })

    act(() => {
      window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
    })

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(screen.getByTestId('email')).toHaveTextContent('')
    expect(client.getQueryData(['board', 't1'])).toBeUndefined()
  })
})
