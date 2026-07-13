import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { GENERIC_ERROR_MESSAGE } from '@/api/problem'
import { listEpics } from '@/api/epics'
import { listTickets } from '@/api/tickets'
import {
  ApiError,
  createTeam,
  deleteTeam,
  listTeams,
  renameTeam,
  type TeamResponse,
} from '@/api/teams'
import { EmptyState } from '@/components/state/EmptyState'
import { ErrorState } from '@/components/state/ErrorState'
import { LoadingState } from '@/components/state/LoadingState'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatTimestamp } from '@/lib/utils'

const REFERENCED_MESSAGE = 'This team has epics or tickets and cannot be deleted.'

/**
 * Team management screen (D2, D3): lists teams from `GET /api/v1/teams`, with create/rename dialogs and
 * a confirm-delete AlertDialog. A second `GET /api/v1/epics` query computes which teams have epics so
 * their delete control is disabled; the backend `409` remains the authoritative guard, surfaced in the
 * confirm dialog if a reference appears concurrently.
 */
export function TeamsPage() {
  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: ({ signal }) => listTeams({ signal }),
  })
  // Reference data for the disabled-delete UX (D3). A failure here is non-fatal: the delete stays
  // enabled and the backend 409 guards it, so we don't block the screen on it.
  const epicsQuery = useQuery({
    queryKey: ['epics', 'all'],
    queryFn: ({ signal }) => listEpics(undefined, { signal }),
  })
  // Tickets also reference a team (D9); same non-fatal treatment as the epics reference query.
  const ticketsQuery = useQuery({
    queryKey: ['tickets', 'all'],
    queryFn: ({ signal }) => listTickets(undefined, { signal }),
  })

  const [createOpen, setCreateOpen] = useState(false)
  const [renameTarget, setRenameTarget] = useState<TeamResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<TeamResponse | null>(null)

  if (teamsQuery.isPending) {
    return <LoadingState label="Loading teams…" />
  }

  if (teamsQuery.isError || !teamsQuery.data) {
    return (
      <ErrorState
        message="Could not load teams."
        onRetry={() => void teamsQuery.refetch()}
        retrying={teamsQuery.isFetching}
      />
    )
  }

  const teams = teamsQuery.data
  const referencedTeamIds = new Set([
    ...(epicsQuery.data ?? []).map((epic) => epic.teamId),
    ...(ticketsQuery.data ?? []).map((ticket) => ticket.teamId),
  ])

  return (
    <section className="flex flex-col gap-6">
      <header className="flex items-center justify-between">
        <h1 className="text-display-sm">Teams</h1>
        <Button onClick={() => setCreateOpen(true)}>New team</Button>
      </header>

      {teams.length === 0 ? (
        <EmptyState message="No teams yet. Create the first team to get started." />
      ) : (
        <ul className="flex flex-col gap-2">
          {teams.map((team) => (
            <TeamRow
              key={team.id}
              team={team}
              referenced={referencedTeamIds.has(team.id)}
              onRename={() => setRenameTarget(team)}
              onDelete={() => setDeleteTarget(team)}
            />
          ))}
        </ul>
      )}

      {createOpen ? <TeamFormDialog onOpenChange={setCreateOpen} /> : null}
      {renameTarget ? (
        <TeamFormDialog team={renameTarget} onOpenChange={() => setRenameTarget(null)} />
      ) : null}
      {deleteTarget ? (
        <DeleteTeamDialog team={deleteTarget} onOpenChange={() => setDeleteTarget(null)} />
      ) : null}
    </section>
  )
}

function TeamRow({
  team,
  referenced,
  onRename,
  onDelete,
}: {
  team: TeamResponse
  referenced: boolean
  onRename: () => void
  onDelete: () => void
}) {
  return (
    <li className="flex items-center gap-4 rounded-md border border-hairline bg-canvas p-4">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="truncate text-body-md-strong text-ink">{team.name}</span>
        <span className="text-caption text-mute">
          Created {formatTimestamp(team.createdAt)} · Updated {formatTimestamp(team.modifiedAt)}
        </span>
      </div>
      <div className="ml-auto flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={onRename}>
          Rename
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={referenced}
          title={referenced ? REFERENCED_MESSAGE : undefined}
          aria-label={
            referenced ? `Delete ${team.name} — ${REFERENCED_MESSAGE}` : `Delete ${team.name}`
          }
          onClick={onDelete}
        >
          Delete
        </Button>
      </div>
    </li>
  )
}

/**
 * Shared create/rename form (D5): one name field. `team` present → rename (`PUT`), absent → create
 * (`POST`). Mounted only while open, so closing unmounts and resets state. Mutation errors (`400`/`409`)
 * render inline so the entered name is preserved.
 */
function TeamFormDialog({
  team,
  onOpenChange,
}: {
  team?: TeamResponse
  onOpenChange: (open: boolean) => void
}) {
  const isRename = team !== undefined
  const [name, setName] = useState(team?.name ?? '')
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: (value: string) => (isRename ? renameTeam(team.id, value) : createTeam(value)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['teams'] })
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Whitespace-only names are not submitted (matches TicketFormDialog); the backend trims the value.
    if (name.trim() === '') {
      return
    }
    setErrorMessage('')
    mutation.mutate(name)
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isRename ? 'Rename team' : 'Create team'}</DialogTitle>
          <DialogDescription>
            {isRename ? 'Give this team a new name.' : 'Name the new team.'}
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="team-name">Name</Label>
            <Input
              id="team-name"
              required
              autoFocus
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          {errorMessage ? (
            <p className="text-error text-body-sm" role="alert">
              {errorMessage}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending || name.trim() === ''}>
              {mutation.isPending ? 'Saving…' : isRename ? 'Save' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Confirm-delete AlertDialog (D3). The action's default close is suppressed so a `409` (concurrent
 * reference) keeps the dialog open with the backend `detail`; a `204` invalidates and closes.
 */
function DeleteTeamDialog({
  team,
  onOpenChange,
}: {
  team: TeamResponse
  onOpenChange: (open: boolean) => void
}) {
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => deleteTeam(team.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['teams'] })
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  return (
    <AlertDialog open onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete team?</AlertDialogTitle>
          <AlertDialogDescription>
            “{team.name}” will be permanently deleted. This cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {errorMessage ? (
          <p className="text-error text-body-sm" role="alert">
            {errorMessage}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={mutation.isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={mutation.isPending}
            onClick={(event) => {
              event.preventDefault()
              setErrorMessage('')
              mutation.mutate()
            }}
          >
            {mutation.isPending ? 'Deleting…' : 'Delete'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
