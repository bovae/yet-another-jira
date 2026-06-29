import type { BoardColumn } from '../api/board'
import { TicketCard } from './TicketCard'

interface ColumnProps {
  column: BoardColumn
}

/** A single board column: a labelled heading and its ticket cards in order. */
export function Column({ column }: ColumnProps) {
  return (
    <section
      className="flex min-h-30 flex-col rounded-md border border-hairline bg-canvas-soft-2 p-3"
      data-testid="board-column"
      aria-label={column.label}
    >
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-body-sm-strong">{column.label}</h2>
        <span
          className="inline-flex min-w-5 items-center justify-center rounded-full border border-hairline bg-canvas px-1.5 text-body text-caption"
          aria-hidden="true"
        >
          {column.cards.length}
        </span>
      </header>
      <div className="flex flex-col gap-2">
        {column.cards.map((card) => (
          <TicketCard key={card.id} card={card} />
        ))}
      </div>
    </section>
  )
}
