import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  createEpic,
  deleteEpic,
  listEpics,
  updateEpic,
  type EpicResponse,
} from '@/api/epics'
import { GENERIC_ERROR_MESSAGE } from '@/api/problem'
import { listTeams, type TeamResponse } from '@/api/teams'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { formatTimestamp } from '@/lib/utils'

const ALL_TEAMS = 'all'

/**
 * Epic management screen (D2, D6): lists epics from `GET /api/v1/epics`, narrowed by a team filter
 * Select (`?teamId=`). Team names are resolved from the cached teams query. Create fixes the team at
 * creation; edit shows the team read-only and never sends it; delete is a confirm AlertDialog that
 * surfaces the backend `409` inline. With no teams, create prompts to make a team first.
 */
export function EpicsPage() {
  const [teamFilter, setTeamFilter] = useState<string | undefined>(undefined)
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<EpicResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<EpicResponse | null>(null)

  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: ({ signal }) => listTeams({ signal }),
  })
  const epicsQuery = useQuery({
    queryKey: ['epics', teamFilter ?? ALL_TEAMS],
    queryFn: ({ signal }) => listEpics(teamFilter, { signal }),
  })

  if (teamsQuery.isPending || epicsQuery.isPending) {
    return <LoadingState label="Loading epics…" />
  }

  if (teamsQuery.isError || epicsQuery.isError || !teamsQuery.data || !epicsQuery.data) {
    return (
      <ErrorState
        message="Could not load epics."
        onRetry={() => {
          void teamsQuery.refetch()
          void epicsQuery.refetch()
        }}
        retrying={teamsQuery.isFetching || epicsQuery.isFetching}
      />
    )
  }

  const teams = teamsQuery.data
  const epics = epicsQuery.data
  const teamNamesById = new Map(teams.map((team) => [team.id, team.name]))

  return (
    <section className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <h1 className="text-display-sm">Epics</h1>
        <div className="flex items-center gap-3">
          <Select
            value={teamFilter ?? ALL_TEAMS}
            onValueChange={(value) => setTeamFilter(value === ALL_TEAMS ? undefined : value)}
          >
            <SelectTrigger aria-label="Filter by team" className="w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_TEAMS}>All teams</SelectItem>
              {teams.map((team) => (
                <SelectItem key={team.id} value={team.id}>
                  {team.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={() => setCreateOpen(true)}>New epic</Button>
        </div>
      </header>

      {epics.length === 0 ? (
        <EmptyState message="No epics here yet." />
      ) : (
        <ul className="flex flex-col gap-2">
          {epics.map((epic) => (
            <EpicRow
              key={epic.id}
              epic={epic}
              teamName={teamNamesById.get(epic.teamId) ?? 'Unknown team'}
              onEdit={() => setEditTarget(epic)}
              onDelete={() => setDeleteTarget(epic)}
            />
          ))}
        </ul>
      )}

      {createOpen ? (
        <EpicCreateDialog teams={teams} defaultTeamId={teamFilter} onOpenChange={setCreateOpen} />
      ) : null}
      {editTarget ? (
        <EpicEditDialog
          epic={editTarget}
          teamName={teamNamesById.get(editTarget.teamId) ?? 'Unknown team'}
          onOpenChange={() => setEditTarget(null)}
        />
      ) : null}
      {deleteTarget ? (
        <DeleteEpicDialog epic={deleteTarget} onOpenChange={() => setDeleteTarget(null)} />
      ) : null}
    </section>
  )
}

function EpicRow({
  epic,
  teamName,
  onEdit,
  onDelete,
}: {
  epic: EpicResponse
  teamName: string
  onEdit: () => void
  onDelete: () => void
}) {
  return (
    <li className="flex items-center gap-4 rounded-md border border-hairline bg-canvas p-4">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="truncate text-body-md-strong text-ink">{epic.title}</span>
        <span className="text-caption text-mute">
          {teamName} · Created {formatTimestamp(epic.createdAt)} · Updated{' '}
          {formatTimestamp(epic.modifiedAt)}
        </span>
      </div>
      <div className="ml-auto flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={onEdit} aria-label={`Edit ${epic.title}`}>
          Edit
        </Button>
        <Button variant="outline" size="sm" onClick={onDelete} aria-label={`Delete ${epic.title}`}>
          Delete
        </Button>
      </div>
    </li>
  )
}

/**
 * Create dialog (D5, D6): required team Select (pre-filled from an active filter), required title, and
 * optional description. With no teams it prompts to create a team first instead of an unfillable form.
 * Mutation errors (`400`/`404`) render inline with entered values preserved.
 */
function EpicCreateDialog({
  teams,
  defaultTeamId,
  onOpenChange,
}: {
  teams: TeamResponse[]
  defaultTeamId?: string
  onOpenChange: (open: boolean) => void
}) {
  const noTeams = teams.length === 0
  const [teamId, setTeamId] = useState(defaultTeamId ?? '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => createEpic({ teamId, title, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['epics'] })
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!teamId) {
      return
    }
    setErrorMessage('')
    mutation.mutate()
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create epic</DialogTitle>
          <DialogDescription>
            {noTeams ? 'Epics belong to a team.' : 'Pick a team, then describe the epic.'}
          </DialogDescription>
        </DialogHeader>
        {noTeams ? (
          <div className="flex flex-col gap-4">
            <p className="text-body text-body-sm" role="status">
              Create a team first — every epic belongs to a team.
            </p>
            <DialogFooter>
              <Button type="button" onClick={() => onOpenChange(false)}>
                Close
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="epic-team">Team</Label>
              <Select value={teamId} onValueChange={setTeamId}>
                <SelectTrigger id="epic-team" className="w-full">
                  <SelectValue placeholder="Select a team" />
                </SelectTrigger>
                <SelectContent>
                  {teams.map((team) => (
                    <SelectItem key={team.id} value={team.id}>
                      {team.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="epic-title">Title</Label>
              <Input
                id="epic-title"
                required
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="epic-description">Description</Label>
              <Textarea
                id="epic-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
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
              <Button type="submit" disabled={mutation.isPending || !teamId || !title}>
                {mutation.isPending ? 'Creating…' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

/**
 * Edit dialog (D5): title and description pre-filled; the team is read-only text (no control), and the
 * `PUT` carries no team so it stays fixed.
 */
function EpicEditDialog({
  epic,
  teamName,
  onOpenChange,
}: {
  epic: EpicResponse
  teamName: string
  onOpenChange: (open: boolean) => void
}) {
  const [title, setTitle] = useState(epic.title)
  const [description, setDescription] = useState(epic.description ?? '')
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => updateEpic(epic.id, { title, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['epics'] })
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage('')
    mutation.mutate()
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit epic</DialogTitle>
          <DialogDescription>
            Update the title and description. The team can’t change.
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <span className="text-body-sm-strong text-ink">Team</span>
            <span className="text-body text-body-sm" data-testid="epic-team-readonly">
              {teamName}
            </span>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="epic-title">Title</Label>
            <Input
              id="epic-title"
              required
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="epic-description">Description</Label>
            <Textarea
              id="epic-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
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
            <Button type="submit" disabled={mutation.isPending || !title}>
              {mutation.isPending ? 'Saving…' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Confirm-delete AlertDialog (D4). The action's default close is suppressed so a `409` (ticket
 * reference, post-E7) keeps the dialog open with the backend `detail`; a `204` invalidates and closes.
 */
function DeleteEpicDialog({
  epic,
  onOpenChange,
}: {
  epic: EpicResponse
  onOpenChange: (open: boolean) => void
}) {
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => deleteEpic(epic.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['epics'] })
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
          <AlertDialogTitle>Delete epic?</AlertDialogTitle>
          <AlertDialogDescription>
            “{epic.title}” will be permanently deleted. This cannot be undone.
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
