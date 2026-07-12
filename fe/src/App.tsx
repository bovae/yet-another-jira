import { Route, Routes } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { AppShell } from '@/components/layout/AppShell'
import { BoardPage } from '@/pages/BoardPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFound, Placeholder } from '@/pages/Placeholder'
import { SignupPage } from '@/pages/SignupPage'
import { VerifyPage } from '@/pages/VerifyPage'

/**
 * Route table for every §10 minimum screen. The public auth routes (E12) render the real screens;
 * `/verify-error` is the backend's browser-flow error-redirect target and reuses the verify page's
 * error variant. Business placeholders (E13–E15) still render stand-ins; the mock board is the board
 * placeholder. Business routes sit behind {@link RequireAuth} inside the {@link AppShell} layout;
 * anything else is a not-found.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/verify" element={<VerifyPage />} />
      <Route path="/verify-error" element={<VerifyPage variant="error" />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<BoardPage />} />
          <Route path="/teams" element={<Placeholder title="Teams" />} />
          <Route path="/epics" element={<Placeholder title="Epics" />} />
          <Route path="/tickets/:id" element={<Placeholder title="Ticket" />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
