import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, addComment, listComments, type CommentResponse } from '@/api/comments'
import { GENERIC_ERROR_MESSAGE } from '@/api/problem'
import { EmptyState } from '@/components/state/EmptyState'
import { ErrorState } from '@/components/state/ErrorState'
import { LoadingState } from '@/components/state/LoadingState'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatTimestamp } from '@/lib/utils'

/**
 * Comment thread for the ticket details view (fe-comments): lists comments oldest first with author and
 * timestamp, and an append-only add form. Comments are immutable, so no edit/delete controls render. The
 * author renders the server-resolved `authorEmail`, falling back to the raw id in mono if unresolved.
 * Fetch/empty/error states are independent of the ticket fields.
 */
export function CommentThread({ ticketId }: { ticketId: string }) {
  const commentsQuery = useQuery({
    queryKey: ['comments', ticketId],
    queryFn: ({ signal }) => listComments(ticketId, { signal }),
  })

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-body-md-strong text-ink">Comments</h2>

      {commentsQuery.isPending ? (
        <LoadingState label="Loading comments…" />
      ) : commentsQuery.isError || !commentsQuery.data ? (
        <ErrorState
          message="Could not load comments."
          onRetry={() => void commentsQuery.refetch()}
          retrying={commentsQuery.isFetching}
        />
      ) : commentsQuery.data.length === 0 ? (
        <EmptyState message="No comments yet." />
      ) : (
        <ul className="flex flex-col gap-3">
          {sortByCreatedAt(commentsQuery.data).map((comment) => (
            <CommentItem key={comment.id} comment={comment} />
          ))}
        </ul>
      )}

      <AddCommentForm ticketId={ticketId} />
    </section>
  )
}

/** Oldest first. The backend already returns them ordered, but we sort defensively for display. */
function sortByCreatedAt(comments: CommentResponse[]): CommentResponse[] {
  return [...comments].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

function CommentItem({ comment }: { comment: CommentResponse }) {
  return (
    <li className="flex flex-col gap-1 rounded-md border border-hairline bg-canvas p-4">
      <div className="flex items-center gap-2 text-caption text-mute">
        {comment.authorEmail ? (
          <span>{comment.authorEmail}</span>
        ) : (
          <span className="font-mono text-caption-mono">{comment.authorId}</span>
        )}
        <span aria-hidden="true">·</span>
        <span>{formatTimestamp(comment.createdAt)}</span>
      </div>
      <p className="whitespace-pre-wrap text-body-sm text-ink">{comment.body}</p>
    </li>
  )
}

/**
 * Add-comment form: a required body. Blank/whitespace-only bodies are not submitted. On success the
 * comments query is invalidated (server returns oldest-first, so the new comment appends) and the form
 * clears; on failure the problem `detail` renders with the entered body preserved.
 */
function AddCommentForm({ ticketId }: { ticketId: string }) {
  const [body, setBody] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => addComment(ticketId, body.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['comments', ticketId] })
      setBody('')
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof ApiError ? error.message : GENERIC_ERROR_MESSAGE)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (body.trim() === '') {
      return
    }
    setErrorMessage('')
    mutation.mutate()
  }

  return (
    <form className="flex flex-col gap-2" onSubmit={handleSubmit}>
      <Label htmlFor="comment-body">Add a comment</Label>
      <Textarea
        id="comment-body"
        required
        value={body}
        onChange={(event) => setBody(event.target.value)}
      />
      {errorMessage ? (
        <p className="text-error text-body-sm" role="alert">
          {errorMessage}
        </p>
      ) : null}
      <div className="flex justify-end">
        <Button type="submit" disabled={mutation.isPending || body.trim() === ''}>
          {mutation.isPending ? 'Posting…' : 'Comment'}
        </Button>
      </div>
    </form>
  )
}
