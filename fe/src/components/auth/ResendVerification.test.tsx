import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest'
import { ResendVerification } from './ResendVerification'
import { ApiError } from '@/api/auth'

// Partial mock: keep ApiError / GENERIC_ERROR_MESSAGE real (the component instanceof-checks ApiError),
// stub only the network call.
vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/auth')>()),
  resend: vi.fn(),
}))

import { resend } from '@/api/auth'
const resendMock = resend as Mock

describe('ResendVerification', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('submit_shouldCallResendAndRenderMessage_whenAccepted', async () => {
    resendMock.mockResolvedValue({ message: 'If an account exists, an email was sent.' })
    const user = userEvent.setup()
    render(<ResendVerification defaultEmail="a@b.com" />)

    await user.click(screen.getByRole('button', { name: /resend verification email/i }))

    expect(resendMock).toHaveBeenCalledWith('a@b.com')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'If an account exists, an email was sent.',
    )
  })

  it('submit_shouldRenderRateLimitMessage_whenRateLimited', async () => {
    resendMock.mockRejectedValue(new ApiError(429, 'Too many requests. Try again later.'))
    const user = userEvent.setup()
    render(<ResendVerification defaultEmail="a@b.com" />)

    await user.click(screen.getByRole('button', { name: /resend verification email/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many requests. Try again later.',
    )
    // Form stays so the user can retry later.
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
  })

  it('submit_shouldDisableButton_whilePending', async () => {
    let resolve: (value: { message: string }) => void = () => {}
    resendMock.mockReturnValue(
      new Promise<{ message: string }>((r) => {
        resolve = r
      }),
    )
    const user = userEvent.setup()
    render(<ResendVerification defaultEmail="a@b.com" />)

    await user.click(screen.getByRole('button', { name: /resend verification email/i }))

    expect(screen.getByRole('button', { name: /sending/i })).toBeDisabled()

    resolve({ message: 'done' })
    await screen.findByRole('status')
  })
})
