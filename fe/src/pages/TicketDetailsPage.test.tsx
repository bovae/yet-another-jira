import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { TicketDetailsPage } from './TicketDetailsPage'
import { ApiError } from '@/api/problem'
import { epic, makeQueryClient, team, ticket } from '@/test/helpers'

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

function renderPage() {
  const client = makeQueryClient()
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tickets/k1']}>
        <Routes>
          <Route path="/tickets" element={<div>tickets list</div>} />
          <Route path="/tickets/:id" element={<TicketDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return client
}

describe('TicketDetailsPage', () => {
  beforeEach(() => {
    getTicketMock.mockResolvedValue(ticket({ state: 'ready_for_implementation' }))
    listTeamsMock.mockResolvedValue([team({ id: 't1', name: 'Alpha' })])
    listEpicsMock.mockResolvedValue([epic({ id: 'e1', teamId: 't1', title: 'Onboarding' })])
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

  it('details_shouldShowCreatedByEmail_whenResolved', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Login broken' })
    expect(screen.getByText('creator@example.com')).toBeInTheDocument()
  })

  it('details_shouldFallBackToRawCreatedById_whenEmailUnresolved', async () => {
    getTicketMock.mockResolvedValue(ticket({ createdBy: 'someone-else', createdByEmail: null }))
    renderPage()

    await screen.findByRole('heading', { name: 'Login broken' })
    expect(screen.getByText('someone-else')).toBeInTheDocument()
    expect(screen.queryByText('creator@example.com')).not.toBeInTheDocument()
  })

  it('details_shouldShowNeutralEpicPlaceholder_whileEpicsPending', async () => {
    // Epics never resolve, so the epic field can only show the neutral placeholder — never briefly
    // claiming a real epic is "Unknown".
    listEpicsMock.mockImplementation(() => new Promise(() => {}))
    renderPage()

    await screen.findByRole('heading', { name: 'Login broken' })
    expect(screen.getByText('…')).toBeInTheDocument()
    expect(screen.queryByText('Unknown epic')).not.toBeInTheDocument()
    expect(screen.queryByText('Onboarding')).not.toBeInTheDocument()
  })

  it('details_shouldRenderNotFound_when404', async () => {
    getTicketMock.mockRejectedValue(new ApiError(404, 'Ticket not found.'))
    renderPage()

    expect(await screen.findByText(/ticket not found/i)).toBeInTheDocument()
  })

  it('edit_shouldUpdateView_whenSaved', async () => {
    getTicketMock
      .mockResolvedValueOnce(ticket({ state: 'ready_for_implementation' }))
      .mockResolvedValue(ticket({ state: 'ready_for_implementation', title: 'Login page 500s' }))
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

  it('delete_shouldInvalidateListsAndBoardAndDropTicketCache_whenConfirmed', async () => {
    deleteTicketMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    const client = renderPage()
    await screen.findByRole('heading', { name: 'Login broken' })
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
    const removeSpy = vi.spyOn(client, 'removeQueries')

    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    const dialog = screen.getByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['tickets'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['board'] })
    })
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: ['ticket', 'k1'] })
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
