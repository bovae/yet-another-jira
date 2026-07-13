# be-package-structure Specification

## ADDED Requirements

### Requirement: Feature packages own their vertical web slice
Backend code SHALL be organized package-by-feature: each feature package (`tickets`, `board`, `comments`, `epics`, `teams`, `auth`) MUST contain its controller, service, and request/response DTOs. New endpoints MUST be added to the owning feature package, not to a central layer package.

#### Scenario: Adding an endpoint to an existing feature
- **WHEN** a developer adds a new endpoint for tickets
- **THEN** the controller method, service logic, and DTOs live under `com.bovae.yaj.tickets`

#### Scenario: Adding a new feature
- **WHEN** a new feature (e.g., attachments) is introduced
- **THEN** a new package `com.bovae.yaj.attachments` is created containing its controller, service, and DTOs

### Requirement: Domain is a shared kernel
JPA entities, domain enums, and repositories SHALL remain in the shared `domain/` package (`domain/model`, `domain/enums`, `domain/repository`). Feature packages MUST NOT own entities or repositories, because repositories are consumed across features and entities form one connected JPA graph.

#### Scenario: Feature needs data access
- **WHEN** a feature service needs persistence
- **THEN** it injects a repository from `com.bovae.yaj.domain.repository` rather than defining its own

### Requirement: Web package holds only cross-cutting HTTP infrastructure
The `web/` package SHALL contain only HTTP concerns that span all features: servlet filters (`web/filter`) and global error handling (`web/error`). It MUST NOT contain feature controllers or feature DTOs.

#### Scenario: Global error mapping change
- **WHEN** the error response format changes for all endpoints
- **THEN** the change is made in `web/error` (e.g., `GlobalExceptionHandler`), not in feature packages

### Requirement: Co-located classes use minimal visibility
Classes referenced only within their feature package SHOULD be package-private. A class SHALL be made `public` only when it is referenced from another package (including test sources in other packages).

#### Scenario: New feature service
- **WHEN** a service is only injected into a controller in the same package
- **THEN** the service class is declared package-private
