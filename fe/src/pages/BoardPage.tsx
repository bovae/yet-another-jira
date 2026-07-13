import { useEffect, useState } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  pointerWithin,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { getBoard, type BoardCard, type BoardView } from '@/api/board'
import { listEpics } from '@/api/epics'
import { listTeams } from '@/api/teams'
import { TICKET_TYPES, patchTicketState } from '@/api/tickets'
import { Column } from '@/components/Column'
import { TicketCardContent } from '@/components/TicketCard'
import { ErrorState } from '@/components/state/ErrorState'
import { LoadingState } from '@/components/state/LoadingState'
import { TicketFormDialog } from '@/components/tickets/TicketFormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/** Select sentinel for "no filter"; maps to omitting the corresponding query param. */
const ALL = 'all'
const SEARCH_DEBOUNCE_MS = 300

/**
 * Board page (E15): the primary Kanban screen. A team selector, three server-side filters (type, epic,
 * title search), and drag-and-drop state changes, all keyed off the URL search params so a reload
 * restores the same view (D2). Columns render via the real board endpoint (D1); a drop persists the new
 * state with an optimistic move and revert-on-failure (D4).
 */
export function BoardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState(() => searchParams.get('q') ?? '')
  const [createOpen, setCreateOpen] = useState(false)
  const [moveError, setMoveError] = useState('')
  // The card being dragged, rendered in the DragOverlay so it stays visible outside its column (D1).
  const [activeCard, setActiveCard] = useState<BoardCard | null>(null)
  const queryClient = useQueryClient()

  const teamIdParam = searchParams.get('teamId')
  const typeFilter = searchParams.get('type') ?? undefined
  const epicFilter = searchParams.get('epicId') ?? undefined
  const qFilter = searchParams.get('q') ?? undefined

  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: ({ signal }) => listTeams({ signal }),
  })
  const teams = teamsQuery.data ?? []
  const teamId = teamIdParam ?? teams[0]?.id

  const boardKey = ['board', teamId, typeFilter ?? null, epicFilter ?? null, qFilter ?? null]
  const boardQuery = useQuery({
    queryKey: boardKey,
    queryFn: ({ signal }) =>
      getBoard(teamId, { type: typeFilter, epicId: epicFilter, q: qFilter }, { signal }),
    enabled: teams.length > 0,
    // Keep the prior board rendered while a filter/search refetch is in flight, so LoadingState
    // only appears on the first-ever load — not on every keystroke (D6).
    placeholderData: keepPreviousData,
  })

  const epicsQuery = useQuery({
    queryKey: ['epics', teamId],
    queryFn: ({ signal }) => listEpics(teamId, { signal }),
    enabled: teams.length > 0,
  })
  const epics = epicsQuery.data ?? []

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor),
  )

  // Follow external `q` changes (back/forward nav, a cleared filter) into the input. During typing the
  // URL hasn't changed yet, so this is a no-op until the debounce writes q — then urlQ === search and
  // it stays a no-op, avoiding a feedback loop. This is a deliberate external-system (URL) → controlled
  // input sync, which is exactly what the setState-in-effect rule can't statically recognize.
  useEffect(() => {
    const urlQ = searchParams.get('q') ?? ''
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSearch((current) => (current === urlQ ? current : urlQ))
  }, [searchParams])

  // Debounce the search box into the `q` param so each keystroke doesn't fire a request (D3).
  useEffect(() => {
    const handle = setTimeout(() => {
      updateParams((next) => {
        if (search.trim() === '') {
          next.delete('q')
        } else {
          next.set('q', search)
        }
      })
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(handle)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search])

  const moveMutation = useMutation({
    mutationFn: ({ id, toState }: { id: string; fromState: string; toState: string }) =>
      patchTicketState(id, toState),
    onMutate: async ({ id, fromState, toState }) => {
      setMoveError('')
      await queryClient.cancelQueries({ queryKey: boardKey })
      const previous = queryClient.getQueryData<BoardView>(boardKey)
      if (previous) {
        queryClient.setQueryData<BoardView>(boardKey, moveCard(previous, id, fromState, toState))
      }
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(boardKey, context.previous)
      }
      setMoveError('Could not move the ticket. Please try again.')
    },
    onSettled: (_data, _error, variables) => {
      // Invalidate the whole board for this team (prefix match covers every type/epic/search combo,
      // not just the currently-viewed one), plus the ticket lists and the moved ticket's detail — a
      // state change alters all of them.
      void queryClient.invalidateQueries({ queryKey: ['board', teamId] })
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
      void queryClient.invalidateQueries({ queryKey: ['ticket', variables.id] })
    },
  })

  function updateParams(mutate: (params: URLSearchParams) => void) {
    // Any team/filter/search change moves to a different board view, so a stale move error no longer
    // applies — clear it here (the single param-mutation point) rather than in a setState effect.
    setMoveError('')
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        mutate(next)
        return next
      },
      { replace: true },
    )
  }

  function handleTeamChange(value: string) {
    updateParams((next) => {
      next.set('teamId', value)
      // Epics are team-scoped; a stale cross-team epic filter would silently empty the board (D2).
      next.delete('epicId')
    })
  }

  function handleFilterChange(key: 'type' | 'epicId', value: string) {
    updateParams((next) => (value === ALL ? next.delete(key) : next.set(key, value)))
  }

  function handleDragStart(event: DragStartEvent) {
    setActiveCard((event.active.data.current?.card as BoardCard | undefined) ?? null)
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveCard(null)
    const { active, over } = event
    if (!over) {
      return
    }
    const fromState = active.data.current?.fromState as string | undefined
    const toState = String(over.id)
    // Same-column drop (or no source) is a no-op — reordering within a column isn't persisted (D4).
    if (!fromState || fromState === toState) {
      return
    }
    moveMutation.mutate({ id: String(active.id), fromState, toState })
  }

  if (teamsQuery.isPending) {
    return <LoadingState label="Loading board…" />
  }

  if (teamsQuery.isError) {
    return (
      <ErrorState
        message="Could not load teams."
        onRetry={() => void teamsQuery.refetch()}
        retrying={teamsQuery.isFetching}
      />
    )
  }

  if (teams.length === 0) {
    return (
      <div className="flex flex-col items-start gap-4 rounded-md border border-hairline bg-canvas p-6">
        <p className="text-body text-body-sm">Create a team to start using the board.</p>
        <Button asChild>
          <Link to="/teams">Go to Teams</Link>
        </Button>
      </div>
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-display-sm">Board</h1>
        <Select value={teamId} onValueChange={handleTeamChange}>
          <SelectTrigger aria-label="Board team" className="w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {teams.map((team) => (
              <SelectItem key={team.id} value={team.id}>
                {team.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={typeFilter ?? ALL}
          onValueChange={(value) => handleFilterChange('type', value)}
        >
          <SelectTrigger aria-label="Filter by type" className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All types</SelectItem>
            {TICKET_TYPES.map((option) => (
              <SelectItem key={option.code} value={option.code}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={epicFilter ?? ALL}
          onValueChange={(value) => handleFilterChange('epicId', value)}
        >
          <SelectTrigger aria-label="Filter by epic" className="w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All epics</SelectItem>
            {epics.map((epic) => (
              <SelectItem key={epic.id} value={epic.id}>
                {epic.title}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Input
          aria-label="Search tickets"
          placeholder="Search titles…"
          className="w-56"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />

        <Button className="ml-auto" onClick={() => setCreateOpen(true)}>
          New ticket
        </Button>
      </header>

      {moveError ? <ErrorState message={moveError} /> : null}

      {boardQuery.isPending ? (
        <LoadingState label="Loading board…" />
      ) : boardQuery.isError ? (
        <ErrorState
          message="Could not load the board."
          onRetry={() => void boardQuery.refetch()}
          retrying={boardQuery.isFetching}
        />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={pointerWithin}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          onDragCancel={() => setActiveCard(null)}
        >
          <div
            className="grid grid-flow-col auto-cols-[minmax(240px,1fr)] gap-4 overflow-x-auto pb-2"
            data-testid="board"
          >
            {boardQuery.data.columns.map((column) => (
              <Column key={column.state} column={column} />
            ))}
          </div>
          <DragOverlay dropAnimation={null}>
            {activeCard ? <TicketCardContent card={activeCard} /> : null}
          </DragOverlay>
        </DndContext>
      )}

      {createOpen ? (
        <TicketFormDialog teams={teams} defaultTeamId={teamId} onOpenChange={setCreateOpen} />
      ) : null}
    </section>
  )
}

/**
 * Optimistically move a card to the top of the target column. Pure so the drag mutation can snapshot
 * the prior board and roll back on failure. A state change bumps `modified_at`, so the server will
 * order the moved card first — the optimistic position matches the eventual server order (D4).
 */
function moveCard(board: BoardView, id: string, fromState: string, toState: string): BoardView {
  let moved: BoardCard | undefined
  const columns = board.columns.map((column) => {
    if (column.state !== fromState) {
      return column
    }
    moved = column.cards.find((card) => card.id === id)
    return { ...column, cards: column.cards.filter((card) => card.id !== id) }
  })
  if (!moved) {
    return board
  }
  return {
    columns: columns.map((column) =>
      column.state === toState
        ? { ...column, cards: [moved as BoardCard, ...column.cards] }
        : column,
    ),
  }
}
