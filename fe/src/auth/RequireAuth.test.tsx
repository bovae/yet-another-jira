import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthContext, type AuthContextValue } from './auth-context'
import { RequireAuth } from './RequireAuth'

function authValue(overrides: Partial<AuthContextValue>): AuthContextValue {
  return {
    status: 'unauthenticated',
    user: null,
    login: () => Promise.resolve(),
    logout: () => Promise.resolve(),
    ...overrides,
  }
}

/** Renders the location's `from` state so the test can assert the intended destination survived. */
function LoginProbe() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from
  return <div data-testid="login">login:{from?.pathname ?? 'none'}</div>
}

function renderGuardedAt(path: string, value: AuthContextValue) {
  return render(
    <AuthContext value={value}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/teams" element={<div data-testid="teams">teams</div>} />
          </Route>
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
}

describe('RequireAuth', () => {
  it('guard_shouldRedirectToLoginPreservingFrom_whenUnauthenticated', () => {
    renderGuardedAt('/teams', authValue({ status: 'unauthenticated' }))

    expect(screen.getByTestId('login')).toHaveTextContent('login:/teams')
    expect(screen.queryByTestId('teams')).not.toBeInTheDocument()
  })

  it('guard_shouldRenderRoute_whenAuthenticated', () => {
    renderGuardedAt(
      '/teams',
      authValue({ status: 'authenticated', user: { id: 'u1', email: 'a@b.com' } }),
    )

    expect(screen.getByTestId('teams')).toBeInTheDocument()
    expect(screen.queryByTestId('login')).not.toBeInTheDocument()
  })

  it('guard_shouldShowLoading_whileHydrating', () => {
    renderGuardedAt('/teams', authValue({ status: 'loading' }))

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByTestId('teams')).not.toBeInTheDocument()
    expect(screen.queryByTestId('login')).not.toBeInTheDocument()
  })
})
