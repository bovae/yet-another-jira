# Requirements Document

## Introduction

This document specifies the requirements for the **yet-another-jira application
skeleton** (`001-skeleton-bootstrap`). The skeleton is the thinnest runnable
scaffold of a three-tier Kanban ticket tracker: a React SPA (presentation), a
Spring Boot HTTP API (application), and a PostgreSQL RDBMS (persistence), with
Valkey as a supporting ephemeral store.

The skeleton proves the wiring, not the features. It must boot the full topology
end-to-end from a clean checkout with a single command, establish cross-cutting
concerns (correlation-id propagation, MDC hygiene, RFC 9457 error contract,
permit-all security with configurable CORS), run migrations that create the
**full target domain schema with zero seed data**, and serve a mock board
endpoint the frontend renders to prove the request path. All quality gates,
CI jobs, and release tooling must be present and green from day one so future
feature work (authentication, teams, epics, tickets, comments, draggable board)
inherits a healthy pipeline.

These requirements are derived from the approved design document
(`design.md`) and the original product requirements source
(`requirements/yet-another-jira.md`). Business CRUD behavior, real
authentication, SMTP, and drag-and-drop persistence are **explicitly out of
scope for the skeleton** and are documented as seams only.

## Glossary

- **System**: The complete yet-another-jira skeleton solution (frontend, backend, database, supporting services, and tooling) taken as a whole.
- **Backend**: The Spring Boot 3.5 / Java 21 HTTP API application served from the `be` module.
- **Frontend**: The React 19 / TypeScript single-page application served as static assets by nginx from the `fe` module.
- **Database**: The PostgreSQL relational database that holds all persistent application data.
- **Valkey_Store**: The Valkey instance used as a supporting ephemeral store (future token denylist and rate limiting), connected via the Redis protocol.
- **Migration_Runner**: The Liquibase component that applies database changelogs on backend startup.
- **Correlation_Filter**: The `CorrelationIdFilter` servlet filter that establishes the per-request correlation identifier.
- **Mdc_Cleanup_Filter**: The outermost `MdcCleanupFilter` servlet filter responsible for clearing the request-scoped MDC.
- **Problem_Handler**: The `GlobalProblemHandler` / `ProblemDetailFactory` components that produce RFC 9457 problem responses.
- **Security_Config**: The Spring Security `SecurityConfig` that applies the permit-all policy and CORS for the skeleton.
- **Mock_Board_Endpoint**: The `GET /api/v1/mock/board` endpoint returning hardcoded sample board data.
- **Board_Page**: The frontend page that fetches and renders the mock board.
- **Build_Tooling**: The Maven build configuration, including profiles that keep the shipped jar thin.
- **Quality_Gates**: The combined static-analysis, formatting, and coverage checks (Spotless, Error Prone + NullAway, JaCoCo, ESLint, Prettier).
- **CI_Pipeline**: The GitHub Actions workflows that build, lint, and test both modules.
- **Release_Tooling**: semantic-release, Dependabot, and pre-commit configurations.
- **Correlation_Id**: The value carried in the `X-Correlation-Id` HTTP header and the `correlationId` MDC key.
- **Canonical_Ticket_State**: One of the five ticket states in workflow order: `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`.
- **Application_Table**: Any domain table holding application data: `users`, `teams`, `epics`, `tickets`, `comments`, `verification_tokens`.

## Requirements

### Requirement 1: One-command end-to-end boot

**User Story:** As a QA engineer, I want to start the whole solution with a single command from a clean checkout, so that I can run the application on any clean laptop without installing language runtimes.

#### Acceptance Criteria

1. WHEN a developer runs `docker compose up --build` from the repository root on a clean checkout with no pre-existing build cache, THE System SHALL start the Database, Valkey_Store, Backend, and Frontend containers and SHALL bring each container to a running state.
2. THE System SHALL require no host-installed language runtime, package manager, or build tool to run the solution, and SHALL require only Docker and Docker Compose on the host.
3. WHEN the Frontend container has started, THE Frontend SHALL respond to an HTTP GET on its published host port with status 200 and SHALL serve the SPA entry document.
4. WHILE the Database or Valkey_Store has not yet passed its container health check, THE System SHALL block Backend startup.
5. WHILE the Backend has not yet passed its container health check, THE System SHALL block Frontend startup.
6. IF a dependency does not report a healthy state within 120 seconds, THEN THE System SHALL abort startup of the dependent container and SHALL report the unhealthy dependency in the compose output.
7. THE System SHALL exclude email-related services from the default boot.
8. WHERE the opt-in email compose profile is selected, THE System SHALL start the email-related services.

