import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { TeamsPage } from './TeamsPage'
import { ApiError } from '@/api/problem'

vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn(),
  createTeam: vi.fn(),
  renameTeam: vi.fn(),
  deleteTeam: vi.fn(),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
}))

import { createTeam, deleteTeam, listTeams, renameTeam } from '@/api/teams'
import { listEpics } from '@/api/epics'

const listTeamsMock = listTeams as Mock
const createTeamMock = createTeam as Mock
const renameTeamMock = renameTeam as Mock
const deleteTeamMock = deleteTeam as Mock
const listEpicsMock = listEpics as Mock

function team(id: string, name: string) {
  return { id, name, createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' }
}

function epic(teamId: string) {
  return {
    id: `e-${teamId}`,
    teamId,
    title: 'Epic',
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <TeamsPage />
    </QueryClientProvider>,
  )
}

describe('TeamsPage', () => {
  beforeEach(() => {
    listEpicsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('delete_shouldBeDisabledOnlyForTeamWithEpics', async () => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha'), team('b', 'Beta')])
    listEpicsMock.mockResolvedValue([epic('a')])
    renderPage()

    expect(await screen.findByText('Alpha')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /delete alpha/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /^delete beta$/i })).toBeEnabled()
  })

  it('create_shouldCloseDialogAndShowNewTeam_whenSuccess', async () => {
    listTeamsMock
      .mockResolvedValueOnce([team('a', 'Alpha')])
      .mockResolvedValue([team('a', 'Alpha'), team('g', 'Gamma')])
    createTeamMock.mockResolvedValue(team('g', 'Gamma'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /new team/i }))
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Gamma')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(createTeamMock).toHaveBeenCalledWith('Gamma')
    expect(await screen.findByText('Gamma')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('rename_shouldShowRenamedTeam_whenSuccess', async () => {
    listTeamsMock.mockResolvedValueOnce([team('a', 'Alpha')]).mockResolvedValue([team('a', 'Core')])
    renameTeamMock.mockResolvedValue(team('a', 'Core'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /rename/i }))
    const input = screen.getByRole('textbox', { name: 'Name' })
    await user.clear(input)
    await user.type(input, 'Core')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(renameTeamMock).toHaveBeenCalledWith('a', 'Core')
    expect(await screen.findByText('Core')).toBeInTheDocument()
  })

  it('create_shouldRenderDetailAndKeepDialogOpen_whenConflict', async () => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha')])
    createTeamMock.mockRejectedValue(new ApiError(409, 'A team with this name already exists.'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /new team/i }))
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Alpha')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A team with this name already exists.',
    )
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Alpha')
  })

  it('delete_shouldRemoveRow_whenConfirmedAndNoContent', async () => {
    listTeamsMock.mockResolvedValueOnce([team('a', 'Alpha')]).mockResolvedValue([])
    deleteTeamMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /^delete alpha$/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(deleteTeamMock).toHaveBeenCalledWith('a')
    await waitFor(() => expect(screen.queryByText('Alpha')).not.toBeInTheDocument())
    expect(screen.getByText(/no teams yet/i)).toBeInTheDocument()
  })

  it('delete_shouldRenderDetailAndKeepTeam_whenConflict', async () => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha')])
    deleteTeamMock.mockRejectedValue(new ApiError(409, 'Team still has epics.'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /^delete alpha$/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Team still has epics.')
    expect(screen.getByText('Alpha')).toBeInTheDocument()
  })

  it('delete_shouldIssueNoRequest_whenCancelled', async () => {
    listTeamsMock.mockResolvedValue([team('a', 'Alpha')])
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Alpha')
    await user.click(screen.getByRole('button', { name: /^delete alpha$/i }))
    await user.click(screen.getByRole('button', { name: /cancel/i }))

    expect(deleteTeamMock).not.toHaveBeenCalled()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
  })
})
