# fe-design-primitives

## ADDED Requirements

### Requirement: shadcn/ui primitives styled by DESIGN.md tokens
The frontend SHALL integrate shadcn/ui as the source of accessible interactive primitives. shadcn's semantic CSS variables (`--background`, `--foreground`, `--primary`, `--border`, `--ring`, `--radius`, and related) MUST be bridged onto the `DESIGN.md` tokens defined in `fe/src/index.css`, so shadcn components render with the project design language despite the stock Tailwind palette reset.

#### Scenario: Bridged primitive renders styled
- **WHEN** a shadcn primitive (e.g. DropdownMenu) is rendered
- **THEN** its surfaces, text, borders, and focus ring use `DESIGN.md` token values, not browser defaults or invisible/unstyled output

### Requirement: App shell with header user menu
Authenticated screens SHALL render inside an app shell with a header containing navigation and a collapsed user menu built on the shadcn DropdownMenu primitive. The menu SHALL show the current user's email and a **Log out** action wired to the session logout.

#### Scenario: User menu opens
- **WHEN** an authenticated user activates the header user menu
- **THEN** a dropdown opens showing the user's email and a Log out item

#### Scenario: Log out fires
- **WHEN** the user selects Log out from the menu
- **THEN** the logout action runs and the user lands on `/login`

### Requirement: Reusable async-state components
The frontend SHALL provide reusable loading, empty, and error state components (error with message and optional retry), built from `DESIGN.md` token utilities, for use by all screens (§11 usability).

#### Scenario: Loading state
- **WHEN** a screen is fetching data
- **THEN** the shared loading component renders

#### Scenario: Error state with retry
- **WHEN** a fetch fails and a retry handler is provided
- **THEN** the shared error component renders the message and a retry control that re-triggers the fetch

#### Scenario: Empty state
- **WHEN** a fetch succeeds with no items
- **THEN** the shared empty component renders a descriptive message
