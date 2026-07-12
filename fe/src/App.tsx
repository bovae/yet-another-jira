import { Route, Routes } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { AppShell } from '@/components/layout/AppShell'
import { BoardPage } from '@/pages/BoardPage'
import { NotFound, Placeholder } from '@/pages/Placeholder'

/**
 * Route table for every §10 minimum screen. Public auth routes (E12) and business placeholders
 * (E13–E15) render stand-ins for now; the mock board is the board placeholder. Business routes sit
 * behind {@link RequireAuth} inside the {@link AppShell} layout; anything else is a not-found.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Placeholder title="Log in" />} />
      <Route path="/signup" element={<Placeholder title="Sign up" />} />
      <Route path="/verify" element={<Placeholder title="Verify email" />} />

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
