import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'
import { AppShell } from './AppShell'

function authenticated(logout: () => Promise<void>): AuthContextValue {
  return {
    status: 'authenticated',
    user: { id: 'u1', email: 'user@example.com' },
    login: () => Promise.resolve(),
    logout,
  }
}

function renderShell(value: AuthContextValue) {
  return render(
    <AuthContext value={value}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<div data-testid="page">board</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext>,
  )
}

describe('AppShell user menu', () => {
  it('userMenu_shouldRevealEmailAndLogout_whenOpened', async () => {
    renderShell(authenticated(() => Promise.resolve()))

    await userEvent.click(screen.getByRole('button', { name: /user menu/i }))

    expect(await screen.findByRole('menuitem', { name: 'Log out' })).toBeInTheDocument()
    // Email shows in both the trigger and the menu label once open.
    expect(screen.getAllByText('user@example.com').length).toBeGreaterThanOrEqual(2)
  })

  it('userMenu_shouldFireLogout_whenLogoutSelected', async () => {
    const logout = vi.fn(() => Promise.resolve())
    renderShell(authenticated(logout))

    await userEvent.click(screen.getByRole('button', { name: /user menu/i }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })
})
