# Requirements Document

## Introduction

This document specifies the requirements for **Epic E0 — Backend domain
foundation** (`002-backend-domain-foundation`), the first backend epic for the
yet-another-jira Kanban ticket tracker. E0 builds directly on the runnable
skeleton delivered in `001-skeleton-bootstrap`, where the full domain schema is
already migrated via Liquibase with zero seed data, the cross-cutting concerns
are wired (correlation-id filter, RFC 9457 error handling, CORS, Valkey,
Actuator), security is a permit-all seam, and a mock board proves the
frontend-to-backend path.

E0 is the persistence and shared-foundation layer that every later epic builds
on. Its scope is JPA entities and Spring Data repositories for the **existing**
tables, plus shared building blocks: a typed domain exception hierarchy mapped to
RFC 9457 problem responses, the `TicketType` and `TicketState` enums with
parse/validate helpers, UTC timestamp handling that respects the database
defaults, and a current-user seam that later epics use to resolve the
authenticated user. E0 also establishes the project-wide patterns that keep the
persistence layer free of the Hibernate N+1 query problem — DTO projections for
reads, a no-EAGER fetch policy, and Open-Session-In-View disabled — and provides
a reusable test-side SQL-statement-count harness that later epics consume to
detect N+1 regressions automatically.

E0 introduces **no HTTP endpoints and no business CRUD**. Authentication logic,
team/epic/ticket/comment CRUD, delete guards, the `modified_at` update semantics,
and the board are explicitly later epics (E1–E10); E0 only provides the
persistence mappings and reference queries those epics will consume. The schema
is **locked**: entities map to the existing columns and the application must not
alter the schema.

These requirements are derived from the E0 entry in
`requirements/epics-catalog.md` and the product requirements source
`requirements/yet-another-jira.md` (primarily §6 ticket fields, §9 API and
persistence expectations, and §11 non-functional requirements).

## Glossary

