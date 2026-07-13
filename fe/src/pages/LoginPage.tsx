import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR_MESSAGE } from '@/api/auth'
import { useAuth } from '@/auth/auth-context'
import { ResendVerification } from '@/components/auth/ResendVerification'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Status = 'idle' | 'pending' | 'error'

/** Shape `RequireAuth` writes into navigation state when it redirects an unauthenticated visitor. */
interface FromState {
  from?: { pathname: string }
}

/**
 * Login screen (D6): authenticates through the auth-context `login` action and, on success, redirects
 * to the location the route guard preserved (`location.state.from`) or `/`. Renders the backend
 * problem `detail` on failure; a `403` (unverified account) additionally reveals the resend action.
 */
export function LoginPage() {
  const { status: authStatus, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as FromState | null)?.from?.pathname ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [unverified, setUnverified] = useState(false)

  // Already signed in → the login screen has nothing to do; send them to the app. Placed after all
  // hooks so the early return never changes the hook call order.
  if (authStatus === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatus('pending')
    setUnverified(false)
    try {
      await login({ email, password })
      void navigate(from, { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message)
        setUnverified(error.status === 403)
      } else {
        setErrorMessage(GENERIC_ERROR_MESSAGE)
      }
      setStatus('error')
    }
  }

  const pending = status === 'pending'

  return (
    <AuthLayout title="Log in">
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
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        {status === 'error' ? (
          <p className="text-error text-body-sm" role="alert">
            {errorMessage}
          </p>
        ) : null}
        <Button type="submit" disabled={pending}>
          {pending ? 'Logging in…' : 'Log in'}
        </Button>
      </form>
      {unverified ? (
        <div className="mt-6 border-t border-hairline pt-6">
          <p className="mb-4 text-body text-body-sm">
            Verify your email to continue — request a new link:
          </p>
          <ResendVerification defaultEmail={email} />
        </div>
      ) : null}
      <p className="mt-6 text-body text-body-sm">
        New here?{' '}
        <Link to="/signup" className="text-link hover:text-link-deep">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
