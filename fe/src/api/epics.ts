/**
 * Epics API: typed calls against `/api/v1/epics`, mirroring the backend `EpicController` contracts —
 * `list(teamId?)` → `EpicResponse[]`, `create`/`update` → `EpicResponse`, `delete` → 204. The team is
 * fixed at creation and carries no field on update (D1). Follows the `auth.ts` shape: typed response
 * mirroring the DTO, runtime parsing, {@link ApiError} on non-success carrying the problem `detail`.
 */
import { apiFetch } from './client'
import { ApiError, isObject, problemError } from './problem'

export const EPICS_PATH = '/api/v1/epics'

/**
 * Backend `EpicResponse(id, teamId, title, description?, createdAt, modifiedAt)` — camelCase JSON,
 * ISO-8601 UTC timestamps. `description` is optional (nullable on the backend).
 */
export interface EpicResponse {
  id: string
  teamId: string
  title: string
  description?: string
  createdAt: string
  modifiedAt: string
}

/** Fields for creating an epic; the team is chosen here and fixed afterwards. */
export interface EpicCreateInput {
  teamId: string
  title: string
  description?: string
}

/** Fields for updating an epic; carries no team, so the epic's team stays fixed. */
export interface EpicUpdateInput {
  title: string
  description?: string
}

/**
 * List epics, optionally narrowed to one team via `?teamId=`.
 *
 * @param options.signal cancellation signal from TanStack Query's `queryFn`
 * @throws ApiError on a non-success status
 */
export async function listEpics(
  teamId?: string,
  options: { signal?: AbortSignal } = {},
): Promise<EpicResponse[]> {
  const path = teamId ? `${EPICS_PATH}?teamId=${encodeURIComponent(teamId)}` : EPICS_PATH
  const res = await apiFetch(path, { signal: options.signal })
  if (!res.ok) {
    throw await problemError(res)
  }
  const payload: unknown = await res.json()
  if (!Array.isArray(payload)) {
    throw new Error('epics response is not an array')
  }
  return payload.map(parseEpic)
}

/**
 * Create an epic under the chosen team.
 *
 * @throws ApiError on a non-success status (400 invalid title/description, 404 unknown team)
 */
export async function createEpic(input: EpicCreateInput): Promise<EpicResponse> {
  const res = await apiFetch(EPICS_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      teamId: input.teamId,
      title: input.title,
      description: input.description || undefined,
    }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseEpic(await res.json())
}

/**
 * Update an epic's title and description. The team is immutable, so it is not sent.
 *
 * @throws ApiError on a non-success status (400 invalid title/description, 404 unknown epic)
 */
export async function updateEpic(id: string, input: EpicUpdateInput): Promise<EpicResponse> {
  const res = await apiFetch(`${EPICS_PATH}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: input.title, description: input.description || undefined }),
  })
  if (!res.ok) {
    throw await problemError(res)
  }
  return parseEpic(await res.json())
}

/**
 * Delete an epic.
 *
 * @throws ApiError on a non-success status (409 when tickets reference the epic, 404 unknown epic)
 */
export async function deleteEpic(id: string): Promise<void> {
  const res = await apiFetch(`${EPICS_PATH}/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw await problemError(res)
  }
}

function parseEpic(payload: unknown): EpicResponse {
  if (
    !isObject(payload) ||
    typeof payload.id !== 'string' ||
    typeof payload.teamId !== 'string' ||
    typeof payload.title !== 'string' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.modifiedAt !== 'string'
  ) {
    throw new Error('epic response is missing required fields')
  }
  return {
    id: payload.id,
    teamId: payload.teamId,
    title: payload.title,
    description: typeof payload.description === 'string' ? payload.description : undefined,
    createdAt: payload.createdAt,
    modifiedAt: payload.modifiedAt,
  }
}

// Re-exported so screens can catch it without importing the problem module directly.
export { ApiError }