### Requirement 2: Three-tier separation and relational persistence

**User Story:** As a system architect, I want clear separation between presentation, application, and persistence tiers, so that the solution is maintainable and matches the mandated architecture.

#### Acceptance Criteria

1. THE System SHALL separate presentation (Frontend), application (Backend), and persistence (Database) into distinct logical tiers, WHERE the Frontend communicates only with the Backend and the Backend is the sole accessor of the Database.
2. THE Backend SHALL expose its capabilities exclusively through an HTTP API under the `/api/v1` path.
3. IF a request targets a Backend path outside `/api/v1`, THEN THE Backend SHALL reject the request with a client error response and SHALL NOT execute any application capability.
4. THE System SHALL use a dedicated server-based PostgreSQL container as the relational store for all persistent application data.
5. IF the PostgreSQL Database is unreachable, THEN THE Backend SHALL return a server error response and SHALL NOT treat Valkey_Store as the authoritative data source.
6. THE System SHALL treat Valkey_Store as a supporting ephemeral store whose contents may be evicted or expire at any time, and SHALL NOT use Valkey_Store as the system of record.
7. THE Frontend SHALL retrieve its rendered data from the Backend and SHALL NOT use browser local storage, session storage, or cookies as the system of record.

### Requirement 3: Correlation-id propagation

**User Story:** As an operator, I want every request and response correlated by a stable identifier, so that I can trace a request across logs and the client.

#### Acceptance Criteria

1. WHEN a request arrives carrying an `X-Correlation-Id` header whose trimmed value is non-blank and between 1 and 128 characters in length, THE Correlation_Filter SHALL use the supplied value as the Correlation_Id for that request.
2. IF a request arrives with an absent or blank `X-Correlation-Id` header, THEN THE Correlation_Filter SHALL generate a new UUID as the Correlation_Id for that request.
3. IF a request arrives with an `X-Correlation-Id` header whose trimmed value exceeds 128 characters, THEN THE Correlation_Filter SHALL discard the supplied value and generate a new UUID as the Correlation_Id for that request.
4. WHEN a request is processed, THE Correlation_Filter SHALL place the Correlation_Id into the MDC under the `correlationId` key before downstream handling.
5. WHEN processing of a request completes, whether it succeeds or fails, THE Correlation_Filter SHALL remove the `correlationId` key from the MDC before the handling thread is released.
6. WHEN the Backend produces any HTTP response, THE Backend SHALL include the resolved Correlation_Id in the `X-Correlation-Id` response header, including on error responses.
7. THE Backend SHALL include the Correlation_Id in every log line emitted during processing of the request.

### Requirement 4: MDC hygiene at the request boundary

**User Story:** As an operator, I want per-request logging context cleared after each request, so that context from one request never leaks into another on a pooled thread.

#### Acceptance Criteria

1. WHEN a request completes successfully, THE Mdc_Cleanup_Filter SHALL remove every MDC key from the request thread so that the thread's MDC context map contains zero keys before the thread returns to the pool.
2. IF a request terminates with an exception, THEN THE Mdc_Cleanup_Filter SHALL remove every MDC key from the request thread so that the thread's MDC context map contains zero keys before the thread returns to the pool, regardless of the exception type.
3. THE Mdc_Cleanup_Filter SHALL execute at the highest filter precedence (ordinal value indicating first-in, last-out position) so that its MDC clearing step runs after all downstream filters and request handlers have returned.
4. THE Mdc_Cleanup_Filter SHALL remove all MDC keys present on the request thread irrespective of which producer populated them and irrespective of the key names.
5. WHEN the request thread's MDC context map already contains zero keys at the boundary, THE Mdc_Cleanup_Filter SHALL complete its clearing step without error and leave the context map empty.

### Requirement 5: RFC 9457 problem details error contract

**User Story:** As an API consumer, I want consistent machine-readable error responses, so that I can handle failures uniformly without exposure to internal details.