- **Domain_Foundation**: The complete E0 deliverable taken as a whole: the JPA entities, the Spring Data repositories, the `TicketType` and `TicketState` enums, the domain exception hierarchy, and the current-user seam.
- **Domain_Model**: The JPA entity layer residing in the `com.bovae.yaj.domain.model` package.
- **Domain_Persistence_Layer**: The `Domain_Model` entities together with the Spring Data repositories operating against the PostgreSQL database.
- **User_Entity**: The JPA entity mapping the `users` table.
- **Verification_Token_Entity**: The JPA entity mapping the `verification_tokens` table.
- **Team_Entity**: The JPA entity mapping the `teams` table.
- **Epic_Entity**: The JPA entity mapping the `epics` table.
- **Ticket_Entity**: The JPA entity mapping the `tickets` table.
- **Comment_Entity**: The JPA entity mapping the `comments` table.
- **User_Repository**: The Spring Data repository for the `User_Entity`.
- **Verification_Token_Repository**: The Spring Data repository for the `Verification_Token_Entity`.
- **Team_Repository**: The Spring Data repository for the `Team_Entity`.
- **Epic_Repository**: The Spring Data repository for the `Epic_Entity`.
- **Ticket_Repository**: The Spring Data repository for the `Ticket_Entity`.
- **Comment_Repository**: The Spring Data repository for the `Comment_Entity`.
- **TicketType**: The Java enum representing the ticket classification codes `bug`, `feature`, and `fix`, corresponding to the `ticket_types` lookup table.
- **TicketState**: The Java enum representing the workflow state codes `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, and `done`, corresponding to the `ticket_states` lookup table.
- **Ticket_Type_Code**: A string value equal to one of the `ticket_types.code` values: `bug`, `feature`, `fix`.
- **Ticket_State_Code**: A string value equal to one of the `ticket_states.code` values: `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`.
- **Domain_Error_Model**: The domain exception hierarchy (`NotFoundException`, `ConflictException`, `ValidationException`, `UnauthorizedException`) residing in the `com.bovae.yaj.error` package.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalProblemHandler` (`@RestControllerAdvice`) that produces RFC 9457 problem responses.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and a UTC `timestamp`.
- **Current_User_Provider**: The current-user seam: an abstraction that exposes the authenticated user identifier for later epics to consume.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/model/**`, `**/config/**`, `**/mapper/*Impl*`, and `**/*Application.*`.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance provisioned by Testcontainers under the `bdd` profile.
- **N_Plus_One**: The Hibernate query anti-pattern in which retrieving a collection of N rows triggers one initial query plus one additional per-row query (N follow-up queries), so that the executed statement count grows with the number of rows returned.
- **DTO_Projection**: A read-model data-transfer object assembled within the query and transaction boundary (for example via a JPQL constructor expression or a Spring Data projection) whose required fields are fully populated before the transaction closes, rather than a managed entity navigated lazily after the transaction closes.
- **SQL_Statement_Count**: The number of SQL statements the `Domain_Persistence_Layer` executes while servicing a single read or operation.
- **SQL_Statement_Count_Assertion**: A test-side assertion over the `SQL_Statement_Count` for an operation, used to detect `N_Plus_One` regressions, including the data-independent assertion that the count does not grow with the number of seeded rows.
- **Open_Session_In_View**: The Spring Boot setting `spring.jpa.open-in-view` that, when enabled, keeps the persistence session open during web-request rendering; the `Domain_Foundation` runs with this setting disabled (`false`).

## Requirements

### Requirement 1: Domain entity package and schema fidelity

**User Story:** As a backend developer, I want JPA entities that map the existing tables exactly, so that later epics persist and read domain data without altering the locked schema. (§9)

#### Acceptance Criteria

1. THE Domain_Model SHALL define the JPA entity classes within the `com.bovae.yaj.domain.model` package.
2. THE Domain_Model SHALL map exactly one entity to each of the existing tables `users`, `verification_tokens`, `teams`, `epics`, `tickets`, and `comments`.
3. THE Domain_Model SHALL represent every entity primary key as a `java.util.UUID`.
4. THE Domain_Model SHALL map each entity field to the existing column of the same semantic name without declaring additional, renamed, or removed columns.
5. WHERE Hibernate schema management is configurable, THE Domain_Persistence_Layer SHALL run in schema-validation mode so that application startup applies no changes to the database schema.
6. IF any mapped entity field does not match its existing column definition, THEN THE Domain_Persistence_Layer SHALL fail application startup and emit a schema-validation error identifying the mismatch.

### Requirement 2: User entity mapping

**User Story:** As a backend developer, I want a `User_Entity` that maps the `users` table, so that authentication and ownership features in later epics can persist and read user records. (§3, §6)

#### Acceptance Criteria

1. THE User_Entity SHALL map the `id`, `email`, `password_hash`, `email_verified`, `deleted_at`, `created_at`, and `modified_at` columns of the `users` table to entity fields.
2. THE User_Entity SHALL represent the `email` column value as a `String` field.
3. THE User_Entity SHALL represent the `email_verified` column value as a primitive `boolean` field.
4. THE User_Entity SHALL represent the nullable `deleted_at` column as a nullable `java.time.Instant` field.
5. THE User_Entity SHALL exclude the `password_hash` value from its `toString` representation so that the password hash is kept out of logs.

### Requirement 3: Verification token entity mapping

**User Story:** As a backend developer, I want a `Verification_Token_Entity` that maps the `verification_tokens` table, so that the email-verification and resend flows in later epics can issue and consume tokens. (§3)

#### Acceptance Criteria

1. THE Verification_Token_Entity SHALL map the `id`, `user_id`, `token_hash`, `purpose`, `expires_at`, `consumed_at`, and `created_at` columns of the `verification_tokens` table to entity fields.
2. THE Verification_Token_Entity SHALL represent the nullable `consumed_at` column as a nullable `java.time.Instant` field.
3. THE Verification_Token_Entity SHALL map the `user_id` column as a foreign-key reference to the `users` table.
4. THE Verification_Token_Entity SHALL exclude the `token_hash` value from its `toString` representation so that the token hash is kept out of logs.

### Requirement 4: Team entity mapping

**User Story:** As a backend developer, I want a `Team_Entity` that maps the `teams` table, so that team management in later epics can persist and read teams. (§4)

#### Acceptance Criteria

1. THE Team_Entity SHALL map the `id`, `name`, `created_at`, and `modified_at` columns of the `teams` table to entity fields.
2. THE Team_Entity SHALL represent the `name` column value as a `String` field.

### Requirement 5: Epic entity mapping

**User Story:** As a backend developer, I want an `Epic_Entity` that maps the `epics` table, so that epic management in later epics can persist and read epics scoped to a team. (§5)

#### Acceptance Criteria

1. THE Epic_Entity SHALL map the `id`, `team_id`, `title`, `description`, `created_at`, and `modified_at` columns of the `epics` table to entity fields.
2. THE Epic_Entity SHALL map the `team_id` column as a foreign-key reference to the `teams` table.
3. THE Epic_Entity SHALL represent the nullable `description` column as a nullable `String` field.

### Requirement 6: Ticket entity mapping

**User Story:** As a backend developer, I want a `Ticket_Entity` that maps the `tickets` table and stores the type and state as lookup codes, so that ticket features in later epics persist tickets while the schema stays locked. (§6, §9)

#### Acceptance Criteria

1. THE Ticket_Entity SHALL map the `id`, `team_id`, `epic_id`, `type`, `state`, `title`, `body`, `created_by`, `created_at`, and `modified_at` columns of the `tickets` table to entity fields.
2. THE Ticket_Entity SHALL persist the `type` column value as a `Ticket_Type_Code` string equal to a `ticket_types.code` value.
3. THE Ticket_Entity SHALL persist the `state` column value as a `Ticket_State_Code` string equal to a `ticket_states.code` value.
4. THE Ticket_Entity SHALL represent the nullable `epic_id` column as a nullable foreign-key reference to the `epics` table.
5. THE Ticket_Entity SHALL map the `team_id` column as a foreign-key reference to the `teams` table.
6. THE Ticket_Entity SHALL map the `created_by` column as a foreign-key reference to the `users` table.

### Requirement 7: Comment entity mapping

**User Story:** As a backend developer, I want a `Comment_Entity` that maps the `comments` table, so that the comment feature in a later epic can persist and read comments. (§7)

#### Acceptance Criteria

1. THE Comment_Entity SHALL map the `id`, `ticket_id`, `author_id`, `body`, and `created_at` columns of the `comments` table to entity fields.
2. THE Comment_Entity SHALL map the `ticket_id` column as a foreign-key reference to the `tickets` table.
3. THE Comment_Entity SHALL map the `author_id` column as a foreign-key reference to the `users` table.

### Requirement 8: Timestamp and auditing handling

**User Story:** As a backend developer, I want consistent UTC timestamp handling that respects the database defaults, so that audit fields are correct without double-managing values the database already sets. (§6, §9)

#### Acceptance Criteria

1. THE Domain_Model SHALL represent every `created_at` and `modified_at` column as a `java.time.Instant` field interpreted in UTC.
2. WHERE a timestamp column defines a database default, THE Domain_Model SHALL obtain that column's value from the database-generated default rather than from an application-supplied value on insert.
3. WHEN an entity backed by database-defaulted timestamp columns is persisted, THE Domain_Persistence_Layer SHALL expose non-null `created_at` and `modified_at` values for that entity after the insert completes.
4. WHERE an entity field is not backed by a database default, THE Domain_Model SHALL assign that field a UTC `Instant` within a `@PrePersist` or `@PreUpdate` lifecycle callback.
5. THE Ticket_Entity SHALL rely on the database default for the initial `modified_at` value.
6. THE Ticket_Entity SHALL apply no entity lifecycle callback that mutates `modified_at`, so that the ticket service introduced in epic E7 owns `modified_at` updates and a save of unchanged values does not advance it (§6).

### Requirement 9: TicketType enum with parse and validate helpers

**User Story:** As a service-layer developer, I want a `TicketType` enum with parse and validate helpers, so that ticket type codes convert safely between the persistence string and a typed value. (§6)

#### Acceptance Criteria

1. THE TicketType SHALL define exactly the enum constants corresponding to the codes `bug`, `feature`, and `fix`.
2. THE TicketType SHALL expose the canonical `Ticket_Type_Code` string for each enum constant.
3. WHEN a known `Ticket_Type_Code` is supplied to the `TicketType` parse operation, THE TicketType SHALL return the specific enum constant whose code equals the supplied code.
4. WHEN a `TicketType` constant is converted to its code and that code is parsed by the `TicketType` parse operation, THE TicketType SHALL return the original constant.
5. IF an unknown code is supplied to the `TicketType` parse operation, THEN THE TicketType SHALL raise a `ValidationException` identifying the rejected code.
6. IF a null or blank code is supplied to the `TicketType` parse operation, THEN THE TicketType SHALL raise a `ValidationException` indicating that a ticket type code is required.

### Requirement 10: TicketState enum with parse, validate, and ordering

**User Story:** As a service-layer developer, I want a `TicketState` enum that carries the workflow position and offers parse and validate helpers, so that state codes convert safely and the fixed workflow order is preserved. (§6, §8)

#### Acceptance Criteria

1. THE TicketState SHALL define exactly the enum constants corresponding to the codes `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, and `done`.
2. THE TicketState SHALL expose the canonical `Ticket_State_Code` string for each enum constant matching the corresponding `ticket_states.code` value.
3. THE TicketState SHALL expose for each enum constant a position equal to the corresponding `ticket_states.position` value, where `new` is 1, `ready_for_implementation` is 2, `in_progress` is 3, `ready_for_acceptance` is 4, and `done` is 5.
4. THE TicketState SHALL order its enum constants by ascending position from `new` (1) through `done` (5).
5. WHEN a known `Ticket_State_Code` is supplied to the `TicketState` parse operation, THE TicketState SHALL return the specific enum constant whose code equals the supplied code.
6. WHEN a `TicketState` constant is converted to its code and that code is parsed by the `TicketState` parse operation, THE TicketState SHALL return the original constant.
7. IF any unknown code is supplied to the `TicketState` parse operation, THEN THE TicketState SHALL raise a `ValidationException` identifying the rejected code.
8. IF a null or blank code is supplied to the `TicketState` parse operation, THEN THE TicketState SHALL raise a `ValidationException` indicating that a ticket state code is required.

