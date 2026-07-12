import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { ApiError, verify } from '@/api/auth'
import { ResendVerification } from '@/components/auth/ResendVerification'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { LoadingState } from '@/components/state/LoadingState'

/** Shown when there is no backend message to render (missing token, or the `/verify-error` redirect). */
const INVALID_LINK_MESSAGE = 'This verification link is invalid or has expired.'

/**
 * Verification result screen.
 *
 * The POST /verify call runs as a TanStack Query keyed by the token (D3): the token is single-use and
 * StrictMode double-mounts effects in dev, so a bare `useEffect` would fire the request twice and the
 * second call would get a `410` for a token that just succeeded. Query-key deduplication fires it
 * exactly once per token with no manual ref guard.
 *
 * `/verify-error` renders this same page with `variant="error"` (D4) — the landing page for the
 * backend's browser-flow error redirect. It short-circuits to the error state with no token read and
 * no API call, as does a `/verify` visit with no `token` parameter.
 */
export function VerifyPage({ variant }: { variant?: 'error' } = {}) {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const isErrorVariant = variant === 'error'

  const query = useQuery({
    queryKey: ['verify', token],
    queryFn: () => verify(token as string),
    enabled: !isErrorVariant && !!token,
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
  })

  // Error variant and missing token both resolve to the error state without ever calling the API.
  if (isErrorVariant || !token) {
    return <ErrorResult message={INVALID_LINK_MESSAGE} />
  }

  if (query.isPending) {
    return (
      <AuthLayout title="Verifying your email">
        <LoadingState label="Verifying your email…" />
      </AuthLayout>
    )
  }

  if (query.isError) {
    const message = query.error instanceof ApiError ? query.error.message : INVALID_LINK_MESSAGE
    return <ErrorResult message={message} />
  }

  return (
    <AuthLayout title="Email verified">
      <p className="text-body text-body-sm" role="status">
        {query.data.message}
      </p>
      <Link to="/login" className="mt-6 inline-block text-link text-body-sm hover:text-link-deep">
        Continue to log in
      </Link>
    </AuthLayout>
  )
}

function ErrorResult({ message }: { message: string }) {
  return (
    <AuthLayout title="Verification failed">
      <p className="mb-6 text-error text-body-sm" role="alert">
        {message}
      </p>
      <p className="mb-4 text-body text-body-sm">Request a new verification link:</p>
      <ResendVerification />
    </AuthLayout>
  )
}
