# fe-routing

## Purpose
Frontend client-side routing: the route table covering all minimum screens (with placeholders for later-epic screens) and authentication guards on business routes.

## Requirements

### Requirement: Route table covers all minimum screens
The frontend SHALL define a client-side route for every minimum screen (§10): `/login`, `/signup`, `/verify`, `/` (Kanban board), `/teams`, `/epics`, and `/tickets/:id`. Routes whose screens belong to later epics SHALL render a placeholder.

#### Scenario: Board route renders
- **WHEN** an authenticated user navigates to `/`
- **THEN** the board page renders inside the app shell

#### Scenario: Placeholder routes resolve
- **WHEN** an authenticated user navigates to `/teams`, `/epics`, or `/tickets/:id`
- **THEN** a placeholder screen renders inside the app shell (no 404, no crash)

#### Scenario: Unknown path
- **WHEN** a user navigates to a path outside the route table
- **THEN** a not-found state renders

### Requirement: Business routes require authentication
The frontend SHALL guard all business routes (`/`, `/teams`, `/epics`, `/tickets/:id`) behind authentication. Unauthenticated visitors MUST be redirected to `/login`, preserving the originally requested location for post-login redirect.

#### Scenario: Unauthenticated redirect
- **WHEN** a visitor with no session navigates to `/teams`
- **THEN** they are redirected to `/login` and the intended location (`/teams`) is preserved in navigation state

#### Scenario: Authenticated pass-through
- **WHEN** a user with a valid session navigates to a business route
- **THEN** the route renders without redirect

#### Scenario: Public routes stay public
- **WHEN** a visitor with no session navigates to `/login`, `/signup`, or `/verify`
- **THEN** the route renders without redirect
