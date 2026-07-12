/**
 * BoardPage component tests: three mutually exclusive states (loading, success, error).
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { vi, type Mock } from 'vitest'
import { BoardPage } from './BoardPage'
import type { BoardView } from '../api/board'

vi.mock('../api/board', () => ({
  getMockBoard: vi.fn(),
}))

// Import after mock so we get the mocked version
import { getMockBoard } from '../api/board'
const getMockBoardMock = getMockBoard as Mock

// --- helpers ---

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  })
}

function Wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={createQueryClient()}>{children}</QueryClientProvider>
}

const FIVE_COLUMNS: BoardView = {
  columns: [
    { state: 'new', label: 'New', cards: [] },
    { state: 'ready_for_implementation', label: 'Ready', cards: [] },
    { state: 'in_progress', label: 'In Progress', cards: [] },
    { state: 'ready_for_acceptance', label: 'Acceptance', cards: [] },
    { state: 'done', label: 'Done', cards: [{ id: '1', title: 'Card', type: 'bug' }] },
  ],
}

// --- tests ---

describe('BoardPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('render_shouldShowLoadingIndicator_whenFetchInFlight', () => {
    // Never resolve — keeps query in pending state
    getMockBoardMock.mockReturnValue(new Promise(() => {}))

    render(<BoardPage />, { wrapper: Wrapper })

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByTestId('board')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('render_shouldShowFiveColumns_whenFetchSucceeds', async () => {
    getMockBoardMock.mockResolvedValue(FIVE_COLUMNS)

    render(<BoardPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByTestId('board')).toBeInTheDocument()
    })

    const columns = screen.getAllByTestId('board-column')
    expect(columns).toHaveLength(5)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('render_shouldShowErrorIndicator_whenFetchFails', async () => {
    getMockBoardMock.mockRejectedValue(new Error('network failure'))

    render(<BoardPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('board')).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('render_shouldRecover_whenRetryClickedAfterFailure', async () => {
    getMockBoardMock
      .mockRejectedValueOnce(new Error('network failure'))
      .mockResolvedValue(FIVE_COLUMNS)

    render(<BoardPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => {
      expect(screen.getByTestId('board')).toBeInTheDocument()
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
