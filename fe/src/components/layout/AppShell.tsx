import { ChevronDown } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'

const NAV_LINKS = [
  { to: '/', label: 'Board', end: true },
  { to: '/teams', label: 'Teams', end: false },
  { to: '/epics', label: 'Epics', end: false },
  { to: '/tickets', label: 'Tickets', end: false },
]

/**
 * Layout for authenticated screens: a header with the app name, primary nav, and a collapsed user
 * menu, over an `<Outlet/>` for the active route. The user menu (shadcn DropdownMenu — the first
 * consumer of the token bridge) shows the current email and a Log out action.
 */
export function AppShell() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen">
      <header className="flex items-center gap-6 border-b border-hairline bg-canvas px-6 py-3">
        <Link to="/" className="text-body-md-strong">
          yet-another-jira
        </Link>
        <nav className="flex items-center gap-1">
          {NAV_LINKS.map(({ to, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'rounded-sm px-3 py-1.5 text-body-sm',
                  isActive ? 'bg-canvas-soft-2 text-ink' : 'text-body hover:text-ink',
                )
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto">
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="User menu"
              className="flex items-center gap-2 rounded-sm border border-hairline bg-canvas px-3 py-1.5 text-body-sm hover:bg-canvas-soft"
            >
              <span className="max-w-40 truncate">{user?.email}</span>
              <ChevronDown className="size-4 text-mute" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>{user?.email}</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => void logout()}>Log out</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>
      <main className="px-6 pt-8 pb-12">
        <Outlet />
      </main>
    </div>
  )
}
