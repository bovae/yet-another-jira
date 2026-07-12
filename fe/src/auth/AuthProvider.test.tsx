import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { AUTH_UNAUTHORIZED_EVENT, TOKEN_KEY } from '@/api/client'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './auth-context'

vi.mock('@/api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

import { fetchMe, logout } from '@/api/auth'
const fetchMeMock = fetchMe as Mock
const logoutMock = logout as Mock

function Consumer() {
  const { status, user, logout: doLogout } = useAuth()
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="email">{user?.email ?? ''}</span>
      <button type="button" onClick={() => void doLogout()}>
        Log out
      </button>
    </div>
  )
}

function renderProvider() {
  return render(
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

  // --- logout ---

  it('logout_shouldClearSession_evenWhenRequestFails', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    fetchMeMock.mockResolvedValue(ME)
    logoutMock.mockRejectedValue(new Error('logout failed: 500'))

    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(screen.getByTestId('email')).toHaveTextContent('')
  })

  // --- global 401 ---

  it('unauthorizedEvent_shouldClearSession_whenDispatched', async () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token')
    fetchMeMock.mockResolvedValue(ME)

    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))

    act(() => {
      window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
    })

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'))
    expect(screen.getByTestId('email')).toHaveTextContent('')
  })
})
