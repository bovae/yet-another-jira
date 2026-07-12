import { useQuery } from '@tanstack/react-query'
import { getMockBoard } from '@/api/board'
import { Column } from '@/components/Column'
import { ErrorState } from '@/components/state/ErrorState'
import { LoadingState } from '@/components/state/LoadingState'

/**
 * Board page: fetches the mock board and renders one of three mutually exclusive states —
 * loading, success (the response columns in order), or error (network failure, non-success status,
 * or the 10s client timeout). Renders inside the app shell, so it carries no page chrome of its own.
 */
export function BoardPage() {
  const { data, isPending, isError, refetch, isFetching } = useQuery({
    queryKey: ['mock-board'],
    queryFn: ({ signal }) => getMockBoard({ signal }),
  })

  if (isPending) {
    return <LoadingState label="Loading board…" />
  }

  if (isError || !data) {
    return (
      <ErrorState
        message="Could not load the board."
        onRetry={() => void refetch()}
        retrying={isFetching}
      />
    )
  }

  return (
    <div
      className="grid grid-flow-col auto-cols-[minmax(240px,1fr)] gap-4 overflow-x-auto pb-2"
      data-testid="board"
    >
      {data.columns.map((column) => (
        <Column key={column.state} column={column} />
      ))}
    </div>
  )
}
