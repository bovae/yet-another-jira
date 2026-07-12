import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { TicketsPage } from './TicketsPage'

vi.mock('@/api/tickets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/tickets')>()),
  listTickets: vi.fn(),
  createTicket: vi.fn(),
}))
vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn(),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
}))

import { createTicket, listTickets } from '@/api/tickets'
import { listTeams } from '@/api/teams'
import { listEpics } from '@/api/epics'

const listTicketsMock = listTickets as Mock
const createTicketMock = createTicket as Mock
const listTeamsMock = listTeams as Mock
const listEpicsMock = listEpics as Mock

function team(id: string, name: string) {
  return { id, name, createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' }
}

function ticket(id: string, teamId: string, title: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    teamId,
    type: 'bug',
    state: 'new',
    title,
    body: 'body',
    createdBy: 'u1',
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
    ...extra,
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <TicketsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TicketsPage', () => {
  beforeEach(() => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha'), team('b', 'Beta')])
    listEpicsMock.mockResolvedValue([])
    listTicketsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('list_shouldRenderTitlesTypeStateAndTeam_byDefault', async () => {
    listTicketsMock.mockResolvedValue([
      ticket('k1', 'a', 'Login broken', { state: 'ready_for_implementation' }),
      ticket('k2', 'b', 'Signup fails', { type: 'feature' }),
    ])
    renderPage()

    expect(await screen.findByRole('link', { name: 'Login broken' })).toBeInTheDocument()
    expect(screen.getByText('Signup fails')).toBeInTheDocument()
    expect(screen.getByText('Ready for implementation')).toBeInTheDocument()
    expect(screen.getByText('Feature')).toBeInTheDocument()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
  })

  it('filter_shouldNarrowListToSelectedTeam', async () => {
    listTicketsMock.mockImplementation((teamId?: string) =>
      Promise.resolve(
        teamId === 'a'
          ? [ticket('k1', 'a', 'Login broken')]
          : [ticket('k1', 'a', 'Login broken'), ticket('k2', 'b', 'Signup fails')],
      ),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Signup fails')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: /filter by team/i }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))

    await waitFor(() => expect(screen.queryByText('Signup fails')).not.toBeInTheDocument())
    expect(screen.getByText('Login broken')).toBeInTheDocument()
    expect(listTicketsMock).toHaveBeenCalledWith('a', expect.anything())
  })

  it('list_shouldRenderEmptyState_whenNoTickets', async () => {
    renderPage()

    expect(await screen.findByText(/no tickets here yet/i)).toBeInTheDocument()
  })

  it('list_shouldRenderErrorAndRetry_whenFetchFails', async () => {
    listTicketsMock
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue([ticket('k1', 'a', 'Login broken')])
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not load tickets/i)
    await user.click(screen.getByRole('button', { name: /try again/i }))

    expect(await screen.findByText('Login broken')).toBeInTheDocument()
  })

  it('create_shouldAppendNewTicket_whenSuccess', async () => {
    listTicketsMock.mockResolvedValueOnce([]).mockResolvedValue([ticket('k9', 'a', 'Fresh ticket')])
    createTicketMock.mockResolvedValue(ticket('k9', 'a', 'Fresh ticket'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /new ticket/i }))
    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Fresh ticket')
    await user.type(screen.getByRole('textbox', { name: 'Body' }), 'Details')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(await screen.findByText('Fresh ticket')).toBeInTheDocument()
  })
})
