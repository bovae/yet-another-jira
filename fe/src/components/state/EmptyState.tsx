/** Shared empty state for a successful fetch that returned no items. */
export function EmptyState({ message }: { message: string }) {
  return (
    <div
      className="rounded-md border border-hairline bg-canvas p-6 text-mute text-body-sm"
      role="status"
    >
      {message}
    </div>
  )
}
