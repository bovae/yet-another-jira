/** Shared loading indicator for data-fetching screens. Pair with TanStack Query's pending status. */
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      className="flex items-center gap-3 rounded-md border border-hairline bg-canvas p-6 text-body text-body-sm"
      role="status"
    >
      <span
        className="size-4 animate-spin rounded-full border-2 border-hairline border-t-link motion-reduce:animate-none"
        aria-hidden="true"
      />
      {label}
    </div>
  )
}
