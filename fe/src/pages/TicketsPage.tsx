import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { listEpics } from '@/api/epics'
import { listTeams } from '@/api/teams'
import { listTickets, ticketStateLabel, ticketTypeLabel, type TicketResponse } from '@/api/tickets'
import { TicketFormDialog } from '@/components/tickets/TicketFormDialog'
import { EmptyState } from '@/components/state/EmptyState'
import { ErrorState } from '@/components/state/ErrorState'
import { LoadingState } from '@/components/state/LoadingState'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const ALL_TEAMS = 'all'

/**
 * Ticket list screen (D1): lists tickets from `GET /api/v1/tickets`, narrowed by a team filter Select
 * (`?teamId=`). Team names and epic titles are resolved from the cached teams/epics queries. Rows link
 * to the details view; "New ticket" opens the shared create dialog. This is the create/open entry point
 * until the real board (E15) lands.
 */
export function TicketsPage() {
  const [teamFilter, setTeamFilter] = useState<string | undefined>(undefined)
  const [createOpen, setCreateOpen] = useState(false)

  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: ({ signal }) => listTeams({ signal }),
  })
  const ticketsQuery = useQuery({
    queryKey: ['tickets', teamFilter ?? ALL_TEAMS],
    queryFn: ({ signal }) => listTickets(teamFilter, { signal }),
  })
  // Epic titles are display sugar; a failure here just leaves the epic column blank, so it doesn't gate
  // the screen.
  const epicsQuery = useQuery({
    queryKey: ['epics', ALL_TEAMS],
    queryFn: ({ signal }) => listEpics(undefined, { signal }),
  })

  if (teamsQuery.isPending || ticketsQuery.isPending) {
    return <LoadingState label="Loading tickets…" />
  }

  if (teamsQuery.isError || ticketsQuery.isError || !teamsQuery.data || !ticketsQuery.data) {
    return (
      <ErrorState
        message="Could not load tickets."
        onRetry={() => {
          void teamsQuery.refetch()
          void ticketsQuery.refetch()
        }}
        retrying={teamsQuery.isFetching || ticketsQuery.isFetching}
      />
    )
  }

  const teams = teamsQuery.data
  const tickets = ticketsQuery.data
  const teamNamesById = new Map(teams.map((team) => [team.id, team.name]))
  const epicTitlesById = new Map((epicsQuery.data ?? []).map((epic) => [epic.id, epic.title]))

  return (
    <section className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <h1 className="text-display-sm">Tickets</h1>
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
          <Button onClick={() => setCreateOpen(true)}>New ticket</Button>
        </div>
      </header>

      {tickets.length === 0 ? (
        <EmptyState message="No tickets here yet." />
      ) : (
        <div className="overflow-x-auto rounded-md border border-hairline bg-canvas">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-hairline border-b">
                <th className="px-4 py-2 text-caption text-mute">Title</th>
                <th className="px-4 py-2 text-caption text-mute">Type</th>
                <th className="px-4 py-2 text-caption text-mute">State</th>
                <th className="px-4 py-2 text-caption text-mute">Team</th>
                <th className="px-4 py-2 text-caption text-mute">Epic</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <TicketRow
                  key={ticket.id}
                  ticket={ticket}
                  teamName={teamNamesById.get(ticket.teamId) ?? 'Unknown team'}
                  epicTitle={ticket.epicId ? (epicTitlesById.get(ticket.epicId) ?? '—') : '—'}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {createOpen ? (
        <TicketFormDialog teams={teams} defaultTeamId={teamFilter} onOpenChange={setCreateOpen} />
      ) : null}
    </section>
  )
}

function TicketRow({
  ticket,
  teamName,
  epicTitle,
}: {
  ticket: TicketResponse
  teamName: string
  epicTitle: string
}) {
  return (
    <tr className="border-hairline border-t first:border-t-0">
      <td className="px-4 py-3 text-body-sm">
        <Link to={`/tickets/${ticket.id}`} className="text-link hover:text-link-deep">
          {ticket.title}
        </Link>
      </td>
      <td className="px-4 py-3 text-body-sm text-body">{ticketTypeLabel(ticket.type)}</td>
      <td className="px-4 py-3 text-body-sm text-body">{ticketStateLabel(ticket.state)}</td>
      <td className="px-4 py-3 text-body-sm text-body">{teamName}</td>
      <td className="px-4 py-3 text-body-sm text-body">{epicTitle}</td>
    </tr>
  )
}
