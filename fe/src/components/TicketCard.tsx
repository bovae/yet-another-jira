import type { BoardCard } from '../api/board'

/** Human-readable labels for the ticket types returned by the backend. */
const TYPE_LABELS: Record<BoardCard['type'], string> = {
  bug: 'Bug',
  feature: 'Feature',
  fix: 'Fix',
}

interface TicketCardProps {
  card: BoardCard
}

/** A single ticket card: title, type badge, and optional epic tag. */
export function TicketCard({ card }: TicketCardProps) {
  return (
    <article className="ticket-card" data-testid="ticket-card">
      <p className="ticket-card__title">{card.title}</p>
      <div className="ticket-card__meta">
        <span className={`ticket-card__type ticket-card__type--${card.type}`}>
          {TYPE_LABELS[card.type]}
        </span>
        {card.epic ? <span className="ticket-card__epic">{card.epic}</span> : null}
      </div>
    </article>
  )
}
