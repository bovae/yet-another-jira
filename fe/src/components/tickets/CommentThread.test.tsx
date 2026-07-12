import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { CommentThread } from './CommentThread'
import { ApiError } from '@/api/problem'

vi.mock('@/api/comments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/comments')>()),
  listComments: vi.fn(),
  addComment: vi.fn(),
}))

import { addComment, listComments } from '@/api/comments'

const listCommentsMock = listComments as Mock
const addCommentMock = addComment as Mock

const ME = { id: 'u1', email: 'me@example.com' }

function comment(id: string, authorId: string, body: string, createdAt: string) {
  return { id, ticketId: 'k1', authorId, body, createdAt }
}

function renderThread() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <CommentThread ticketId="k1" me={ME} />
    </QueryClientProvider>,
  )
}

describe('CommentThread', () => {
  beforeEach(() => {
    listCommentsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('list_shouldRenderOldestFirstWithResolvedAuthors', async () => {
    listCommentsMock.mockResolvedValue([
      comment('c2', 'u2', 'newer', '2026-07-12T02:00:00Z'),
      comment('c1', 'u1', 'older', '2026-07-12T01:00:00Z'),
    ])
    renderThread()

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('older')
    expect(items[1]).toHaveTextContent('newer')
    // Own comment shows email; another user's shows the raw id.
    expect(items[0]).toHaveTextContent('me@example.com')
    expect(items[1]).toHaveTextContent('u2')
  })

  it('list_shouldRenderEmptyState_whenNoComments', async () => {
    renderThread()

    expect(await screen.findByText(/no comments yet/i)).toBeInTheDocument()
  })

  it('add_shouldAppendCommentAndClearForm_whenSuccess', async () => {
    listCommentsMock
      .mockResolvedValueOnce([comment('c1', 'u1', 'older', '2026-07-12T01:00:00Z')])
      .mockResolvedValue([
        comment('c1', 'u1', 'older', '2026-07-12T01:00:00Z'),
        comment('c2', 'u1', 'my new comment', '2026-07-12T03:00:00Z'),
      ])
    addCommentMock.mockResolvedValue(comment('c2', 'u1', 'my new comment', '2026-07-12T03:00:00Z'))
    const user = userEvent.setup()
    renderThread()

    const textarea = await screen.findByRole('textbox', { name: /add a comment/i })
    await user.type(textarea, 'my new comment')
    await user.click(screen.getByRole('button', { name: /^comment$/i }))

    expect(await screen.findByText('my new comment')).toBeInTheDocument()
    await waitFor(() => expect(textarea).toHaveValue(''))
    expect(addCommentMock).toHaveBeenCalledWith('k1', 'my new comment')
  })

  it('add_shouldNotSubmit_whenBodyBlank', async () => {
    const user = userEvent.setup()
    renderThread()

    const button = await screen.findByRole('button', { name: /^comment$/i })
    expect(button).toBeDisabled()

    await user.type(screen.getByRole('textbox', { name: /add a comment/i }), '   ')
    expect(button).toBeDisabled()
    expect(addCommentMock).not.toHaveBeenCalled()
  })

  it('add_shouldSurfaceErrorAndPreserveBody_whenRejected', async () => {
    addCommentMock.mockRejectedValue(new ApiError(400, 'Comment is too long.'))
    const user = userEvent.setup()
    renderThread()

    const textarea = await screen.findByRole('textbox', { name: /add a comment/i })
    await user.type(textarea, 'keep me')
    await user.click(screen.getByRole('button', { name: /^comment$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Comment is too long.')
    expect(textarea).toHaveValue('keep me')
  })
})