### Requirement 11: Spring Data repositories and resolution

**User Story:** As a backend developer, I want a Spring Data repository for each entity, so that later epics read and write domain data through a consistent persistence API. (§9)

#### Acceptance Criteria

1. THE Domain_Persistence_Layer SHALL define the repositories `User_Repository`, `Verification_Token_Repository`, `Team_Repository`, `Epic_Repository`, `Ticket_Repository`, and `Comment_Repository`.
2. THE Domain_Persistence_Layer SHALL declare the repository interfaces in the `com.bovae.yaj.domain.repository` package.
3. THE Domain_Persistence_Layer SHALL provide create, read, update, and delete operations for each mapped entity through Spring Data.
4. WHEN the Spring application context starts, THE Domain_Persistence_Layer SHALL resolve every repository bean.
5. IF any repository bean fails to resolve at startup, THEN THE Domain_Persistence_Layer SHALL fail the entire application-context startup rather than continuing with partial functionality.

### Requirement 12: Reference queries for later delete guards

**User Story:** As a developer building the later delete guards, I want existence and count queries that detect referencing rows, so that epics E5–E8 can reject deletes that would violate referential integrity and return HTTP 409. (§4, §5, §9)

#### Acceptance Criteria

1. THE Epic_Repository SHALL provide a query that reports whether any epic references a given team identifier.
2. THE Ticket_Repository SHALL provide a query that reports whether any ticket references a given team identifier.
3. THE Ticket_Repository SHALL provide a query that reports whether any ticket references a given epic identifier.
4. THE Comment_Repository SHALL provide a query that reports whether any comment references a given ticket identifier.
5. THE Comment_Repository SHALL provide a query that returns the count of comments referencing a given ticket identifier.
6. WHEN a reference query is invoked for an identifier that no row references, THE Domain_Persistence_Layer SHALL report absence as a `false` existence result or a zero count.

