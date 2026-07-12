import { Navigate, Outlet, useLocation } from 'react-router'
import { LoadingState } from '@/components/state/LoadingState'
import { useAuth } from './auth-context'

/**
 * Route guard for business routes. While the session is still hydrating it renders a loading state
 * (so a valid token is not treated as unauthenticated mid-boot); once resolved, an unauthenticated
 * visitor is redirected to `/login` with the originally requested location preserved in navigation
 * state, so the login form (E12) can send them back.
 */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="px-6 pt-8">
        <LoadingState />
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
