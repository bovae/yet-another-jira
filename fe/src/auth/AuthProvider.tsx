import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, login as loginRequest, logout as logoutRequest } from '@/api/auth'
import type { Credentials } from '@/api/auth'
import { AUTH_UNAUTHORIZED_EVENT, TOKEN_KEY } from '@/api/client'
import { AuthContext, type AuthStatus, type AuthUser } from './auth-context'

/**
 * Holds session state (token in localStorage, user in React state) and hydrates it on boot.
 *
 * Redirects are intentionally NOT issued here — the provider only mutates state, and the route
 * guard derives navigation from `status` (design D3/D4). That keeps the api and auth layers free of
 * router coupling and avoids racing an imperative navigation against a re-render.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  // Start in `loading` only when a token exists to validate; otherwise we already know the answer,
  // which also keeps the hydration effect free of a synchronous setState.
  const [status, setStatus] = useState<AuthStatus>(() =>
    localStorage.getItem(TOKEN_KEY) ? 'loading' : 'unauthenticated',
  )
  const [user, setUser] = useState<AuthUser | null>(null)

  // Boot hydration: validate a stored token against /auth/me. Rejected token → clear and
  // unauthenticated (apiFetch also clears on the 401).
  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) {
      return
    }
    fetchMe()
      .then((me) => {
        setUser({ id: me.id, email: me.email })
        setStatus('authenticated')
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_KEY)
        setUser(null)
        setStatus('unauthenticated')
      })
  }, [])

  // A token-authenticated 401 anywhere in the app expires the session (apiFetch already removed the
  // token); drop the user so the guard redirects to /login on the next render.
  useEffect(() => {
    function handleUnauthorized() {
      setUser(null)
      setStatus('unauthenticated')
    }
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  const login = useCallback(async (credentials: Credentials) => {
    const { accessToken } = await loginRequest(credentials)
    localStorage.setItem(TOKEN_KEY, accessToken)
    const me = await fetchMe()
    setUser({ id: me.id, email: me.email })
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } catch {
      // The server-side denylist entry expires with the token on its own; clearing locally is what
      // ends the session for this client, so a failed request must not block it.
    } finally {
      localStorage.removeItem(TOKEN_KEY)
      setUser(null)
      setStatus('unauthenticated')
    }
  }, [])

  const value = useMemo(() => ({ status, user, login, logout }), [status, user, login, logout])

  return <AuthContext value={value}>{children}</AuthContext>
}
