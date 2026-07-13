import type { ComponentPropsWithRef } from 'react'
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

interface TicketCardContentProps extends ComponentPropsWithRef<'article'> {
  card: BoardCard
}

/**
 * Presentational card visuals only — no drag or click wiring. Shared by the draggable `TicketCard`
 * (source card) and the board's `DragOverlay` (drag preview), so both look identical (D2). Extra props
 * (ref, drag listeners, onClick) are forwarded onto the article by whichever caller supplies them.
 */
export function TicketCardContent({ card, className, ...rest }: TicketCardContentProps) {
  return (
    <article
      className={cn(
        'cursor-grab touch-none rounded-md border border-hairline bg-canvas p-3 shadow-card',
        className,
      )}
      data-testid="ticket-card"
      {...rest}
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

interface TicketCardProps {
  card: BoardCard
  /** The column the card currently sits in, carried as drag data so a drop can detect same-column no-ops. */
  fromState: string
}

/**
 * A single draggable ticket card. Drag data carries the source column (for same-column no-op detection)
 * and the full card (so the board's `DragOverlay` can render a preview, D1/D2). No inline transform: the
 * overlay handles movement while dragging, and the source card just dims (`opacity-60`) as a placeholder.
 * A plain click (no drag — the 8px pointer activation distance separates the two, D4/D6) opens details.
 */
export function TicketCard({ card, fromState }: TicketCardProps) {
  const navigate = useNavigate()
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.id,
    data: { fromState, card },
  })

  return (
    <TicketCardContent
      ref={setNodeRef}
      card={card}
      className={cn(isDragging && 'opacity-60')}
      onClick={() => void navigate(`/tickets/${card.id}`)}
      {...listeners}
      {...attributes}
    />
  )
}
