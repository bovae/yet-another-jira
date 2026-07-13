import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { EpicsPage } from './EpicsPage'
import { ApiError } from '@/api/problem'

vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
  createEpic: vi.fn(),
  updateEpic: vi.fn(),
  deleteEpic: vi.fn(),
}))
vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn(),
}))
vi.mock('@/api/tickets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/tickets')>()),
  listTickets: vi.fn(),
}))

import { createEpic, deleteEpic, listEpics, updateEpic } from '@/api/epics'
import { listTeams } from '@/api/teams'
import { listTickets } from '@/api/tickets'

const listEpicsMock = listEpics as Mock
const createEpicMock = createEpic as Mock
const updateEpicMock = updateEpic as Mock
const deleteEpicMock = deleteEpic as Mock
const listTeamsMock = listTeams as Mock
const listTicketsMock = listTickets as Mock

function team(id: string, name: string) {
  return { id, name, createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' }
}

function epic(id: string, teamId: string, title: string, description?: string) {
  return {
    id,
    teamId,
    title,
    description,
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
  }
}

function ticket(epicId: string) {
  return {
    id: `k-${epicId}`,
    teamId: 'a',
    epicId,
    type: 'bug',
    state: 'new',
    title: 'Ticket',
    body: 'body',
    createdBy: 'u1',
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <EpicsPage />
    </QueryClientProvider>,
  )
}

describe('EpicsPage', () => {
  beforeEach(() => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha'), team('b', 'Beta')])
    listEpicsMock.mockResolvedValue([])
    listTicketsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('filter_shouldNarrowListToSelectedTeam', async () => {
    const login = epic('e1', 'a', 'Login')
    const signup = epic('e2', 'b', 'Signup')
    listEpicsMock.mockImplementation((teamId?: string) =>
      Promise.resolve(teamId === 'a' ? [login] : [login, signup]),
    )
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Login')).toBeInTheDocument()
    expect(screen.getByText('Signup')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: /filter by team/i }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))

    await waitFor(() => expect(screen.queryByText('Signup')).not.toBeInTheDocument())
    expect(screen.getByText('Login')).toBeInTheDocument()
    expect(listEpicsMock).toHaveBeenCalledWith('a', expect.anything())
  })

  it('create_shouldSendChosenTeamId_whenSuccess', async () => {
    createEpicMock.mockResolvedValue(epic('e9', 'a', 'Checkout'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('button', { name: /new epic/i })
    await user.click(screen.getByRole('button', { name: /new epic/i }))
    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Checkout')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(createEpicMock).toHaveBeenCalledWith({ teamId: 'a', title: 'Checkout', description: '' })
  })

  it('edit_shouldSendNoTeamAndKeepTeamReadOnly', async () => {
    listEpicsMock.mockResolvedValue([epic('e1', 'a', 'Login', 'old')])
    updateEpicMock.mockResolvedValue(epic('e1', 'a', 'Sign in', 'old'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /edit login/i }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByTestId('epic-team-readonly')).toHaveTextContent('Alpha')
    expect(within(dialog).queryByRole('combobox')).not.toBeInTheDocument()

    const title = within(dialog).getByRole('textbox', { name: 'Title' })
    await user.clear(title)
    await user.type(title, 'Sign in')
    await user.click(within(dialog).getByRole('button', { name: /^save$/i }))

    expect(updateEpicMock).toHaveBeenCalledWith('e1', { title: 'Sign in', description: 'old' })
    const [, payload] = updateEpicMock.mock.calls[0] as [string, Record<string, unknown>]
    expect(payload).not.toHaveProperty('teamId')
  })

  it('create_shouldPromptToCreateTeam_whenNoTeams', async () => {
    listTeamsMock.mockResolvedValue([])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /new epic/i }))

    expect(screen.getByText(/create a team first/i)).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Title' })).not.toBeInTheDocument()
  })

  it('delete_shouldBeDisabled_whenTicketReferencesEpic', async () => {
    listEpicsMock.mockResolvedValue([epic('e1', 'a', 'Login'), epic('e2', 'a', 'Signup')])
    listTicketsMock.mockResolvedValue([ticket('e1')])
    renderPage()

    expect(await screen.findByText('Login')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /delete login/i })).toBeDisabled(),
    )
    expect(screen.getByRole('button', { name: /^delete signup$/i })).toBeEnabled()
  })

  it('delete_shouldBeEnabled_whenNoTicketReferencesEpic', async () => {
    listEpicsMock.mockResolvedValue([epic('e1', 'a', 'Login')])
    renderPage()

    expect(await screen.findByRole('button', { name: /delete login/i })).toBeEnabled()
  })

  it('delete_shouldRenderDetailAndKeepEpic_whenConflict', async () => {
    listEpicsMock.mockResolvedValue([epic('e1', 'a', 'Login')])
    deleteEpicMock.mockRejectedValue(new ApiError(409, 'Epic is referenced by tickets.'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /delete login/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Epic is referenced by tickets.')
    expect(screen.getByText('Login')).toBeInTheDocument()
  })

  it('delete_shouldRemoveRow_whenConfirmedAndNoContent', async () => {
    listEpicsMock.mockResolvedValueOnce([epic('e1', 'a', 'Login')]).mockResolvedValue([])
    deleteEpicMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /delete login/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(deleteEpicMock).toHaveBeenCalledWith('e1')
    await waitFor(() => expect(screen.queryByText('Login')).not.toBeInTheDocument())
    expect(screen.getByText(/no epics here yet/i)).toBeInTheDocument()
  })

  it('delete_shouldIssueNoRequestAndKeepRow_whenCancelled', async () => {
    listEpicsMock.mockResolvedValue([epic('e1', 'a', 'Login')])
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /delete login/i }))
    await user.click(screen.getByRole('button', { name: /cancel/i }))

    expect(deleteEpicMock).not.toHaveBeenCalled()
    expect(screen.getByText('Login')).toBeInTheDocument()
  })

  it('create_shouldKeepSubmitDisabledAndNotFire_whenTitleWhitespaceOnly', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: /new epic/i }))
    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), '   ')

    const submit = screen.getByRole('button', { name: /^create$/i })
    expect(submit).toBeDisabled()
    await user.click(submit)
    expect(createEpicMock).not.toHaveBeenCalled()
  })
})
