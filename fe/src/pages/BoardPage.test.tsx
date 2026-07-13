/**
 * BoardPage tests: team selector, five ordered/labelled columns, server-side filters (set/omit params),
 * team-change epic reset, drag-and-drop optimistic move + revert, and the create/open entry points.
 *
 * `@dnd-kit/core` is mocked: jsdom can't reproduce real pointer physics, so `DndContext` just renders
 * its children and exposes the drag lifecycle handlers (`onDragStart`/`onDragEnd`/`onDragCancel`), which
 * the drag tests invoke directly, and `DragOverlay` renders its children inline. The click-vs-drag
 * activation distance is covered by the Playwright smoke instead.
 */
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { BoardPage } from './BoardPage'
import type { BoardView } from '../api/board'

const dnd = vi.hoisted(() => ({
  onDragStart: undefined as ((event: unknown) => void) | undefined,
  onDragEnd: undefined as ((event: unknown) => void) | undefined,
  onDragCancel: undefined as (() => void) | undefined,
}))

vi.mock('@dnd-kit/core', () => ({
  DndContext: ({
    children,
    onDragStart,
    onDragEnd,
    onDragCancel,
  }: {
    children: React.ReactNode
    onDragStart: (event: unknown) => void
    onDragEnd: (event: unknown) => void
    onDragCancel: () => void
  }) => {
    dnd.onDragStart = onDragStart
    dnd.onDragEnd = onDragEnd
    dnd.onDragCancel = onDragCancel
    return children
  },
  DragOverlay: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="drag-overlay">{children}</div>
  ),
  useDraggable: () => ({
    attributes: {},
    listeners: {},
    setNodeRef: () => {},
    transform: null,
    isDragging: false,
  }),
  useDroppable: () => ({ setNodeRef: () => {}, isOver: false }),
  useSensor: () => ({}),
  useSensors: () => [],
  PointerSensor: class {},
  KeyboardSensor: class {},
  pointerWithin: () => [],
}))

vi.mock('@/api/board', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/board')>()),
  getBoard: vi.fn(),
}))
vi.mock('@/api/teams', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/teams')>()),
  listTeams: vi.fn(),
}))
vi.mock('@/api/epics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/epics')>()),
  listEpics: vi.fn(),
}))
vi.mock('@/api/tickets', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/tickets')>()),
  patchTicketState: vi.fn(),
  createTicket: vi.fn(),
}))

import { getBoard } from '@/api/board'
import { listTeams } from '@/api/teams'
import { listEpics } from '@/api/epics'
import { patchTicketState } from '@/api/tickets'

const getBoardMock = getBoard as Mock
const listTeamsMock = listTeams as Mock
const listEpicsMock = listEpics as Mock
const patchTicketStateMock = patchTicketState as Mock

// Mirrors SEARCH_DEBOUNCE_MS in BoardPage (not exported); kept in sync here for the debounce test.
const DEBOUNCE_MS = 300

const STATES = ['new', 'ready_for_implementation', 'in_progress', 'ready_for_acceptance', 'done']
const ORDERED_LABELS = [
  'New',
  'Ready for implementation',
  'In progress',
  'Ready for acceptance',
  'Done',
]

function team(id: string, name: string) {
  return { id, name, createdAt: '2026-07-12T00:00:00Z', modifiedAt: '2026-07-12T00:00:00Z' }
}

function epic(id: string, teamId: string, title: string) {
  return {
    id,
    teamId,
    title,
    createdAt: '2026-07-12T00:00:00Z',
    modifiedAt: '2026-07-12T00:00:00Z',
  }
}

/** A full 5-column board; `cardsByState` places named cards into their column. */
function board(
  cardsByState: Record<string, { id: string; title: string; type?: string }[]> = {},
): BoardView {
  return {
    columns: STATES.map((state) => ({
      state,
      cards: (cardsByState[state] ?? []).map((c) => ({
        id: c.id,
        title: c.title,
        type: c.type ?? 'bug',
      })),
    })),
  }
}

