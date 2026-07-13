/**
 * Shared test helpers: `fetch`/`apiFetch` Response builders, a QueryClient-providing render wrapper,
 * and fixture builders for the API response shapes. Keeps the mocking approach identical to the
 * existing suite (a stubbed `fetch` returning these Responses; TanStack Query with retries disabled).
 */
import { render, type RenderResult } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import type { ReactElement } from 'react'
import type { TeamResponse } from '@/api/teams'
import type { EpicResponse } from '@/api/epics'
import type { TicketResponse } from '@/api/tickets'
import type { CommentResponse } from '@/api/comments'

const TS = '2026-07-12T00:00:00Z'

/** A JSON `Response` (default 200) as the backend returns for success bodies. */
export function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })
}

/** An RFC 9457 problem `Response` — what the backend returns on failures. */
export function problemResponse(status: number, detail: string): Response {
  return jsonResponse({ type: 'about:blank', title: 'Error', status, detail }, { status })
}

/** A QueryClient with retries disabled, so error paths surface immediately in tests. */
export function makeQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
}

/**
 * Render `ui` inside a fresh `QueryClientProvider` (retries off), optionally wrapped in a
 * `MemoryRouter` when `initialEntries` is given. Returns the created `client` so a test can spy on
 * its cache methods (`invalidateQueries`, `removeQueries`, …).
 */
export function renderWithClient(
  ui: ReactElement,
  options: { client?: QueryClient; initialEntries?: string[] } = {},
): RenderResult & { client: QueryClient } {
  const client = options.client ?? makeQueryClient()
  const tree = <QueryClientProvider client={client}>{ui}</QueryClientProvider>
  const result = render(
    options.initialEntries ? (
      <MemoryRouter initialEntries={options.initialEntries}>{tree}</MemoryRouter>
    ) : (
      tree
    ),
  )
  return { client, ...result }
}

export function team(overrides: Partial<TeamResponse> = {}): TeamResponse {
  return { id: 't1', name: 'Alpha', createdAt: TS, modifiedAt: TS, ...overrides }
}

export function epic(overrides: Partial<EpicResponse> = {}): EpicResponse {
  return {
    id: 'e1',
    teamId: 't1',
    title: 'Onboarding',
    createdAt: TS,
    modifiedAt: TS,
    ...overrides,
  }
}

export function ticket(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 'k1',
    teamId: 't1',
    epicId: 'e1',
    type: 'bug',
    state: 'new',
    title: 'Login broken',
    body: 'Steps to reproduce',
    createdBy: 'u1',
    createdByEmail: 'creator@example.com',
    createdAt: TS,
    modifiedAt: TS,
    ...overrides,
  }
}

export function comment(overrides: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 'c1',
    ticketId: 'k1',
    authorId: 'u1',
    authorEmail: 'author@example.com',
    body: 'First!',
    createdAt: TS,
    ...overrides,
  }
}
