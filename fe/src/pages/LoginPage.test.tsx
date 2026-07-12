import { render, screen } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import { ApiError, type Credentials } from '@/api/auth'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'

// ResendVerification (revealed on 403) imports resend from the API module — stub it so a rendered
// resend form never hits the network. ApiError stays real for the LoginPage instanceof checks.
vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/auth')>()),
  resend: vi.fn(),
}))

function authValue(login: (credentials: Credentials) => Promise<void>): AuthContextValue {
  return {
    status: 'unauthenticated',
    user: null,
    login,
    logout: () => Promise.resolve(),
  }
}

function renderLoginAt(state: unknown, login: (credentials: Credentials) => Promise<void>): void {
  render(
    <AuthContext value={authValue(login)}>
      <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div data-testid="home">home</div>} />
          <Route path="/teams" element={<div data-testid="teams">teams</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
}

async function fillAndSubmit(user: UserEvent) {
  await user.type(screen.getByLabelText(/email/i), 'a@b.com')
  await user.type(screen.getByLabelText(/password/i), 'password1')
  await user.click(screen.getByRole('button', { name: /^log in$/i }))
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('submit_shouldRedirectToPreservedLocation_whenSuccess', async () => {
    const login = vi.fn(() => Promise.resolve())
    const user = userEvent.setup()
    renderLoginAt({ from: { pathname: '/teams' } }, login)

    await fillAndSubmit(user)

    expect(login).toHaveBeenCalledWith({ email: 'a@b.com', password: 'password1' })
    expect(await screen.findByTestId('teams')).toBeInTheDocument()
  })

  it('submit_shouldRedirectToRoot_whenNoPreservedLocation', async () => {
    const login = vi.fn(() => Promise.resolve())
    const user = userEvent.setup()
    renderLoginAt(null, login)

    await fillAndSubmit(user)

    expect(await screen.findByTestId('home')).toBeInTheDocument()
  })

  it('submit_shouldRenderBackendDetail_whenInvalidCredentials', async () => {
    const login = vi.fn(() => Promise.reject(new ApiError(401, 'Invalid email or password.')))
    const user = userEvent.setup()
    renderLoginAt(null, login)

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.queryByTestId('home')).not.toBeInTheDocument()
  })

  it('submit_shouldRevealResend_whenUnverified', async () => {
    const login = vi.fn(() => Promise.reject(new ApiError(403, 'Please verify your email.')))
    const user = userEvent.setup()
    renderLoginAt(null, login)

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Please verify your email.')
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
  })

  it('submit_shouldDisableButton_whilePending', async () => {
    const login = vi.fn(() => new Promise<void>(() => {}))
    const user = userEvent.setup()
    renderLoginAt(null, login)

    await fillAndSubmit(user)

    expect(screen.getByRole('button', { name: /logging in/i })).toBeDisabled()
  })
})
