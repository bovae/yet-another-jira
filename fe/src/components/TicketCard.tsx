import { useDraggable } from '@dnd-kit/core'
import { useNavigate } from 'react-router'
import type { BoardCard } from '@/api/board'
import { ticketTypeLabel } from '@/api/tickets'
import { cn } from '@/lib/utils'

/** DESIGN.md semantic-soft pairings per known ticket type; unknown codes fall back to a neutral chip. */
const TYPE_BADGE_CLASSES: Record<string, string> = {
  bug: 'bg-error-soft text-error',
  feature: 'bg-link-bg-soft text-link',
  fix: 'bg-warning-soft text-warning-deep',
}

interface TicketCardProps {
  card: BoardCard
  /** The column the card currently sits in, carried as drag data so a drop can detect same-column no-ops. */
  fromState: string
}

/**
 * A single ticket card: title, type badge, and optional epic title. Draggable via `@dnd-kit` (drag data
 * carries the source column); a plain click (no drag — the 8px pointer activation distance separates the
 * two, D4/D6) opens the ticket's details view.
 */
export function TicketCard({ card, fromState }: TicketCardProps) {
  const navigate = useNavigate()
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: card.id,
    data: { fromState },
  })

  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined

  return (
    <article
      ref={setNodeRef}
      style={style}
      className={cn(
        'cursor-grab touch-none rounded-md border border-hairline bg-canvas p-3 shadow-card',
        isDragging && 'z-10 opacity-60',
      )}
      data-testid="ticket-card"
      onClick={() => void navigate(`/tickets/${card.id}`)}
      {...listeners}
      {...attributes}
    >
      <p className="mb-2 text-body-sm">{card.title}</p>
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={cn(
            'rounded-full px-2 py-px font-mono text-caption-mono',
            TYPE_BADGE_CLASSES[card.type] ?? 'bg-canvas-soft-2 text-body',
          )}
        >
          {ticketTypeLabel(card.type)}
        </span>
        {card.epicTitle ? <span className="text-caption text-mute">{card.epicTitle}</span> : null}
      </div>
    </article>
  )
}
