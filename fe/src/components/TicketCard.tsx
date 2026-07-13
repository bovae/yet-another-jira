import type { ComponentPropsWithRef } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { Link } from 'react-router'
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
  /**
   * When set, the title renders as a router link to this path — a focusable, Enter-activatable target
   * so the card is keyboard-openable (F-63). Omitted for the drag-overlay preview, which is inert.
   */
  titleHref?: string
}

/**
 * Presentational card visuals only — no drag wiring. Shared by the draggable `TicketCard` (source card)
 * and the board's `DragOverlay` (drag preview), so both look identical (D2). Extra props (ref, drag
 * listeners) are forwarded onto the article by whichever caller supplies them.
 */
export function TicketCardContent({ card, className, titleHref, ...rest }: TicketCardContentProps) {
  return (
    <article
      className={cn(
        'cursor-grab touch-none rounded-md border border-hairline bg-canvas p-3 shadow-card',
        className,
      )}
      data-testid="ticket-card"
      {...rest}
    >
      {titleHref ? (
        <Link
          to={titleHref}
          className="mb-2 block rounded-xs text-body-sm outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
        >
          {card.title}
        </Link>
      ) : (
        <p className="mb-2 text-body-sm">{card.title}</p>
      )}
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
 * Opening details is the title link (keyboard-openable); the 8px pointer activation distance keeps a
 * click on it from being read as a drag (D4/D6).
 */
export function TicketCard({ card, fromState }: TicketCardProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.id,
    data: { fromState, card },
  })

  return (
    <TicketCardContent
      ref={setNodeRef}
      card={card}
      titleHref={`/tickets/${card.id}`}
      className={cn(isDragging && 'opacity-60')}
      {...listeners}
      {...attributes}
    />
  )
}