### Requirement 13: Lookup helper queries

**User Story:** As a developer building the auth and verification flows, I want lookup queries for users by email and verification tokens by hash, so that epics E1–E3 can resolve records using the existing case-insensitive `citext` and the stored token hash. (§3)

#### Acceptance Criteria

1. WHEN the `User_Repository` email lookup is invoked with an email value, THE User_Repository SHALL return the matching user compared case-insensitively, consistent with the `citext` column.
2. IF no user matches the supplied email, THEN THE User_Repository SHALL return an empty result.
3. WHEN the `Verification_Token_Repository` lookup is invoked with a token-hash value, THE Verification_Token_Repository SHALL return the verification token whose `token_hash` equals the supplied value.
4. IF no verification token matches the supplied token-hash value, THEN THE Verification_Token_Repository SHALL return an empty result.

### Requirement 14: Domain exception hierarchy

**User Story:** As a service-layer developer, I want a small set of typed domain exceptions, so that business code in later epics signals failure categories that map to the correct HTTP statuses. (§9)

#### Acceptance Criteria

1. THE Domain_Error_Model SHALL define the exception types `NotFoundException`, `ConflictException`, `ValidationException`, and `UnauthorizedException`.
2. THE Domain_Error_Model SHALL declare the domain exception types in the `com.bovae.yaj.error` package.
3. THE Domain_Error_Model SHALL define each domain exception as an unchecked exception extending `RuntimeException`.
4. THE Domain_Error_Model SHALL allow each domain exception to carry a human-readable detail message.

