import type { ReactNode } from 'react'

/**
 * Centered-card shell for the public auth screens (login, signup, verify). Sits on the canvas-soft
 * page body with a lifted white card; the screen supplies its title and body. Chrome mirrors the
 * app's other cards (canvas surface, hairline border, {@link shadow-card}) per DESIGN.md.
 */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-6 py-16">
      <section className="w-full max-w-sm rounded-lg border border-hairline bg-canvas p-8 shadow-card">
        <h1 className="mb-6 text-display-sm">{title}</h1>
        {children}
      </section>
    </main>
  )
}
