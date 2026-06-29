import { useQuery } from '@tanstack/react-query'
import { getMockBoard } from '../api/board'
import { Column } from '../components/Column'

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
    <div
      className="flex items-center gap-3 rounded-md border border-hairline bg-canvas p-6 text-body text-body-sm"
      role="status"
      data-testid="board-loading"
    >
      <span
        className="size-4 animate-spin rounded-full border-2 border-hairline border-t-link motion-reduce:animate-none"
        aria-hidden="true"
      />
      Loading board…
    </div>
  ) : isError || !data ? (
    <div
      className="flex items-center gap-3 rounded-md border border-error-soft bg-error-soft p-6 text-error text-body-sm"
      role="alert"
      data-testid="board-error"
    >
      <span>Could not load the board.</span>
      <button
        type="button"
        className="ml-auto cursor-pointer rounded-sm border border-error bg-canvas px-3 py-1 text-error text-button-md hover:bg-canvas-soft disabled:cursor-default disabled:opacity-60"
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
    <div
      className="grid grid-flow-col auto-cols-[minmax(240px,1fr)] gap-4 overflow-x-auto pb-2"
      data-testid="board"
    >
      {data.columns.map((column) => (
        <Column key={column.state} column={column} />
      ))}
    </div>
  )

  return (
    <main className="min-h-screen px-6 pt-8 pb-12">
      <header className="mb-6">
        <p className="mb-1 font-mono text-caption-mono text-mute">MOCK BOARD</p>
        <h1 className="text-display-lg">yet-another-jira</h1>
      </header>
      {content}
    </main>
  )
}