### Requirement 15: Domain exception to RFC 9457 problem mapping

**User Story:** As an API consumer, I want domain exceptions mapped to RFC 9457 problem responses with the correct status codes, so that errors stay consistent and machine-readable across the application. (§9, §11)

#### Acceptance Criteria

1. WHEN a `NotFoundException` reaches the `Problem_Handler`, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 404.
2. WHEN a `ConflictException` reaches the `Problem_Handler`, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 409.
3. WHEN a `ValidationException` reaches the `Problem_Handler`, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 400.
4. WHEN an `UnauthorizedException` reaches the `Problem_Handler`, THE Problem_Handler SHALL produce an RFC 9457 problem response with HTTP status 401.
5. WHEN the `Problem_Handler` maps any domain exception, THE Problem_Handler SHALL build the response through the existing `Problem_Detail_Factory` so that the `correlationId` and UTC `timestamp` members are present.
6. WHERE a mapped domain exception carries a detail message, THE Problem_Handler SHALL set the problem `detail` member to that message.
7. WHEN the `Problem_Handler` maps a domain exception, THE Problem_Handler SHALL exclude stack traces, internal type names, and SQL statements from every member of the response body.

### Requirement 16: Current-user seam

**User Story:** As a developer of later epics, I want a current-user accessor abstraction, so that ticket and comment creation can resolve the authenticated user identifier once real authentication lands in E4. (§3, §6)

#### Acceptance Criteria

