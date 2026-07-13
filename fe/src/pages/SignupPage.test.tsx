import { render, screen } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest'
import { SignupPage } from './SignupPage'
import { ApiError } from '@/api/auth'
import { AuthContext, type AuthContextValue, type AuthStatus } from '@/auth/auth-context'

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/auth')>()),
  signup: vi.fn(),
}))

import { signup } from '@/api/auth'
const signupMock = signup as Mock

function authValue(status: AuthStatus): AuthContextValue {
  return {
    status,
    user: status === 'authenticated' ? { id: 'u1', email: 'me@example.com' } : null,
    login: () => Promise.resolve(),
    logout: () => Promise.resolve(),
  }
}

function renderPage(status: AuthStatus = 'unauthenticated') {
  render(
    <AuthContext value={authValue(status)}>
      <MemoryRouter initialEntries={['/signup']}>
        <Routes>
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/" element={<div data-testid="home">home</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
}

async function fillAndSubmit(user: UserEvent) {
  await user.type(screen.getByLabelText(/email/i), 'a@b.com')
  await user.type(screen.getByLabelText(/password/i), 'password1')
  await user.click(screen.getByRole('button', { name: /sign up/i }))
}

describe('SignupPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('render_shouldRedirectToRoot_whenAlreadyAuthenticated', async () => {
    renderPage('authenticated')

    expect(await screen.findByTestId('home')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /sign up/i })).not.toBeInTheDocument()
  })

  it('submit_shouldSwapToConfirmation_whenSuccess', async () => {
    signupMock.mockResolvedValue({
      id: 'u1',
      email: 'a@b.com',
      emailVerified: false,
      createdAt: '2026-07-12T00:00:00Z',
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user)

    expect(signupMock).toHaveBeenCalledWith({ email: 'a@b.com', password: 'password1' })
    expect(await screen.findByRole('status')).toHaveTextContent('a@b.com')
    expect(screen.getByRole('link', { name: /back to log in/i })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('button', { name: /sign up/i })).not.toBeInTheDocument()
  })

  it.each([
    [400, 'Password does not meet the strength requirements.'],
    [409, 'An account with this email already exists.'],
  ])('submit_shouldRenderBackendDetailAndKeepForm_whenStatus%i', async (status, detail) => {
    signupMock.mockRejectedValue(new ApiError(status, detail))
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(detail)
    expect(screen.getByRole('button', { name: /sign up/i })).toBeInTheDocument()
  })

  it('submit_shouldDisableButton_whilePending', async () => {
    signupMock.mockReturnValue(new Promise(() => {}))
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user)

    expect(screen.getByRole('button', { name: /creating account/i })).toBeDisabled()
  })
})
