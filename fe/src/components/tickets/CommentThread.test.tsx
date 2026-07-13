import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { CommentThread } from './CommentThread'
import { ApiError } from '@/api/problem'
import { comment, renderWithClient } from '@/test/helpers'

vi.mock('@/api/comments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/comments')>()),
  listComments: vi.fn(),
  addComment: vi.fn(),
}))

import { addComment, listComments } from '@/api/comments'

const listCommentsMock = listComments as Mock
const addCommentMock = addComment as Mock

function renderThread() {
  return renderWithClient(<CommentThread ticketId="k1" />)
}

describe('CommentThread', () => {
  beforeEach(() => {
    listCommentsMock.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('list_shouldRenderOldestFirstAndResolveAuthorEmailWithRawIdFallback', async () => {
    listCommentsMock.mockResolvedValue([
      comment({
        id: 'c2',
        authorId: 'u2',
        authorEmail: null,
        body: 'newer',
        createdAt: '2026-07-12T02:00:00Z',
      }),
      comment({
        id: 'c1',
        authorId: 'u1',
        authorEmail: 'ada@example.com',
        body: 'older',
        createdAt: '2026-07-12T01:00:00Z',
      }),
    ])
    renderThread()

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('older')
    expect(items[1]).toHaveTextContent('newer')
    // A resolved author shows the email; an unresolved one falls back to the raw id.
    expect(items[0]).toHaveTextContent('ada@example.com')
    expect(items[1]).toHaveTextContent('u2')
  })

  it('list_shouldRenderEmptyState_whenNoComments', async () => {
    renderThread()

    expect(await screen.findByText(/no comments yet/i)).toBeInTheDocument()
  })

  it('add_shouldAppendCommentAndClearForm_whenSuccess', async () => {
    const older = comment({ id: 'c1', body: 'older', createdAt: '2026-07-12T01:00:00Z' })
    const mine = comment({ id: 'c2', body: 'my new comment', createdAt: '2026-07-12T03:00:00Z' })
    listCommentsMock.mockResolvedValueOnce([older]).mockResolvedValue([older, mine])
    addCommentMock.mockResolvedValue(mine)
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