1. THE Domain_Foundation SHALL define a `Current_User_Provider` abstraction that exposes an operation returning the authenticated user identifier as a `java.util.UUID`.
2. WHEN the `Current_User_Provider` resolves an authenticated user, THE Current_User_Provider SHALL return that user's `UUID`.
3. WHEN the `Current_User_Provider` is asked for the current user identifier and no authenticated user is present, THE Current_User_Provider SHALL raise an `UnauthorizedException`, including the case where authentication was never attempted.
4. THE Domain_Foundation SHALL defer the JWT-based implementation of the `Current_User_Provider` to epic E4, providing only the seam in E0.

### Requirement 17: Persistence and cross-cutting constraints

**User Story:** As a system owner, I want the domain foundation to honor the system-wide persistence rules, so that PostgreSQL remains the single source of truth and a fresh database stays clean. (§9, §11)

#### Acceptance Criteria

1. THE Domain_Foundation SHALL persist all domain entities exclusively through the PostgreSQL-backed repositories, treating PostgreSQL as the system of record.
2. THE Domain_Persistence_Layer SHALL preserve referential integrity by relying on the existing database foreign-key constraints together with the reference queries defined in Requirement 12.
3. THE Domain_Foundation SHALL introduce no application seed data or sample data into the database.
4. THE Domain_Foundation SHALL keep all source-controlled files free of plaintext secrets, including database credentials and connection strings.
5. THE Domain_Model SHALL represent timestamp fields as `java.time.Instant` values so that later API layers serialize them as ISO-8601 UTC instants.

### Requirement 18: Verification and quality gates

**User Story:** As a maintainer, I want the foundation verified against the project's quality gates, so that the persistence layer is proven before later epics build on it. (§11; E0 Definition of Done)

#### Acceptance Criteria

1. WHEN the integration test suite runs against the `Integration_Test_Context`, THE Domain_Persistence_Layer SHALL load every entity and resolve every repository without a schema-validation error.
2. THE Project_Build SHALL satisfy the `Coverage_Gate` thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages.
3. WHERE a class resides in a package excluded by the `Coverage_Gate`, THE Coverage_Gate SHALL omit that class from coverage measurement, including the `Domain_Model` entities under `**/model/**`.
4. THE Project_Build SHALL include unit tests covering the `TicketType` and `TicketState` parse and validate logic, including the unknown-code and null-or-blank-code paths.
5. THE Project_Build SHALL include unit tests covering the mapping of each domain exception to its RFC 9457 problem response status.
6. THE Project_Build SHALL cover the primary persistence flow with a Cucumber BDD scenario executed under the `bdd` profile against a Testcontainers PostgreSQL instance.
7. THE Project_Build SHALL omit direct tests of Lombok-generated accessors, builders, and `toString` methods.
8. WHEN the Spotless and the Error Prone with NullAway checks run, THE Project_Build SHALL complete without formatting or static-analysis violations.
9. IF the Spotless check or the Error Prone with NullAway check detects a violation, THEN THE Project_Build SHALL fail immediately and identify the violating file and location.
10. THE Project_Build SHALL include a `SQL_Statement_Count_Assertion` test that exercises an existence, count, or lookup reference query from Requirement 12 or Requirement 13 and asserts the query completes in a single SQL statement with no per-row lazy loading.
11. WHEN a covered read is executed against the `Integration_Test_Context`, THE Project_Build SHALL verify through the reusable `SQL_Statement_Count_Assertion` harness that the executed `SQL_Statement_Count` does not increase when additional rows are seeded.

### Requirement 19: N+1 query avoidance and detection (project-wide mandatory)

**User Story:** As a system owner, I want the persistence layer to avoid the Hibernate N+1 query problem and to ship a reusable detection harness, so that read paths stay efficient at scale and `N_Plus_One` regressions are caught automatically as every later epic adds real reads. (§8, §9, §11)

