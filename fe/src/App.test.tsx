import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'

const UNAUTHENTICATED: AuthContextValue = {
  status: 'unauthenticated',
  user: null,
  login: () => Promise.resolve(),
  logout: () => Promise.resolve(),
}

function renderAppAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(
    <AuthContext value={UNAUTHENTICATED}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    </AuthContext>,
  )
}

/**
 * Routing coverage for the public auth routes: each renders its real screen (not the "coming in a
 * later milestone" placeholder) and stays public for an unauthenticated visitor.
 */
describe('App public auth routes', () => {
  it('route_shouldRenderLoginScreen_whenLogin', () => {
    renderAppAt('/login')

    expect(screen.getByRole('button', { name: /^log in$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /create an account/i })).toBeInTheDocument()
    expect(screen.queryByText(/coming in a later milestone/i)).not.toBeInTheDocument()
  })

  it('route_shouldRenderSignupScreen_whenSignup', () => {
    renderAppAt('/signup')

    expect(screen.getByRole('button', { name: /^sign up$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /log in/i })).toBeInTheDocument()
    expect(screen.queryByText(/coming in a later milestone/i)).not.toBeInTheDocument()
  })

  it('route_shouldRenderVerifyErrorState_whenVerifyWithoutToken', () => {
    renderAppAt('/verify')

    expect(screen.getByRole('alert')).toHaveTextContent(/invalid or has expired/i)
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
    expect(screen.queryByText(/coming in a later milestone/i)).not.toBeInTheDocument()
  })

  it('route_shouldRenderVerifyErrorState_whenVerifyError', () => {
    renderAppAt('/verify-error')

    expect(screen.getByRole('alert')).toHaveTextContent(/invalid or has expired/i)
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
  })
})
