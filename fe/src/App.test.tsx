import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'

// The management screens fetch on mount; stub the API so authenticated routes render their real
// screens instead of hanging in a perpetual loading state.
vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn().mockResolvedValue([]),
}))

const UNAUTHENTICATED: AuthContextValue = {
  status: 'unauthenticated',
  user: null,
  login: () => Promise.resolve(),
  logout: () => Promise.resolve(),
}

const AUTHENTICATED: AuthContextValue = {
  status: 'authenticated',
  user: { id: 'u1', email: 'a@b.com' },
  login: () => Promise.resolve(),
  logout: () => Promise.resolve(),
}

function renderAppAt(path: string, auth: AuthContextValue = UNAUTHENTICATED) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(
    <AuthContext value={auth}>
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

/**
 * Business routes for an authenticated visitor: `/teams` and `/epics` render the real management
 * screens (E13), while `/tickets/:id` is still a later-epic placeholder.
 */
describe('App authenticated business routes', () => {
  it('route_shouldRenderTeamsScreen_whenTeams', async () => {
    renderAppAt('/teams', AUTHENTICATED)

    expect(await screen.findByRole('button', { name: /new team/i })).toBeInTheDocument()
    expect(screen.queryByText(/coming in a later milestone/i)).not.toBeInTheDocument()
  })

  it('route_shouldRenderEpicsScreen_whenEpics', async () => {
    renderAppAt('/epics', AUTHENTICATED)

    expect(await screen.findByRole('button', { name: /new epic/i })).toBeInTheDocument()
    expect(screen.queryByText(/coming in a later milestone/i)).not.toBeInTheDocument()
  })

  it('route_shouldRenderPlaceholder_whenTicket', () => {
    renderAppAt('/tickets/123', AUTHENTICATED)

    expect(screen.getByRole('heading', { name: /ticket/i })).toBeInTheDocument()
    expect(screen.getByText(/coming in a later milestone/i)).toBeInTheDocument()
  })
})