This is a mandatory, project-wide constraint. E0 is the persistence foundation, so it establishes the N+1-safe patterns and configuration, provides the reusable test-side detection harness that later epics reuse, and ensures E0's own persistence code complies. The criteria below are outcome-based: the concrete entity-mapping mechanism and the concrete harness tool are deferred to `design.md` (see the design notes).

#### Acceptance Criteria

1. WHEN a read path returns data to its caller, THE Domain_Persistence_Layer SHALL return a `DTO_Projection` assembled within the query and transaction boundary, so that every required field of the returned `DTO_Projection` is resolved before the transaction closes.
2. WHERE a read result requires data from an associated row, THE Domain_Persistence_Layer SHALL include that associated data in the `DTO_Projection` produced within the transaction rather than relying on lazy navigation of a managed entity after the transaction closes.
3. WHEN a read returns a collection of results, THE Domain_Persistence_Layer SHALL execute a `SQL_Statement_Count` that is bounded by a constant and independent of the number of rows returned.
4. WHEN a collection-returning read requires associated data, THE Domain_Persistence_Layer SHALL fetch that associated data within the same query or through a bounded number of batch queries.
5. THE Domain_Foundation SHALL run the application with `Open_Session_In_View` disabled by setting `spring.jpa.open-in-view` to `false`.
6. IF code accesses an unfetched lazy association outside the transaction boundary, THEN THE Domain_Persistence_Layer SHALL fail fast by raising a `LazyInitializationException` rather than issuing an additional SQL statement.
7. WHERE an entity association is mapped as a JPA relationship, THE Domain_Model SHALL configure that relationship with lazy fetching and SHALL declare no association with EAGER fetching.
8. THE Domain_Foundation SHALL provide a reusable test harness that asserts the `SQL_Statement_Count` executed during a unit, slice, or BDD test.
9. WHERE the `SQL_Statement_Count_Assertion` harness is implemented, THE Domain_Foundation SHALL by default use a mechanism that introduces no new runtime dependency, such as enabling `hibernate.generate_statistics` in the test profile and asserting the executed-statement count.
10. WHERE a test covers a collection-returning read, THE Project_Build SHALL include a `SQL_Statement_Count_Assertion` verifying that the `SQL_Statement_Count` does not increase when additional rows are seeded.
11. WHEN an existence, count, or lookup reference query defined in Requirement 12 or Requirement 13 executes, THE Domain_Persistence_Layer SHALL complete that query in a single SQL statement without triggering per-row lazy loading.

**Design note (criterion 7 — association fetch form):** Requirements 3, 5, 6, and 7 require each cross-table reference to be mapped "as a foreign-key reference." This requirement constrains how that reference behaves at fetch time (no EAGER fetching; no per-row lazy loading on collection reads) but does not fix the mapping form. Whether a reference is a `LAZY @ManyToOne` association or a raw `java.util.UUID` foreign-key field is an explicitly-flagged `design.md` decision.

**Design note (criteria 8–9 — harness tool):** The specific detection tool (Hibernate's built-in statistics via `hibernate.generate_statistics` versus a third-party SQL-statement-count validator, datasource-proxy, or QuickPerf) is a `design.md` choice. The no-new-dependency default is available because the `be` module already has `spring-boot-starter-data-jpa` (Hibernate) on the classpath (see `be/pom.xml`).

**Scope note (criterion 11 — E0 boundary):** E0 itself introduces no collection-returning business reads (no endpoints or CRUD yet), so the data-independent query-count assertions over the real board and list reads land in their own epics (for example E9 board read). E0's responsibility is to establish the configuration (`Open_Session_In_View` off), the fetch policy (no EAGER), the DTO-projection-for-reads rule, and the reusable `SQL_Statement_Count_Assertion` harness those later epics consume, and to keep its own existence, count, and lookup reference queries (Requirements 12 and 13) single-statement and free of per-row lazy loading.