#### Acceptance Criteria

1. WHEN the Backend returns any response with an HTTP status code in the range 400 to 599, THE Problem_Handler SHALL set the response `Content-Type` header to `application/problem+json`.
2. WHEN the Backend returns a response with an HTTP status code in the range 400 to 599, THE Problem_Handler SHALL include in the problem body a non-empty `status` member equal to the HTTP status code, a non-empty `title` member, a non-empty `correlationId` member equal to the correlation identifier of the originating request, and a `timestamp` member formatted as a UTC ISO-8601 instant representing the time the error response was generated.
3. IF an unhandled exception occurs, THEN THE Problem_Handler SHALL return HTTP status 500 with a generic, non-empty `title` member, and SHALL exclude stack traces, SQL statements, internal identifiers, and infrastructure details from every member of the response body.
4. WHEN a request targets a route that matches no registered endpoint, THE Backend SHALL return an RFC 9457 problem response with HTTP status 404 and a `Content-Type` header of `application/problem+json`.
5. IF a request fails input validation, THEN THE Problem_Handler SHALL return HTTP status 400 with an RFC 9457 problem response whose `title` member indicates a validation failure.

### Requirement 6: Permit-all security with configurable CORS seam

**User Story:** As a developer, I want a security layer wired with a clearly marked permit-all policy and configurable CORS, so that the seam for future JWT authentication exists without blocking skeleton requests.

#### Acceptance Criteria

1. WHEN a request is received on any endpoint, THE Security_Config SHALL authorize the request without requiring authentication credentials and SHALL return the endpoint's response rather than an authentication or authorization rejection.
2. THE Security_Config SHALL disable CSRF protection.
3. THE Security_Config SHALL configure session management as stateless such that no server-side session is created or stored for any request.
4. THE Backend SHALL derive the allowed CORS origins, allowed methods, and allowed headers from externalized configuration properties resolved at application startup.
5. IF any of the externalized CORS origins, methods, or headers properties is absent or resolves to an empty value at application startup, THEN THE Backend SHALL fail startup and emit an error indicating which CORS property is missing.
6. WHEN the Backend returns a CORS response to the Frontend, THE Backend SHALL include `X-Correlation-Id` in the list of exposed response headers.
7. WHEN the application starts with the permit-all policy active, THE Security_Config SHALL emit a startup warning log indicating that the permit-all policy is skeleton-only and must be replaced before deployment to a real environment.

### Requirement 7: Full domain schema with zero seed data

**User Story:** As a QA engineer, I want a fresh database to contain the complete schema and no application data, so that I can create all test data through the application without manual database edits.

#### Acceptance Criteria

