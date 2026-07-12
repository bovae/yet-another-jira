import { Route, Routes } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { AppShell } from '@/components/layout/AppShell'
import { BoardPage } from '@/pages/BoardPage'
import { EpicsPage } from '@/pages/EpicsPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFound } from '@/pages/Placeholder'
import { SignupPage } from '@/pages/SignupPage'
import { TeamsPage } from '@/pages/TeamsPage'
import { TicketDetailsPage } from '@/pages/TicketDetailsPage'
import { TicketsPage } from '@/pages/TicketsPage'
import { VerifyPage } from '@/pages/VerifyPage'

/**
 * Route table for every §10 minimum screen. The public auth routes (E12) render the real screens;
 * `/verify-error` is the backend's browser-flow error-redirect target and reuses the verify page's
 * error variant. `/teams` and `/epics` render the real management screens (E13); `/tickets` and
 * `/tickets/:id` render the real ticket list and details screens (E14). The mock board is still the
 * board placeholder. Business routes sit behind {@link RequireAuth} inside the {@link AppShell} layout;
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
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/epics" element={<EpicsPage />} />
          <Route path="/tickets" element={<TicketsPage />} />
          <Route path="/tickets/:id" element={<TicketDetailsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
