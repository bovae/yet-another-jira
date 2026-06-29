import type { BoardCard } from '../api/board'

/** Human-readable labels for the ticket types returned by the backend. */
const TYPE_LABELS: Record<BoardCard['type'], string> = {
  bug: 'Bug',
  feature: 'Feature',
  fix: 'Fix',
}

/** DESIGN.md semantic-soft pairings per ticket type (background + text token). */
const TYPE_BADGE_CLASSES: Record<BoardCard['type'], string> = {
  bug: 'bg-error-soft text-error',
  feature: 'bg-link-bg-soft text-link',
  fix: 'bg-warning-soft text-warning-deep',
}

interface TicketCardProps {
  card: BoardCard
}

/** A single ticket card: title, type badge, and optional epic tag. */
export function TicketCard({ card }: TicketCardProps) {
  return (
    <article
      className="rounded-md border border-hairline bg-canvas p-3 shadow-card"
      data-testid="ticket-card"
    >
      <p className="mb-2 text-body-sm">{card.title}</p>
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={`rounded-full px-2 py-px font-mono text-caption-mono ${TYPE_BADGE_CLASSES[card.type]}`}
        >
          {TYPE_LABELS[card.type]}
        </span>
        {card.epic ? <span className="text-caption text-mute">{card.epic}</span> : null}
      </div>
    </article>
  )
}
