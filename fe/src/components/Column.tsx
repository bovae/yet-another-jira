import type { BoardColumn } from '../api/board'
import { TicketCard } from './TicketCard'

interface ColumnProps {
  column: BoardColumn
}

/** A single board column: a labelled heading and its ticket cards in order. */
export function Column({ column }: ColumnProps) {
  return (
    <section className="board-column" data-testid="board-column" aria-label={column.label}>
      <header className="board-column__header">
        <h2 className="board-column__title">{column.label}</h2>
        <span className="board-column__count" aria-hidden="true">
          {column.cards.length}
        </span>
      </header>
      <div className="board-column__cards">
        {column.cards.map((card) => (
          <TicketCard key={card.id} card={card} />
        ))}
      </div>
    </section>
  )
}
