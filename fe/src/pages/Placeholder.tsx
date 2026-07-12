import { Link } from 'react-router'

/**
 * Stand-in for screens owned by later epics (auth forms E12, team/epic/ticket screens E13–E15).
 * Renders a titled panel so the route resolves visibly instead of 404-ing.
 */
export function Placeholder({ title }: { title: string }) {
  return (
    <section className="rounded-md border border-hairline bg-canvas p-6">
      <h1 className="mb-1 text-display-sm">{title}</h1>
      <p className="text-body text-body-sm">This screen is coming in a later milestone.</p>
    </section>
  )
}

/** Catch-all for paths outside the route table. */
export function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-3 px-6 text-center">
      <p className="font-mono text-caption-mono text-mute">404</p>
      <h1 className="text-display-md">Page not found</h1>
      <Link to="/" className="text-link text-body-sm hover:text-link-deep">
        Back to the board
      </Link>
    </main>
  )
}
