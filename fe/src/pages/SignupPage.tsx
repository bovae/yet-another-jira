import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router'
import { ApiError, GENERIC_ERROR_MESSAGE, signup } from '@/api/auth'
import { useAuth } from '@/auth/auth-context'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const MIN_PASSWORD_LENGTH = 8

type Status = 'idle' | 'pending' | 'error' | 'success'

/**
 * Sign-up screen (D2): plain `useState` form with native email/minLength hints (server authoritative).
 * On `201` it swaps to a check-your-email confirmation for the registered address; on `400`/`409` it
 * keeps the form and renders the backend problem `detail` so the user can correct and resubmit.
 */
export function SignupPage() {
  const { status: authStatus } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [registeredEmail, setRegisteredEmail] = useState('')

  // Already signed in → skip signup and enter the app. After all hooks so order stays stable.
  if (authStatus === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatus('pending')
    try {
      const account = await signup({ email, password })
      setRegisteredEmail(account.email)
      setStatus('success')
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
      setStatus('error')
    }
  }

  if (status === 'success') {
    return (
      <AuthLayout title="Check your email">
        <p className="text-body text-body-sm" role="status">
          We sent a verification link to{' '}
          <span className="text-body-sm-strong text-ink">{registeredEmail}</span>. Follow it to
          activate your account.
        </p>
        <Link to="/login" className="mt-6 inline-block text-link text-body-sm hover:text-link-deep">
          Back to log in
        </Link>
      </AuthLayout>
    )
  }

  const pending = status === 'pending'

  return (
    <AuthLayout title="Sign up">
      <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)}>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <p className="text-mute text-caption">At least {MIN_PASSWORD_LENGTH} characters.</p>
        </div>
        {status === 'error' ? (
          <p className="text-error text-body-sm" role="alert">
            {errorMessage}
          </p>
        ) : null}
        <Button type="submit" disabled={pending}>
          {pending ? 'Creating account…' : 'Sign up'}
        </Button>
      </form>
      <p className="mt-6 text-body text-body-sm">
        Already have an account?{' '}
        <Link to="/login" className="text-link hover:text-link-deep">
          Log in
        </Link>
      </p>
    </AuthLayout>
  )
}
