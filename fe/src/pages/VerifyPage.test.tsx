import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest'
import { VerifyPage } from './VerifyPage'
import { ApiError } from '../api/auth'

// Stub the network calls; keep ApiError real for the page's instanceof check. The mock path is
// relative (not the @ alias) to match the repo's other API mocks and avoid an alias-resolution race
// with react-query's first-run dep optimization.
vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  verify: vi.fn(),
  resend: vi.fn(),
}))

import { verify } from '../api/auth'
const verifyMock = verify as Mock

function createQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
}

function renderVerifyAt(path: string) {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/verify-error" element={<VerifyPage variant="error" />} />
          <Route path="/login" element={<div data-testid="login">login</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('VerifyPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('render_shouldShowSuccessWithLoginLink_whenTokenValid', async () => {
    verifyMock.mockResolvedValue({
      verified: true,
      message: 'Your email is verified. Please log in.',
      next: 'login',
    })

    renderVerifyAt('/verify?token=good')

    expect(await screen.findByText(/your email is verified/i)).toBeInTheDocument()
    expect(verifyMock).toHaveBeenCalledWith('good')
    expect(screen.getByRole('link', { name: /continue to log in/i })).toHaveAttribute(
      'href',
      '/login',
    )
  })

  it('render_shouldShowErrorWithResend_whenTokenGone', async () => {
    verifyMock.mockRejectedValue(new ApiError(410, 'This link has expired.'))

    renderVerifyAt('/verify?token=stale')

    expect(await screen.findByRole('alert')).toHaveTextContent('This link has expired.')
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
  })

  it('render_shouldShowErrorWithoutFetch_whenTokenMissing', () => {
    renderVerifyAt('/verify')

    expect(screen.getByRole('alert')).toHaveTextContent(/invalid or has expired/i)
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
    expect(verifyMock).not.toHaveBeenCalled()
  })

  it('render_shouldShowErrorWithoutFetch_whenErrorVariant', () => {
    renderVerifyAt('/verify-error')

    expect(screen.getByRole('alert')).toHaveTextContent(/invalid or has expired/i)
    expect(verifyMock).not.toHaveBeenCalled()
  })
})
