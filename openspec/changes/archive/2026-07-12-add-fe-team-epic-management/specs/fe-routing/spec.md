# fe-routing

## MODIFIED Requirements

### Requirement: Route table covers all minimum screens
The frontend SHALL define a client-side route for every minimum screen (§10): `/login`, `/signup`, `/verify`, `/verify-error`, `/` (Kanban board), `/teams`, `/epics`, and `/tickets/:id`. The public auth routes (`/login`, `/signup`, `/verify`, `/verify-error`) SHALL render the real auth screens. `/teams` and `/epics` SHALL render the real team and epic management screens. Routes whose screens belong to later epics (`/tickets/:id`) SHALL render a placeholder.

#### Scenario: Board route renders
- **WHEN** an authenticated user navigates to `/`
- **THEN** the board page renders inside the app shell

#### Scenario: Auth routes render real screens
- **WHEN** a visitor navigates to `/login`, `/signup`, `/verify`, or `/verify-error`
- **THEN** the corresponding auth screen renders (not a placeholder)

#### Scenario: Management routes render real screens
- **WHEN** an authenticated user navigates to `/teams` or `/epics`
- **THEN** the corresponding management screen renders inside the app shell (not a placeholder)

#### Scenario: Placeholder routes resolve
- **WHEN** an authenticated user navigates to `/tickets/:id`
- **THEN** a placeholder screen renders inside the app shell (no 404, no crash)

#### Scenario: Unknown path
- **WHEN** a user navigates to a path outside the route table
- **THEN** a not-found state renders
