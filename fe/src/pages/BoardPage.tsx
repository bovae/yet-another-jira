import { useQuery } from '@tanstack/react-query'
import { getMockBoard } from '../api/board'
import { Column } from '../components/Column'
import './BoardPage.css'

/**
 * Board page: fetches the mock board and renders one of three mutually exclusive states —
 * loading, success (the response columns in order), or error (network failure, non-success status,
 * or the 10s client timeout).
 */
export function BoardPage() {
  const { data, isPending, isError, refetch, isFetching } = useQuery({
    queryKey: ['mock-board'],
    queryFn: getMockBoard,
  })

  const content = isPending ? (
    <div className="board-state" role="status" data-testid="board-loading">
      <span className="board-state__spinner" aria-hidden="true" />
      Loading board…
    </div>
  ) : isError || !data ? (
    <div className="board-state board-state--error" role="alert" data-testid="board-error">
      <span>Could not load the board.</span>
      <button
        type="button"
        className="board-state__retry"
        data-testid="board-retry"
        onClick={() => {
          void refetch()
        }}
        disabled={isFetching}
      >
        {isFetching ? 'Retrying…' : 'Try again'}
      </button>
    </div>
  ) : (
    <div className="board" data-testid="board">
      {data.columns.map((column) => (
        <Column key={column.state} column={column} />
      ))}
    </div>
  )

  return (
    <main className="board-page">
      <header className="board-page__header">
        <p className="board-page__eyebrow">MOCK BOARD</p>
        <h1 className="board-page__title">yet-another-jira</h1>
      </header>
      {content}
    </main>
  )
}