1. WHEN the Backend starts against a clean Database, THE Migration_Runner SHALL apply the changelogs that create the full target domain schema, including the `users`, `verification_tokens`, `teams`, `epics`, `tickets`, and `comments` tables.
2. THE Migration_Runner SHALL create `ticket_type` with exactly the values `bug`, `feature`, and `fix`.
3. THE Migration_Runner SHALL create `ticket_state` with exactly the canonical values `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, and `done`.
4. THE Migration_Runner SHALL enforce case-insensitive uniqueness on `users.email` and `teams.name`.
5. THE Migration_Runner SHALL define a cascading delete from `tickets` to `comments` so that deleting a ticket deletes its comments.
6. THE Migration_Runner SHALL define restricting foreign keys from `tickets` to `teams` and from `tickets` to `epics`.
7. WHEN migrations complete on a clean Database, THE System SHALL leave every Application_Table with zero rows and SHALL retain only Liquibase metadata rows.
8. THE System SHALL provide schema creation exclusively through repeatable migrations and SHALL NOT load sample or seed data on the default startup path.
9. IF a write attempts to persist a `users.email` or `teams.name` value that matches an existing value when compared case-insensitively, THEN THE System SHALL reject the write, leave the existing row unchanged, and return an error indicating a uniqueness violation.
10. IF a delete is attempted on a `teams` or `epics` row that is referenced by one or more `tickets` rows, THEN THE System SHALL reject the delete, preserve both the referenced row and the referencing `tickets` rows, and return an error indicating a restricting foreign-key violation.
11. IF the Migration_Runner fails to apply any changelog during startup, THEN THE System SHALL halt startup, leave the Database schema in the state it held before the failed changelog, and surface an error indicating the migration failure.

### Requirement 8: Mock board endpoint

**User Story:** As a frontend developer, I want a backend endpoint that returns a realistic board shape, so that the SPA can prove the end-to-end request path before real board logic exists.

#### Acceptance Criteria

1. WHEN a client requests `GET /api/v1/mock/board`, THE Mock_Board_Endpoint SHALL return HTTP status 200 with a body containing an ordered collection of columns.
2. WHEN a client requests `GET /api/v1/mock/board`, THE Mock_Board_Endpoint SHALL return exactly five columns ordered as `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`, with each column mapped to exactly one distinct Canonical_Ticket_State.
3. THE Mock_Board_Endpoint SHALL return each card with a non-empty title of 1 to 200 characters and a type equal to exactly one of `bug`, `feature`, or `fix`.
4. THE Mock_Board_Endpoint SHALL return hardcoded sample data without reading from any Application_Table in the Database.
5. WHEN a client requests `GET /api/v1/mock/board`, THE Mock_Board_Endpoint SHALL return a board containing at least one card distributed across the columns.

### Requirement 9: Frontend renders backend data with explicit UI states

**User Story:** As a user, I want the board page to show loading, success, and error states from live backend data, so that the interface stays usable and the SPA-to-API path is proven.

#### Acceptance Criteria

1. WHEN the Board_Page mounts, THE Frontend SHALL issue a GET request for board data to the Mock_Board_Endpoint with a request timeout of 10 seconds.
2. WHILE the board request is in flight, THE Board_Page SHALL display a loading indicator and SHALL NOT display board columns or an error indicator.
3. WHEN the board request returns a success response, THE Board_Page SHALL render exactly five columns, one per state column in the response, in the order the columns appear in the response.
4. WHEN the board request returns a success response, THE Board_Page SHALL replace the loading indicator with the rendered columns.
5. IF the board request fails due to a network error, a non-success response, or no response received within the 10-second timeout, THEN THE Board_Page SHALL display an error indicator, SHALL remove the loading indicator, and SHALL NOT render board columns.
6. WHEN the Frontend issues a request to the Mock_Board_Endpoint, THE Frontend SHALL include the `X-Correlation-Id` header on the request with a non-empty value of at most 128 characters.

### Requirement 10: Supporting store and health endpoints

**User Story:** As an operator, I want the supporting store wired and standard health endpoints exposed, so that container orchestration can verify readiness and surface misconfiguration early.

#### Acceptance Criteria

1. THE Backend SHALL configure a connection to Valkey_Store using externalized host and port configuration properties.
2. IF the Valkey_Store host or port configuration properties are absent or malformed at startup, THEN THE Backend SHALL terminate the startup sequence, log an error indicating the missing or invalid Valkey_Store configuration, and exit with a non-zero status code without serving any requests.
3. IF Valkey_Store is unreachable within 5 seconds during startup, THEN THE Backend SHALL terminate the startup sequence, log an error indicating Valkey_Store is unreachable, and exit with a non-zero status code without serving any requests.
4. THE Backend SHALL expose health and readiness state through Spring Boot Actuator at `/actuator/health`.
5. WHEN the `/actuator/health` endpoint is queried, THE Backend SHALL report an UP status while Valkey_Store connectivity is established and a DOWN status while Valkey_Store is unreachable.
6. WHEN an unauthenticated caller queries the health or readiness endpoints, THE Backend SHALL return the health and readiness state without requiring authentication.

### Requirement 11: Thin runtime artifact

**User Story:** As a maintainer, I want the shipped backend jar to carry only mandatory runtime dependencies, so that the artifact stays lean and dev/test tooling never bloats it.

#### Acceptance Criteria

1. WHEN the Build_Tooling packages the backend jar, THE Build_Tooling SHALL include only dependencies of runtime scope and SHALL exclude dependencies of test, provided, and build-time scope.
2. WHEN the Build_Tooling packages the backend jar, THE Build_Tooling SHALL exclude compiled classes originating from Lombok, MapStruct, Testcontainers, Cucumber, and devtools from the packaged jar.
3. THE Build_Tooling SHALL place optional, development-only, and build-time tooling behind Maven profiles that are inactive by default and activated only by explicit selection.
4. IF the packaged jar contains compiled classes from Lombok, MapStruct, Testcontainers, Cucumber, or devtools, THEN THE Build_Tooling SHALL fail the build with an error indicating the offending dependency and SHALL NOT produce a publishable artifact.

### Requirement 12: Quality gates

**User Story:** As a maintainer, I want formatting, static analysis, and coverage gates enforced from day one, so that feature work inherits a healthy and consistently checked codebase.

#### Acceptance Criteria

1. WHEN the Quality_Gates run, THE Quality_Gates SHALL verify backend formatting through Spotless on `.java`, `.feature`, and `pom.xml` files.
2. IF Spotless detects a formatting violation, THEN THE Quality_Gates SHALL fail the build and identify the violating files.
3. WHEN the Quality_Gates run, THE Quality_Gates SHALL run Error Prone with NullAway over the `com.bovae.yaj` packages, treating the codebase as non-null by default.
4. IF Error Prone or NullAway reports a violation, THEN THE Quality_Gates SHALL fail the build and identify the violating file and location.
5. THE Quality_Gates SHALL enforce a coverage threshold of at least 90% line coverage and at least 90% branch coverage computed over the merged unit and BDD execution data for non-excluded packages.
6. IF merged coverage falls below the 90% line or 90% branch threshold over non-excluded packages, THEN THE Quality_Gates SHALL fail the build.
7. WHEN the Quality_Gates run, THE Quality_Gates SHALL verify frontend linting and formatting through ESLint and Prettier.
8. IF ESLint or Prettier detects a violation, THEN THE Quality_Gates SHALL fail the build and identify the violating files.

### Requirement 13: Automated tests across both modules

**User Story:** As a maintainer, I want automated backend and frontend tests covering the skeleton seams, so that the proven wiring stays proven as the code evolves.

#### Acceptance Criteria

1. THE System SHALL provide backend unit tests for the Correlation_Filter, Mdc_Cleanup_Filter, Problem_Handler, and Mock_Board_Endpoint that assert the observable behaviors specified in Requirements 3, 4, 5, and 8 respectively.
2. THE System SHALL provide a Cucumber BDD suite that, with a real PostgreSQL Database and Valkey_Store provisioned through Testcontainers, exercises the startup path including Migration_Runner execution, Correlation_Filter handling, and Problem_Handler responses.
3. WHEN the BDD suite requests the Mock_Board_Endpoint against the running stack, THE BDD suite SHALL verify the response contains exactly five columns, one per Canonical_Ticket_State in workflow order.
4. WHEN the BDD suite sends a request carrying a non-blank `X-Correlation-Id` header, THE BDD suite SHALL verify the response returns the same value, AND WHEN the BDD suite sends a request with an absent or blank `X-Correlation-Id` header, THE BDD suite SHALL verify the response returns a generated non-blank Correlation_Id.
5. WHEN the BDD suite requests an unknown route, THE BDD suite SHALL verify the error response uses content type `application/problem+json` and includes the `status`, `title`, `correlationId`, and `timestamp` members.
6. WHILE the BDD suite runs against a freshly migrated Database, THE BDD suite SHALL verify the `users`, `teams`, `epics`, `tickets`, and `comments` tables exist and each Application_Table contains zero rows.
7. THE System SHALL provide a frontend test for the Board_Page that asserts a loading indicator while the board request is in flight, five rendered columns on a successful response, and an error indicator on a failed response.
8. THE System SHALL provide a frontend smoke test that loads the Frontend against the running composed stack and asserts five board columns are visible.

### Requirement 14: Continuous integration pipeline

**User Story:** As a maintainer, I want parallel, non-duplicating CI jobs, so that builds are fast and each check has a single clear owner.

#### Acceptance Criteria

1. WHEN a push or pull request targets `develop` or `main`, THE CI_Pipeline SHALL run the build, lint, and test jobs for the backend module and the frontend module.
2. THE CI_Pipeline SHALL run its jobs in parallel and SHALL NOT execute the same individual check (build, lint, or test) in more than one job.
3. WHEN a commit message is evaluated, THE CI_Pipeline SHALL validate it against the Conventional Commits standard.
4. IF a commit message does not conform to the Conventional Commits standard, THEN THE CI_Pipeline SHALL fail the validation job and produce an error indication identifying the non-conforming commit message.
5. IF any build, lint, or test job completes with a non-success status, THEN THE CI_Pipeline SHALL report an overall failure status and indicate which job failed, without marking the run as successful.
6. WHEN the backend test job runs, THE CI_Pipeline SHALL publish a test report and upload the coverage report as a downloadable artifact retained for at least 30 days.
7. WHEN the frontend test job runs, THE CI_Pipeline SHALL publish a frontend test report.

### Requirement 15: Release and dependency tooling

**User Story:** As a maintainer, I want independent release automation and dependency updates for each module, so that the two deployables version separately and stay current.

#### Acceptance Criteria

1. THE Release_Tooling SHALL provide independent semantic-release configurations for the backend and frontend modules, each using a module-specific tag prefix such that the two modules' release tags do not collide.
2. WHEN a release runs for one module, THE Release_Tooling SHALL version and tag only that module and SHALL leave the other module's version and tags unchanged.
3. THE Release_Tooling SHALL configure Dependabot to monitor the Maven dependencies under `/be`, the npm dependencies under `/fe`, and the GitHub Actions, each on a weekly schedule on a fixed day targeting `develop` with at most 5 open pull requests per entry.
4. WHEN files are staged for commit, THE Release_Tooling SHALL run pre-commit hooks that apply backend Spotless formatting and frontend ESLint and Prettier on the relevant staged files.
5. IF a pre-commit hook detects a lint or formatting violation that cannot be automatically fixed, THEN THE Release_Tooling SHALL abort the commit, report the affected files, and preserve the staged changes.
6. THE Release_Tooling SHALL treat `main` as the stable release branch and `develop` as a prerelease branch on the `dev` channel for both the backend and frontend semantic-release configurations.
7. WHEN releasable commits land on `develop`, THE Release_Tooling SHALL produce a prerelease version bearing the `dev` pre-release identifier (a version of the form `MAJOR.MINOR.PATCH-dev.N`) and SHALL mark the corresponding GitHub release as a prerelease, AND WHEN releasable commits land on `main`, THE Release_Tooling SHALL produce a stable release version without a pre-release identifier.

### Requirement 16: Secret hygiene and externalized configuration

**User Story:** As a security-conscious maintainer, I want no committed secrets and all environment-specific values externalized, so that credentials and SMTP secrets never enter source control.

#### Acceptance Criteria

1. THE System SHALL resolve every environment-specific value, including the datasource URL, datasource username, datasource password, Valkey_Store host, Valkey_Store port, CORS allowed origins, SMTP host, and SMTP port, from environment variables or externalized configuration properties at startup, with no such value present as a literal in source-controlled application code.
2. THE System SHALL keep all source-controlled files free of plaintext secret values, where a secret value includes any user password, datasource password, SMTP credential, API token, or private key, and SHALL represent such values only as environment-variable references or placeholder tokens that resolve at runtime.
3. WHERE email delivery is configured, THE System SHALL read the SMTP host and SMTP port from environment variables, accepting an SMTP port in the range 1 to 65535, so that the SMTP target can be swapped per environment without code changes.
4. IF a required environment-specific value listed in criterion 1 is absent or empty when the System starts, THEN THE System SHALL fail to start and emit a startup error indicating which configuration value is missing, without substituting a hard-coded fallback value.
5. IF the configured SMTP port is non-numeric or outside the range 1 to 65535, THEN THE System SHALL reject the configuration at startup and emit an error indicating the invalid SMTP port, without starting email delivery.

### Requirement 17: Project documentation

**User Story:** As a new contributor, I want a README describing prerequisites, configuration, and startup, so that I can run and work on the solution without tribal knowledge.

#### Acceptance Criteria

1. THE System SHALL include a README file located at the repository root.
2. THE README SHALL list every host-installed prerequisite required to run the solution, and SHALL state that Docker Compose is the only required host runtime and that no host-installed frontend, backend, or database runtime is required.
3. THE README SHALL document the single `docker compose up --build` startup command run from the repository root.
4. THE README SHALL document each externalized configuration value required to run the solution, including for each value its name, purpose, and default value.
5. WHEN a contributor follows only the documented prerequisites, configuration, and startup command from a clean checkout, THE System SHALL start the complete solution without requiring any step not described in the README.
