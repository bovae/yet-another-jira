import { useDroppable } from '@dnd-kit/core'
import type { BoardColumn } from '@/api/board'
import { ticketStateLabel } from '@/api/tickets'
import { cn } from '@/lib/utils'
import { TicketCard } from './TicketCard'

interface ColumnProps {
  column: BoardColumn
}

/**
 * A single board column: a labelled heading (from the state code), a card count, and its cards in the
 * server-provided order. The whole column is a `@dnd-kit` drop target (D4), and its body scrolls so a
 * long column stays bounded without virtualization (D8).
 */
export function Column({ column }: ColumnProps) {
  const label = ticketStateLabel(column.state)
  const { setNodeRef, isOver } = useDroppable({ id: column.state })

  return (
    <section
      ref={setNodeRef}
      className={cn(
        'flex h-[calc(100vh-16rem)] flex-col rounded-md border border-hairline bg-canvas-soft-2 p-3',
        isOver && 'ring-2 ring-inset ring-link',
      )}
      data-testid="board-column"
      aria-label={label}
    >
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-body-sm-strong">{label}</h2>
        <span
          className="inline-flex min-w-5 items-center justify-center rounded-full border border-hairline bg-canvas px-1.5 text-body text-caption"
          aria-hidden="true"
        >
          {column.cards.length}
        </span>
      </header>
      <div className="flex flex-col gap-2 overflow-y-auto">
        {column.cards.map((card) => (
          <TicketCard key={card.id} card={card} fromState={column.state} />
        ))}
      </div>
    </section>
  )
}
