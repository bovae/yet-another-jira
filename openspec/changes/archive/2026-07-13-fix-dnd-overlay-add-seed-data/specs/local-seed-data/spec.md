# local-seed-data

## ADDED Requirements

### Requirement: Local-only seed migration populates a usable dataset
The system SHALL provide a Liquibase seed changeset that populates the database with demo data: at least one verified user with a documented password, at least three teams, at least two epics per team, tickets for every team covering all five board states with a mix of ticket types (some tickets linked to epics, some not), and at least one comment on every seeded ticket. All seeded rows SHALL satisfy existing schema constraints (FKs, NOT NULL) using fixed UUIDs so the changeset is deterministic.

#### Scenario: Fresh local stack is usable immediately
- **WHEN** the stack starts locally via docker-compose against an empty database
- **THEN** the board shows multiple selectable teams, each board column contains at least one ticket, and every ticket detail view shows at least one comment

#### Scenario: Seeded user can log in
- **WHEN** the user signs in with the documented seed credentials on a locally seeded stack
- **THEN** authentication succeeds without email verification steps

### Requirement: Seed runs only in local environments
The seed changeset SHALL be gated by Liquibase context `local`, and the runtime context SHALL default to a non-local value so the seed is skipped unless explicitly enabled. Docker-compose SHALL be the only place that enables the `local` context. Schema changesets SHALL remain context-free and run in all environments.

#### Scenario: Non-local run skips the seed
- **WHEN** the backend starts without the local Liquibase context enabled (env var unset)
- **THEN** all schema migrations apply but no seed rows are inserted

#### Scenario: Seed applies once
- **WHEN** the backend restarts against an already-seeded local database
- **THEN** the seed changeset is not re-executed and no duplicate rows appear
