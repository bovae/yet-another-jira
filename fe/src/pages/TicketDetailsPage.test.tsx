import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { TicketDetailsPage } from './TicketDetailsPage'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'
import { ApiError } from '@/api/problem'

vi.mock('@/api/tickets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/tickets')>()),
  getTicket: vi.fn(),
  updateTicket: vi.fn(),
  deleteTicket: vi.fn(),
}))
vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn(),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
}))
vi.mock('@/api/comments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/comments')>()),
  listComments: vi.fn(),
  addComment: vi.fn(),
}))

import { deleteTicket, getTicket, updateTicket } from '@/api/tickets'
import { listTeams } from '@/api/teams'
import { listEpics } from '@/api/epics'
import { listComments } from '@/api/comments'

const getTicketMock = getTicket as Mock
const updateTicketMock = updateTicket as Mock
const deleteTicketMock = deleteTicket as Mock
const listTeamsMock = listTeams as Mock
const listEpicsMock = listEpics as Mock
const listCommentsMock = listComments as Mock

const USER: AuthContextValue = {
  status: 'authenticated',
  user: { id: 'u1', email: 'me@example.com' },
  login: () => Promise.resolve(),
  logout: () => Promise.resolve(),
}

function ticket(extra: Record<string, unknown> = {}) {
  return {
    id: 'k1',
    teamId: 'a',
    epicId: 'e1',
    type: 'bug',
    state: 'ready_for_implementation',
    title: 'Login broken',
    body: 'Steps to reproduce',
    createdBy: 'u1',
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
    ...extra,
  }
}

function renderPage(auth: AuthContextValue = USER) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <AuthContext value={auth}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/tickets/k1']}>
          <Routes>
            <Route path="/tickets" element={<div>tickets list</div>} />
            <Route path="/tickets/:id" element={<TicketDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </AuthContext>,
  )
}

describe('TicketDetailsPage', () => {
  beforeEach(() => {
    getTicketMock.mockResolvedValue(ticket())
    listTeamsMock.mockResolvedValue([
      {
        id: 'a',
        name: 'Alpha',
        createdAt: '2026-07-12T00:00:00Z',
        modifiedAt: '2026-07-12T00:00:00Z',
      },
    ])
    listEpicsMock.mockResolvedValue([
      {
        id: 'e1',
        teamId: 'a',
        title: 'Onboarding',
        createdAt: '2026-07-12T00:00:00Z',
        modifiedAt: '2026-07-12T00:00:00Z',
      },
    ])
    listCommentsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('details_shouldRenderAllFields', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Login broken' })).toBeInTheDocument()
    expect(screen.getByText('Steps to reproduce')).toBeInTheDocument()
    expect(screen.getByText('Bug')).toBeInTheDocument()
    expect(screen.getByText('Ready for implementation')).toBeInTheDocument()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Onboarding')).toBeInTheDocument()
  })

  it('details_shouldShowEmail_whenCreatedByCurrentUser', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Login broken' })
    expect(screen.getByText('me@example.com')).toBeInTheDocument()
  })

  it('details_shouldShowRawId_whenCreatedByOtherUser', async () => {
    getTicketMock.mockResolvedValue(ticket({ createdBy: 'someone-else' }))
    renderPage()

    await screen.findByRole('heading', { name: 'Login broken' })
    expect(screen.getByText('someone-else')).toBeInTheDocument()
    expect(screen.queryByText('me@example.com')).not.toBeInTheDocument()
  })

  it('details_shouldRenderNotFound_when404', async () => {
    getTicketMock.mockRejectedValue(new ApiError(404, 'Ticket not found.'))
    renderPage()

    expect(await screen.findByText(/ticket not found/i)).toBeInTheDocument()
  })

  it('edit_shouldUpdateView_whenSaved', async () => {
    getTicketMock
      .mockResolvedValueOnce(ticket())
      .mockResolvedValue(ticket({ title: 'Login page 500s' }))
    updateTicketMock.mockResolvedValue(ticket({ title: 'Login page 500s' }))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const dialog = screen.getByRole('dialog')
    const title = within(dialog).getByRole('textbox', { name: 'Title' })
    await user.clear(title)
    await user.type(title, 'Login page 500s')
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }))

    expect(await screen.findByRole('heading', { name: 'Login page 500s' })).toBeInTheDocument()
    expect(updateTicketMock).toHaveBeenCalledTimes(1)
  })

  it('delete_shouldNavigateToList_whenConfirmed', async () => {
    deleteTicketMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /^delete$/i }))
    const dialog = screen.getByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('tickets list')).toBeInTheDocument()
    expect(deleteTicketMock).toHaveBeenCalledWith('k1')
  })

  it('delete_shouldIssueNoRequest_whenCancelled', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /^delete$/i }))
    const dialog = screen.getByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    expect(screen.getByRole('heading', { name: 'Login broken' })).toBeInTheDocument()
    expect(deleteTicketMock).not.toHaveBeenCalled()
  })
})
