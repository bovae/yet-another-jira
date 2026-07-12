import { createContext, useContext } from 'react'
import type { Credentials } from '@/api/auth'

/** The authenticated user exposed to the app (id + email; other fields stay server-side). */
export interface AuthUser {
  id: string
  email: string
}

/**
 * - `loading`: boot hydration is validating a stored token, outcome unknown.
 * - `authenticated`: a valid session with a known user.
 * - `unauthenticated`: no token, or the token was rejected/cleared.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: AuthUser | null
  /** Authenticate, store the token, and hydrate the user. Rejects if credentials are invalid. */
  login: (credentials: Credentials) => Promise<void>
  /** End the session; clears local state even if the server call fails. */
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/** Access the auth context. Throws if used outside an {@link AuthProvider}. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
