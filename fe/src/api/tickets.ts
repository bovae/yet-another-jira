/**
 * Tickets API: typed calls against `/api/v1/tickets`, mirroring the backend `TicketController` contracts —
 * `list(teamId?)` → `TicketResponse[]`, `get`/`create`/`update` → `TicketResponse`, `delete` → 204. Both
 * team and epic are editable, so create and update carry the same field set; an omitted `epicId` clears
 * the epic on update (full replacement). Follows the `epics.ts` shape (D7): path constant, typed response
 * mirroring the DTO, runtime parsing, {@link ApiError} on non-success carrying the problem `detail`.
 */
import { apiFetch } from './client'
import { ApiError, isObject, problemError } from './problem'

export const TICKETS_PATH = '/api/v1/tickets'

/**
 * Canonical ticket type codes with human-readable labels, in the order the UI presents them (D4). The
 * API speaks the code; every screen shows the label. Kept beside the API module so E15's board reuses it.
 */
export const TICKET_TYPES = [
  { code: 'bug', label: 'Bug' },
  { code: 'feature', label: 'Feature' },
  { code: 'fix', label: 'Fix' },
] as const

/** Canonical ticket state codes with labels, in workflow order (D4). */
export const TICKET_STATES = [
  { code: 'new', label: 'New' },
  { code: 'ready_for_implementation', label: 'Ready for implementation' },
  { code: 'in_progress', label: 'In progress' },
  { code: 'ready_for_acceptance', label: 'Ready for acceptance' },
  { code: 'done', label: 'Done' },
] as const

/** Human-readable label for a ticket type code, falling back to the raw code for unknown values. */
export function ticketTypeLabel(code: string): string {
  return TICKET_TYPES.find((type) => type.code === code)?.label ?? code
}

/** Human-readable label for a ticket state code, falling back to the raw code for unknown values. */
export function ticketStateLabel(code: string): string {
  return TICKET_STATES.find((state) => state.code === code)?.label ?? code
}

/**
 * Backend `TicketResponse(id, teamId, epicId?, type, state, title, body, createdBy, createdAt, modifiedAt)`
 * — camelCase JSON, ISO-8601 UTC timestamps. `epicId` is optional (nullable on the backend).
 */
export interface TicketResponse {
  id: string
  teamId: string
  epicId?: string
  type: string
  state: string
  title: string
  body: string
  createdBy: string
  createdAt: string
  modifiedAt: string
}

/** Fields for creating or updating a ticket; team and epic are both editable. */
export interface TicketInput {
  teamId: string
  type: string
  state: string
  epicId?: string
  title: string
  body: string
}

/**
 * List tickets, optionally narrowed to one team via `?teamId=`.
 *
 * @param options.signal cancellation signal from TanStack Query's `queryFn`
 * @throws ApiError on a non-success status
 */
export async function listTickets(
  teamId?: string,
  options: { signal?: AbortSignal } = {},
): Promise<TicketResponse[]> {
  const path = teamId ? `${TICKETS_PATH}?teamId=${encodeURIComponent(teamId)}` : TICKETS_PATH
  const res = await apiFetch(path, { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  const payload: unknown = await res.json()
  if (!Array.isArray(payload)) {
    throw new Error('tickets response is not an array')
  }
  return payload.map(parseTicket)
}

/**
 * Fetch a single ticket by id.
 *
 * @param options.signal cancellation signal from TanStack Query's `queryFn`
 * @throws ApiError on a non-success status (404 unknown ticket)
 */
export async function getTicket(
  id: string,
  options: { signal?: AbortSignal } = {},
): Promise<TicketResponse> {
  const res = await apiFetch(`${TICKETS_PATH}/${id}`, { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTicket(await res.json())
}

/**
 * Create a ticket. A missing `epicId` is omitted so the ticket has no epic.
 *
 * @throws ApiError on a non-success status (400 invalid field, 404 unknown team/epic)
 */
export async function createTicket(input: TicketInput): Promise<TicketResponse> {
  const res = await apiFetch(TICKETS_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(toBody(input)),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTicket(await res.json())
}

/**
 * Full-replacement update of a ticket. An omitted `epicId` clears any stored epic.
 *
 * @throws ApiError on a non-success status (400 invalid field, 404 unknown ticket/team/epic)
 */
export async function updateTicket(id: string, input: TicketInput): Promise<TicketResponse> {
  const res = await apiFetch(`${TICKETS_PATH}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(toBody(input)),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTicket(await res.json())
}

/**
 * Change only a ticket's workflow state (D5) — the board's drag-and-drop contract. The endpoint is
 * idempotent, so re-issuing the same target state is safe.
 *
 * @throws ApiError on a non-success status (400 invalid state, 404 unknown ticket)
 */
export async function patchTicketState(id: string, state: string): Promise<TicketResponse> {
  const res = await apiFetch(`${TICKETS_PATH}/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ state }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTicket(await res.json())
}

/**
 * Delete a ticket.
 *
 * @throws ApiError on a non-success status (404 unknown ticket)
 */
export async function deleteTicket(id: string): Promise<void> {
  const res = await apiFetch(`${TICKETS_PATH}/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw await problemError(res)
  }
}

function toBody(input: TicketInput) {
  return {
    teamId: input.teamId,
    type: input.type,
    state: input.state,
    epicId: input.epicId || undefined,
    title: input.title,
    body: input.body,
  }
}

function parseTicket(payload: unknown): TicketResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.teamId !== 'string' ||
    typeof payload.type !== 'string' ||
    typeof payload.state !== 'string' ||
    typeof payload.title !== 'string' ||
    typeof payload.body !== 'string' ||
    typeof payload.createdBy !== 'string' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.modifiedAt !== 'string'
  ) {
    throw new Error('ticket response is missing required fields')
  }
  return {
    id: payload.id,
    teamId: payload.teamId,
    epicId: typeof payload.epicId === 'string' ? payload.epicId : undefined,
    type: payload.type,
    state: payload.state,
    title: payload.title,
    body: payload.body,
    createdBy: payload.createdBy,
    createdAt: payload.createdAt,
    modifiedAt: payload.modifiedAt,
  }
}

// Re-exported so screens can catch it without importing the problem module directly.
export { ApiError }
