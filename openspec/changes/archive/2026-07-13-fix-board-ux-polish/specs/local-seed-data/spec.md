# local-seed-data — Delta

## MODIFIED Requirements

### Requirement: Local-only seed migration populates a usable dataset
The system SHALL provide a Liquibase seed changeset that populates the database with demo data: at least one verified user with a documented password, at least three teams, at least two epics per team, tickets for every team covering all five board states with a mix of ticket types (some tickets linked to epics, some not), and at least one comment on every seeded ticket. All seeded rows SHALL satisfy existing schema constraints (FKs, NOT NULL) using fixed UUIDs so the changeset is deterministic. Seeded timestamps SHALL be fixed (deterministic) but varied and internally consistent: rows SHALL NOT all share one identical instant; each row's `modified_at` SHALL be greater than or equal to its `created_at`; entities SHALL be created in a plausible order (user/teams before epics, epics before tickets); and every comment's `created_at` SHALL be at or after its ticket's `created_at`.

#### Scenario: Fresh local stack is usable immediately
- **WHEN** the stack starts locally via docker-compose against an empty database
- **THEN** the board shows multiple selectable teams, each board column contains at least one ticket, and every ticket detail view shows at least one comment

#### Scenario: Seeded user can log in
- **WHEN** the user signs in with the documented seed credentials on a locally seeded stack
- **THEN** authentication succeeds without email verification steps

#### Scenario: Seeded timestamps are varied and consistent
- **WHEN** the seed changeset runs against an empty local database
- **THEN** seeded tickets carry distinct creation timestamps (so board cards have a meaningful modified-first order), every row's `modified_at` is at or after its `created_at`, and every comment is dated at or after its ticket's creation
