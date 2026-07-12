/**
 * Shared error state: a message and an optional retry control. When `onRetry` is provided the retry
 * button re-triggers the fetch; `retrying` disables it while the retry is in flight.
 */
export function ErrorState({
  message,
  onRetry,
  retrying = false,
}: {
  message: string
  onRetry?: () => void
  retrying?: boolean
}) {
  return (
    <div
      className="flex items-center gap-3 rounded-md border border-error-soft bg-error-soft p-6 text-error text-body-sm"
      role="alert"
    >
      <span>{message}</span>
      {onRetry ? (
        <button
          type="button"
          className="ml-auto cursor-pointer rounded-sm border border-error bg-canvas px-3 py-1 text-error text-button-md hover:bg-canvas-soft disabled:cursor-default disabled:opacity-60"
          onClick={onRetry}
          disabled={retrying}
        >
          {retrying ? 'Retrying…' : 'Try again'}
        </button>
      ) : null}
    </div>
  )
}