function renderPage(initialEntries: string[] = ['/']) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/" element={<BoardPage />} />
          <Route path="/tickets/:id" element={<div>Ticket details</div>} />
          <Route path="/teams" element={<div>Teams screen</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return queryClient
}

function column(label: string) {
  // The column's accessible name now folds in the count (e.g. "New, 0 tickets"), so match its prefix.
  return within(screen.getByRole('region', { name: new RegExp(`^${label}, `) }))
}

describe('BoardPage', () => {
  beforeEach(() => {
    dnd.onDragStart = undefined
    dnd.onDragEnd = undefined
    dnd.onDragCancel = undefined
    listTeamsMock.mockResolvedValue([team('a', 'Alpha'), team('b', 'Beta')])
    listEpicsMock.mockResolvedValue([])
    getBoardMock.mockResolvedValue(board())
    patchTicketStateMock.mockResolvedValue({})
  })

  afterEach(() => {
    // Restore real timers so a fake-timer test can never leak its clock into the next one.
    vi.useRealTimers()
    vi.resetAllMocks()
  })

  // --- columns ---

  it('render_shouldShowFiveLabelledColumnsInWorkflowOrder_whenFetchSucceeds', async () => {
    renderPage()

    await waitFor(() => expect(screen.getByTestId('board')).toBeInTheDocument())

    const columns = screen.getAllByTestId('board-column')
    expect(columns.map((c) => c.getAttribute('aria-label'))).toEqual(
      ORDERED_LABELS.map((label) => `${label}, 0 tickets`),
    )
  })

  it('render_shouldPromptToCreateTeam_whenNoTeams', async () => {
    listTeamsMock.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText(/create a team to start/i)).toBeInTheDocument()
    expect(screen.queryByTestId('board')).not.toBeInTheDocument()
    expect(getBoardMock).not.toHaveBeenCalled()
  })

  // --- error paths ---

  it('board_shouldRenderErrorThenRecover_whenBoardQueryFailsThenRetried', async () => {
    getBoardMock
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText(/could not load the board/i)).toBeInTheDocument()
    expect(screen.queryByTestId('board')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /try again/i }))

    expect(await screen.findByTestId('board')).toBeInTheDocument()
    expect(column('New').getByText('Login broken')).toBeInTheDocument()
  })

  it('board_shouldDisableRetryButton_whileRefetching', async () => {
    // First load succeeds so the data stays cached; the invalidated refetch then errors while that
    // data is retained, keeping the ErrorState (not LoadingState) mounted so its retry is observable.
    // A final never-resolving fetch holds the retry in flight.
    getBoardMock
      .mockResolvedValueOnce(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
      .mockRejectedValueOnce(new Error('boom'))
      .mockImplementationOnce(() => new Promise<BoardView>(() => {}))
    const user = userEvent.setup()
    const queryClient = renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ['board'] })
    })
    await user.click(await screen.findByRole('button', { name: /try again/i }))

    await waitFor(() => expect(screen.getByRole('button', { name: /retrying/i })).toBeDisabled())
  })

  it('teams_shouldRenderErrorAndSkipBoardFetch_whenTeamsQueryFails', async () => {
    listTeamsMock.mockRejectedValue(new Error('nope'))
    renderPage()

    expect(await screen.findByText(/could not load teams/i)).toBeInTheDocument()
    expect(getBoardMock).not.toHaveBeenCalled()
  })

  // --- filters ---

  it('filter_shouldSetTypeParam_whenTypePicked', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByTestId('board')

    await user.click(screen.getByRole('combobox', { name: /filter by type/i }))
    await user.click(screen.getByRole('option', { name: 'Bug' }))

    await waitFor(() =>
      expect(getBoardMock).toHaveBeenCalledWith(
        'a',
        expect.objectContaining({ type: 'bug' }),
        expect.anything(),
      ),
    )
  })

  it('filter_shouldOmitTypeParam_whenResetToAll', async () => {
    const user = userEvent.setup()
    renderPage(['/?type=bug'])
    await screen.findByTestId('board')

    await user.click(screen.getByRole('combobox', { name: /filter by type/i }))
    await user.click(screen.getByRole('option', { name: 'All types' }))

    await waitFor(() =>
      expect(getBoardMock).toHaveBeenCalledWith(
        'a',
        expect.objectContaining({ type: undefined }),
        expect.anything(),
      ),
    )
  })

  it('search_shouldRequestFinalTermOnly_andNotIntermediatePrefixes', async () => {
    renderPage()
    const input = await screen.findByRole('textbox', { name: /search tickets/i })
    await waitFor(() => expect(getBoardMock).toHaveBeenCalled())
    getBoardMock.mockClear()

    // Fake the clock for the debounce window so intermediate keystrokes can't fire a request. Drive
    // the keystrokes with fireEvent (synchronous) — userEvent's own timing fights a fake clock.
    vi.useFakeTimers()
    fireEvent.change(input, { target: { value: 'a' } })
    fireEvent.change(input, { target: { value: 'ab' } })
    fireEvent.change(input, { target: { value: 'abc' } })
    // Nothing fires while the debounce window is still open.
    expect(getBoardMock).not.toHaveBeenCalled()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    })
    vi.useRealTimers()

    // Exactly one request, for the final term — never for the 'a'/'ab' prefixes.
    await waitFor(() =>
      expect(getBoardMock).toHaveBeenCalledWith(
        'a',
        expect.objectContaining({ q: 'abc' }),
        expect.anything(),
      ),
    )
    const requestedQs = getBoardMock.mock.calls.map((call) => (call[1] as { q?: string }).q)
    expect(requestedQs).toEqual(['abc'])
  })

  it('search_shouldKeepPreviousBoardVisibleWithoutLoadingFlash_whileRefetching', async () => {
    // First load resolves with a card; the filtered refetch is held pending so the in-flight state
    // is observable, then resolved with different data (D6 — placeholderData: keepPreviousData).
    let resolveRefetch: (value: BoardView) => void = () => {}
    getBoardMock
      .mockResolvedValueOnce(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
      .mockImplementationOnce(() => new Promise<BoardView>((resolve) => (resolveRefetch = resolve)))
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    await user.type(screen.getByRole('textbox', { name: /search tickets/i }), 'cache')

    // Refetch in flight: the previous board stays rendered and LoadingState never appears.
    await waitFor(() => expect(getBoardMock).toHaveBeenCalledTimes(2))
    expect(column('New').getByText('Login broken')).toBeInTheDocument()
    expect(screen.queryByText(/loading board/i)).not.toBeInTheDocument()

    // Fresh data swaps in once the refetch resolves.
    act(() => resolveRefetch(board({ new: [{ id: 'k2', title: 'Cache board data' }] })))
    await waitFor(() => expect(column('New').getByText('Cache board data')).toBeInTheDocument())
    expect(column('New').queryByText('Login broken')).not.toBeInTheDocument()
  })

  it('filter_shouldResetEpic_whenTeamChanges', async () => {
    listEpicsMock.mockImplementation((teamId?: string) =>
      Promise.resolve(
        teamId === 'a' ? [epic('e1', 'a', 'Onboarding')] : [epic('e2', 'b', 'Billing')],
      ),
    )
    const user = userEvent.setup()
    renderPage(['/?teamId=a&epicId=e1'])
    await screen.findByTestId('board')

    await user.click(screen.getByRole('combobox', { name: /board team/i }))
    await user.click(screen.getByRole('option', { name: 'Beta' }))

    // Board now requests team b with no epic filter.
    await waitFor(() =>
      expect(getBoardMock).toHaveBeenCalledWith(
        'b',
        expect.objectContaining({ epicId: undefined }),
        expect.anything(),
      ),
    )
    expect(screen.getByRole('combobox', { name: /filter by epic/i })).toHaveTextContent('All epics')
  })

  // --- drag and drop ---

  it('drag_shouldRenderDraggedCardInOverlayOutsideSourceColumn_whenDragStarts', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    act(() => {
      dnd.onDragStart?.({
        active: {
          id: 'k1',
          data: {
            current: { fromState: 'new', card: { id: 'k1', title: 'Login broken', type: 'bug' } },
          },
        },
      })
    })

    // Overlay portal renders the card above all columns; the source card stays in place as a placeholder.
    expect(within(screen.getByTestId('drag-overlay')).getByText('Login broken')).toBeInTheDocument()
    expect(column('New').getByText('Login broken')).toBeInTheDocument()
  })

  it('drag_shouldClearOverlayAndIssueNoRequest_whenDragCancelled', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    act(() => {
      dnd.onDragStart?.({
        active: {
          id: 'k1',
          data: {
            current: { fromState: 'new', card: { id: 'k1', title: 'Login broken', type: 'bug' } },
          },
        },
      })
    })
    expect(within(screen.getByTestId('drag-overlay')).getByText('Login broken')).toBeInTheDocument()

    act(() => dnd.onDragCancel?.())

    expect(
      within(screen.getByTestId('drag-overlay')).queryByText('Login broken'),
    ).not.toBeInTheDocument()
    expect(patchTicketStateMock).not.toHaveBeenCalled()
  })

  it('drag_shouldOptimisticallyMoveCardToTargetColumn_whenDropped', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    patchTicketStateMock.mockReturnValue(new Promise(() => {})) // stays pending → optimistic state holds
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    await act(async () => {
      dnd.onDragEnd?.({
        active: { id: 'k1', data: { current: { fromState: 'new' } } },
        over: { id: 'in_progress' },
      })
      await Promise.resolve()
    })

    await waitFor(() => expect(column('In progress').getByText('Login broken')).toBeInTheDocument())
    expect(column('New').queryByText('Login broken')).not.toBeInTheDocument()
    expect(patchTicketStateMock).toHaveBeenCalledWith('k1', 'in_progress')
  })

  it('drag_shouldInvalidateBoardTicketsAndMovedTicket_whenDropPersists', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    patchTicketStateMock.mockResolvedValue({})
    const queryClient = renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await act(async () => {
      dnd.onDragEnd?.({
        active: { id: 'k1', data: { current: { fromState: 'new' } } },
        over: { id: 'in_progress' },
      })
      await Promise.resolve()
    })

    // onSettled invalidates the team's whole board (prefix), the ticket lists, and the moved ticket.
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['board', 'a'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['tickets'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['ticket', 'k1'] })
    })
  })

  it('drag_shouldRevertAndShowError_whenPatchRejected', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    patchTicketStateMock.mockRejectedValue(new Error('boom'))
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    await act(async () => {
      dnd.onDragEnd?.({
        active: { id: 'k1', data: { current: { fromState: 'new' } } },
        over: { id: 'in_progress' },
      })
      await Promise.resolve()
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not move the ticket/i)
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())
  })

  it('drag_shouldIssueNoRequest_whenDroppedOnSameColumn', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    renderPage()
    await waitFor(() => expect(column('New').getByText('Login broken')).toBeInTheDocument())

    await act(async () => {
      dnd.onDragEnd?.({
        active: { id: 'k1', data: { current: { fromState: 'new' } } },
        over: { id: 'new' },
      })
      await Promise.resolve()
    })

    expect(patchTicketStateMock).not.toHaveBeenCalled()
  })

  // --- entry points ---

  it('create_shouldOpenDialogPresetToSelectedTeam', async () => {
    const user = userEvent.setup()
    renderPage(['/?teamId=b'])
    await screen.findByTestId('board')

    await user.click(screen.getByRole('button', { name: /new ticket/i }))

    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByRole('combobox', { name: 'Team' })).toHaveTextContent('Beta')
  })

  it('card_shouldNavigateToDetails_whenClicked', async () => {
    getBoardMock.mockResolvedValue(board({ new: [{ id: 'k1', title: 'Login broken' }] }))
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => expect(screen.getByText('Login broken')).toBeInTheDocument())

    await user.click(screen.getByText('Login broken'))

    expect(await screen.findByText('Ticket details')).toBeInTheDocument()
  })
})
