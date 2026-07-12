import { useState, type FormEvent } from 'react'
import { ApiError, GENERIC_ERROR_MESSAGE, resend } from '@/api/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Status = 'idle' | 'pending' | 'success' | 'error'

/**
 * Self-contained resend-verification action (D5): owns its email field and request lifecycle, and is
 * embedded by the login screen (on a 403) and the verification result/error screens. On the uniform
 * `202` it renders the backend's message verbatim (no account enumeration); on `429` it shows the
 * rate-limit message and keeps the form so the user can retry later.
 *
 * @param defaultEmail pre-fills the field from the surrounding screen (e.g. the address the user just
 *   tried to log in with), saving a re-type.
 */
export function ResendVerification({ defaultEmail = '' }: { defaultEmail?: string }) {
  const [email, setEmail] = useState(defaultEmail)
  const [status, setStatus] = useState<Status>('idle')
  const [message, setMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatus('pending')
    try {
      const response = await resend(email)
      setMessage(response.message)
      setStatus('success')
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
      setStatus('error')
    }
  }

  if (status === 'success') {
    return (
      <p
        className="rounded-md border border-hairline bg-canvas-soft p-4 text-body text-body-sm"
        role="status"
      >
        {message}
      </p>
    )
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)}>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="resend-email">Email</Label>
        <Input
          id="resend-email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </div>
      {status === 'error' ? (
        <p className="text-error text-body-sm" role="alert">
          {message}
        </p>
      ) : null}
      <Button type="submit" variant="outline" disabled={status === 'pending'}>
        {status === 'pending' ? 'Sending…' : 'Resend verification email'}
      </Button>
    </form>
  )
}
