import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listEpics } from '@/api/epics'
import { GENERIC_ERROR_MESSAGE } from '@/api/problem'
import type { TeamResponse } from '@/api/teams'
import {
  ApiError,
  TICKET_STATES,
  TICKET_TYPES,
  createTicket,
  updateTicket,
  type TicketResponse,
} from '@/api/tickets'
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

/** Sentinel for the "no epic" Select choice; maps to omitting `epicId` on submit. */
const NO_EPIC = 'none'

/**
 * Shared create/edit ticket dialog (D2, D3). `ticket` present → edit (`PUT`), absent → create (`POST`).
 * Team, type, and state are required Selects; the epic Select is scoped to the chosen team (with a
 * "None" choice) and reloads its options whenever the team changes — switching team resets the epic to
 * "None". Mutation errors render inline with entered values preserved. With no teams, create prompts to
 * make a team first instead of an unfillable form.
 */
export function TicketFormDialog({
  teams,
  ticket,
  defaultTeamId,
  onOpenChange,
}: {
  teams: TeamResponse[]
  ticket?: TicketResponse
  defaultTeamId?: string
  onOpenChange: (open: boolean) => void
}) {
  const isEdit = ticket !== undefined
  const noTeams = teams.length === 0
  const [teamId, setTeamId] = useState(ticket?.teamId ?? defaultTeamId ?? '')
  const [type, setType] = useState(ticket?.type ?? TICKET_TYPES[0].code)
  const [state, setState] = useState(ticket?.state ?? TICKET_STATES[0].code)
  const [epicId, setEpicId] = useState(ticket?.epicId ?? NO_EPIC)
  const [title, setTitle] = useState(ticket?.title ?? '')
  const [body, setBody] = useState(ticket?.body ?? '')
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  // Epic options for the currently selected team; disabled until a team is chosen (D3). Cached per
  // teamId, so switching back and forth reuses the earlier fetch.
  const epicsQuery = useQuery({
    queryKey: ['epics', teamId],
    queryFn: ({ signal }) => listEpics(teamId, { signal }),
    enabled: teamId !== '',
  })
  const epics = epicsQuery.data ?? []

  const mutation = useMutation({
    mutationFn: () => {
      const input = {
        teamId,
        type,
        state,
        epicId: epicId === NO_EPIC ? undefined : epicId,
        title,
        body,
      }
      return isEdit ? updateTicket(ticket.id, input) : createTicket(input)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
      // The board reads the same tickets; keep it fresh after a create or edit from any screen (D6).
      void queryClient.invalidateQueries({ queryKey: ['board'] })
      if (isEdit) {
        void queryClient.invalidateQueries({ queryKey: ['ticket', ticket.id] })
      }
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  function handleTeamChange(value: string) {
    setTeamId(value)
    // Cross-team epics are invalid, so clear the selection whenever the team changes (D3).
    setEpicId(NO_EPIC)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!teamId || !title.trim() || !body.trim()) {
      return
    }
    setErrorMessage('')
    mutation.mutate()
  }

  const canSubmit = teamId !== '' && title.trim() !== '' && body.trim() !== ''

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit ticket' : 'Create ticket'}</DialogTitle>
          <DialogDescription>
            {noTeams
              ? 'Tickets belong to a team.'
              : 'Pick a team, type, and state, then describe the ticket.'}
          </DialogDescription>
        </DialogHeader>
        {noTeams ? (
          <div className="flex flex-col gap-4">
            <p className="text-body text-body-sm" role="status">
              Create a team first — every ticket belongs to a team.
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
              <Label htmlFor="ticket-team">Team</Label>
              <Select value={teamId} onValueChange={handleTeamChange}>
                <SelectTrigger id="ticket-team" className="w-full">
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
            <div className="flex gap-4">
              <div className="flex flex-1 flex-col gap-1.5">
                <Label htmlFor="ticket-type">Type</Label>
                <Select value={type} onValueChange={setType}>
                  <SelectTrigger id="ticket-type" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TICKET_TYPES.map((option) => (
                      <SelectItem key={option.code} value={option.code}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-1 flex-col gap-1.5">
                <Label htmlFor="ticket-state">State</Label>
                <Select value={state} onValueChange={setState}>
                  <SelectTrigger id="ticket-state" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TICKET_STATES.map((option) => (
                      <SelectItem key={option.code} value={option.code}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ticket-epic">Epic</Label>
              <Select value={epicId} onValueChange={setEpicId} disabled={teamId === ''}>
                <SelectTrigger id="ticket-epic" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_EPIC}>None</SelectItem>
                  {epics.map((epic) => (
                    <SelectItem key={epic.id} value={epic.id}>
                      {epic.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ticket-title">Title</Label>
              <Input
                id="ticket-title"
                required
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="ticket-body">Body</Label>
              <Textarea
                id="ticket-body"
                required
                value={body}
                onChange={(event) => setBody(event.target.value)}
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
              <Button type="submit" disabled={mutation.isPending || !canSubmit}>
                {mutation.isPending ? 'Saving…' : isEdit ? 'Save' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
