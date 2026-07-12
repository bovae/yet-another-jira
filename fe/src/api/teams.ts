/**
 * Teams API: typed calls against `/api/v1/teams`, mirroring the backend `TeamController` contracts —
 * `list` → `TeamResponse[]`, `create`/`rename` → `TeamResponse`, `delete` → 204. Follows the
 * `auth.ts` shape (D1): path constant, typed response mirroring the DTO, runtime payload parsing,
 * {@link ApiError} on non-success carrying the RFC 9457 problem `detail`.
 */
import { apiFetch } from './client'
import { ApiError, isObject, problemError } from './problem'

export const TEAMS_PATH = '/api/v1/teams'

/** Backend `TeamResponse(id, name, createdAt, modifiedAt)` — camelCase JSON, ISO-8601 UTC timestamps. */
export interface TeamResponse {
  id: string
  name: string
  createdAt: string
  modifiedAt: string
}

/**
 * List all teams.
 *
 * @param options.signal cancellation signal from TanStack Query's `queryFn`, so a superseded query
 *   aborts the in-flight request
 * @throws ApiError on a non-success status
 */
export async function listTeams(options: { signal?: AbortSignal } = {}): Promise<TeamResponse[]> {
  const res = await apiFetch(TEAMS_PATH, { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  const payload: unknown = await res.json()
  if (!Array.isArray(payload)) {
    throw new Error('teams response is not an array')
  }
  return payload.map(parseTeam)
}

/**
 * Create a team.
 *
 * @throws ApiError on a non-success status (400 invalid name, 409 duplicate name)
 */
export async function createTeam(name: string): Promise<TeamResponse> {
  const res = await apiFetch(TEAMS_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTeam(await res.json())
}

/**
 * Rename a team.
 *
 * @throws ApiError on a non-success status (400 invalid name, 404 unknown team, 409 duplicate name)
 */
export async function renameTeam(id: string, name: string): Promise<TeamResponse> {
  const res = await apiFetch(`${TEAMS_PATH}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseTeam(await res.json())
}

/**
 * Delete a team.
 *
 * @throws ApiError on a non-success status (409 when the team is still referenced, 404 unknown team)
 */
export async function deleteTeam(id: string): Promise<void> {
  const res = await apiFetch(`${TEAMS_PATH}/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw await problemError(res)
  }
}

function parseTeam(payload: unknown): TeamResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.name !== 'string' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.modifiedAt !== 'string'
  ) {
    throw new Error('team response is missing required fields')
  }
  return {
    id: payload.id,
    name: payload.name,
    createdAt: payload.createdAt,
    modifiedAt: payload.modifiedAt,
  }
}

// Re-exported so screens can catch it without importing the problem module directly.
export { ApiError }
