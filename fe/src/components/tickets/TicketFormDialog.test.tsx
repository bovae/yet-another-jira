import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { TicketFormDialog } from './TicketFormDialog'
import { ApiError } from '@/api/problem'

vi.mock('@/api/tickets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/tickets')>()),
  createTicket: vi.fn(),
  updateTicket: vi.fn(),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
}))

import { createTicket, updateTicket } from '@/api/tickets'
import { listEpics } from '@/api/epics'

const createTicketMock = createTicket as Mock
const updateTicketMock = updateTicket as Mock
const listEpicsMock = listEpics as Mock

const TEAMS = [
  { id: 'a', name: 'Alpha', createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' },
  { id: 'b', name: 'Beta', createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' },
]

function epic(id: string, teamId: string, title: string) {
  return {
    id,
    teamId,
    title,
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
  }
}

function renderDialog(props: Partial<Parameters<typeof TicketFormDialog>[0]> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <TicketFormDialog teams={TEAMS} onOpenChange={() => {}} {...props} />
    </QueryClientProvider>,
  )
}

describe('TicketFormDialog', () => {
  beforeEach(() => {
    listEpicsMock.mockImplementation((teamId?: string) =>
      Promise.resolve(teamId === 'a' ? [epic('e1', 'a', 'Login')] : [epic('e2', 'b', 'Signup')]),
    )
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('create_shouldSendCanonicalCodesAndOmitEpic_whenNoneSelected', async () => {
    createTicketMock.mockResolvedValue(epic('k1', 'a', 'x'))
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Login broken')
    await user.type(screen.getByRole('textbox', { name: 'Body' }), 'Repro')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() => expect(createTicketMock).toHaveBeenCalledTimes(1))
    const input = createTicketMock.mock.calls[0][0] as Record<string, unknown>
    expect(input).toMatchObject({
      teamId: 'a',
      type: 'bug',
      state: 'new',
      title: 'Login broken',
      body: 'Repro',
    })
    expect(input.epicId).toBeUndefined()
  })

  it('create_shouldSendEpicId_whenEpicPicked', async () => {
    createTicketMock.mockResolvedValue(epic('k1', 'a', 'x'))
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.click(screen.getByRole('combobox', { name: 'Epic' }))
    await user.click(await screen.findByRole('option', { name: 'Login' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'T')
    await user.type(screen.getByRole('textbox', { name: 'Body' }), 'B')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() => expect(createTicketMock).toHaveBeenCalledTimes(1))
    expect((createTicketMock.mock.calls[0][0] as Record<string, unknown>).epicId).toBe('e1')
  })

  it('teamChange_shouldClearEpicAndReloadOptions', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.click(screen.getByRole('combobox', { name: 'Epic' }))
    await user.click(await screen.findByRole('option', { name: 'Login' }))
    expect(screen.getByRole('combobox', { name: 'Epic' })).toHaveTextContent('Login')

    // Switch team → epic resets to "None" and options reload for the new team.
    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Beta' }))
    expect(screen.getByRole('combobox', { name: 'Epic' })).toHaveTextContent('None')

    await user.click(screen.getByRole('combobox', { name: 'Epic' }))
    expect(await screen.findByRole('option', { name: 'Signup' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Login' })).not.toBeInTheDocument()
  })

  it('submit_shouldSurfaceValidationErrorAndPreserveValues_whenRejected', async () => {
    createTicketMock.mockRejectedValue(new ApiError(400, 'Title is required.'))
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('combobox', { name: 'Team' }))
    await user.click(screen.getByRole('option', { name: 'Alpha' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'My title')
    await user.type(screen.getByRole('textbox', { name: 'Body' }), 'My body')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Title is required.')
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('My title')
    expect(screen.getByRole('textbox', { name: 'Body' })).toHaveValue('My body')
  })

  it('create_shouldPromptToCreateTeam_whenNoTeams', () => {
    renderDialog({ teams: [] })

    expect(screen.getByText(/create a team first/i)).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Title' })).not.toBeInTheDocument()
  })

  it('edit_shouldPrefillFromTicketAndCallUpdate', async () => {
    updateTicketMock.mockResolvedValue(epic('k1', 'a', 'x'))
    const user = userEvent.setup()
    renderDialog({
      ticket: {
        id: 'k1',
        teamId: 'a',
        epicId: 'e1',
        type: 'feature',
        state: 'in_progress',
        title: 'Old title',
        body: 'Old body',
        createdBy: 'u1',
        createdAt: '2026-07-12T00:00:00Z',
        modifiedAt: '2026-07-12T00:00:00Z',
      },
    })

    const title = screen.getByRole('textbox', { name: 'Title' })
    expect(title).toHaveValue('Old title')
    await user.clear(title)
    await user.type(title, 'New title')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(updateTicketMock).toHaveBeenCalledTimes(1))
    const [id, input] = updateTicketMock.mock.calls[0] as [string, Record<string, unknown>]
    expect(id).toBe('k1')
    expect(input).toMatchObject({
      teamId: 'a',
      type: 'feature',
      state: 'in_progress',
      title: 'New title',
    })
  })
})
