import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { listEpics } from '@/api/epics'
import { GENERIC_ERROR_MESSAGE } from '@/api/problem'
import { listTeams } from '@/api/teams'
import { ApiError, deleteTicket, getTicket, ticketStateLabel, ticketTypeLabel } from '@/api/tickets'
import { useAuth } from '@/auth/auth-context'
import { CommentThread } from '@/components/tickets/CommentThread'
import { TicketFormDialog } from '@/components/tickets/TicketFormDialog'
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
import { formatTimestamp } from '@/lib/utils'

/**
 * Ticket details view (D5): shows every ticket field plus metadata, the comment thread, and edit/delete
 * actions. Runs the `ticket(id)` query alongside teams/epics reference queries (for team name and epic
 * title). A `404` renders a not-found panel rather than crashing. Created-by shows the current user's
 * email when the id matches, otherwise the raw id in mono (D6).
 */
export function TicketDetailsPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const ticketQuery = useQuery({
    queryKey: ['ticket', id],
    queryFn: ({ signal }) => getTicket(id ?? '', { signal }),
    enabled: id !== undefined,
    retry: false,
  })
  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: ({ signal }) => listTeams({ signal }),
  })
  const epicsQuery = useQuery({
    queryKey: ['epics', 'all'],
    queryFn: ({ signal }) => listEpics(undefined, { signal }),
  })

  if (
    ticketQuery.isError &&
    ticketQuery.error instanceof ApiError &&
    ticketQuery.error.status === 404
  ) {
    return <TicketNotFound />
  }

  if (ticketQuery.isPending || teamsQuery.isPending) {
    return <LoadingState label="Loading ticket…" />
  }

  if (ticketQuery.isError || !ticketQuery.data) {
    return (
      <ErrorState
        message="Could not load the ticket."
        onRetry={() => void ticketQuery.refetch()}
        retrying={ticketQuery.isFetching}
      />
    )
  }

  const ticket = ticketQuery.data
  const teams = teamsQuery.data ?? []
  const teamName = teams.find((team) => team.id === ticket.teamId)?.name ?? 'Unknown team'
  const epicTitle = ticket.epicId
    ? ((epicsQuery.data ?? []).find((epic) => epic.id === ticket.epicId)?.title ?? 'Unknown epic')
    : 'None'
  const createdBy =
    user && user.id === ticket.createdBy ? (
      user.email
    ) : (
      <span className="font-mono text-caption-mono">{ticket.createdBy}</span>
    )

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-col gap-6">
        <header className="flex items-start justify-between gap-4">
          <h1 className="text-display-sm">{ticket.title}</h1>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
              Edit
            </Button>
            <Button variant="outline" size="sm" onClick={() => setDeleteOpen(true)}>
              Delete
            </Button>
          </div>
        </header>

        <p className="whitespace-pre-wrap text-body-sm text-ink">{ticket.body}</p>

        <dl className="grid grid-cols-2 gap-x-8 gap-y-3 sm:grid-cols-3">
          <Field label="Type">{ticketTypeLabel(ticket.type)}</Field>
          <Field label="State">{ticketStateLabel(ticket.state)}</Field>
          <Field label="Team">{teamName}</Field>
          <Field label="Epic">{epicTitle}</Field>
          <Field label="Created by">{createdBy}</Field>
          <Field label="Created">{formatTimestamp(ticket.createdAt)}</Field>
          <Field label="Updated">{formatTimestamp(ticket.modifiedAt)}</Field>
        </dl>
      </div>

      <CommentThread ticketId={ticket.id} me={user} />

      {editOpen ? (
        <TicketFormDialog teams={teams} ticket={ticket} onOpenChange={setEditOpen} />
      ) : null}
      {deleteOpen ? (
        <DeleteTicketDialog
          ticketId={ticket.id}
          title={ticket.title}
          onOpenChange={setDeleteOpen}
          onDeleted={() => void navigate('/tickets')}
        />
      ) : null}
    </section>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption text-mute">{label}</dt>
      <dd className="text-body-sm text-ink">{children}</dd>
    </div>
  )
}

function TicketNotFound() {
  return (
    <section className="rounded-md border border-hairline bg-canvas p-6">
      <h1 className="mb-1 text-display-sm">Ticket not found</h1>
      <p className="mb-4 text-body text-body-sm">This ticket doesn’t exist or was deleted.</p>
      <Link to="/tickets" className="text-link text-body-sm hover:text-link-deep">
        Back to tickets
      </Link>
    </section>
  )
}

/**
 * Confirm-delete AlertDialog. The action's default close is suppressed so an error keeps the dialog open
 * with the backend `detail`; a `204` invalidates the list and navigates back to `/tickets`.
 */
function DeleteTicketDialog({
  ticketId,
  title,
  onOpenChange,
  onDeleted,
}: {
  ticketId: string
  title: string
  onOpenChange: (open: boolean) => void
  onDeleted: () => void
}) {
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => deleteTicket(ticketId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
      onDeleted()
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  return (
    <AlertDialog open onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete ticket?</AlertDialogTitle>
          <AlertDialogDescription>
            “{title}” will be permanently deleted. This cannot be undone.
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
