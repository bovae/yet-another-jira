# Requirements Document

## Introduction

This document specifies the requirements for **Epic E5 — Teams CRUD + delete guard**
(`007-teams-crud-delete-guard`), the first business-feature epic for the yet-another-jira Kanban
ticket tracker and the first to expose persistent application data through the API. E5 builds
directly on the domain foundation (`002-backend-domain-foundation`, "E0") and on authentication
enforcement (`006-authn-authz-enforcement`, "E4").

After E0 the `teams` table is mapped by the `com.bovae.yaj.domain.model.Team` entity and reachable
through the `com.bovae.yaj.domain.repository.TeamRepository`, and the reference-counting queries the
delete guard needs already exist on the neighbouring repositories
(`EpicRepository.existsByTeamId`, `TicketRepository.existsByTeamId`). After E4 every `/api/v1/**`
route other than the four public auth endpoints is a Protected_Endpoint guarded by the JWT
authentication filter, and the `CurrentUserProvider` is implemented. E5 layers the first real
business CRUD onto that foundation: the team lifecycle.

E5 delivers five HTTP operations over the team resource:

1. **List teams** at `GET /api/v1/teams` — returns every team. There is no membership, ownership, or
   per-team visibility (§4): every authenticated, verified user sees and manages every team.
2. **Create team** at `POST /api/v1/teams` — trims the submitted name, rejects an empty name, and
   enforces case-insensitive uniqueness, returning `409` on a clash.
3. **Get team** at `GET /api/v1/teams/{id}` — returns a single team or `404`.
4. **Rename team** at `PUT /api/v1/teams/{id}` — the same trim and uniqueness rules as create, plus
   the `modified_at` discipline: the modified timestamp advances **only** on an actual change to the
   stored name; saving an unchanged name must not advance it (mirroring the ticket `modified_at`
   semantics that E7 will formalize, §6).
5. **Delete team** at `DELETE /api/v1/teams/{id}` — guarded: a team that is referenced by any epic or
   any ticket cannot be deleted and yields `409` with a clear message; deletion never cascades
   (§4, §9).

E5 reuses, rather than re-implements, the foundation already shipped: the `Team` entity and its
existing column mappings (`id`, `name`, `created_at`, `modified_at`), the `TeamRepository`, the
existence queries `EpicRepository.existsByTeamId` and `TicketRepository.existsByTeamId`, the typed
domain exception hierarchy (`NotFoundException` → `404`, `ConflictException` → `409`,
`ValidationException` → `400`, `UnauthorizedException` → `401`) and its central mapping in the
`com.bovae.yaj.web.error.GlobalExceptionHandler`, the `ProblemDetailFactory` enrichment
(`correlationId`, ISO-8601 UTC `timestamp`), the E4 authentication enforcement that makes every team
route require a valid bearer token, and the `CurrentUserProvider` accessor. The `teams` table schema
is **locked**: E5 maps and reads the existing columns and must not alter the schema, and the database
foreign keys from `epics.team_id` and `tickets.team_id` (both `ON DELETE RESTRICT`) remain the
referential-integrity backstop behind the service-level delete guard.

E5 is **backend only**. The team-management screen — the list, the create/rename dialogs, and the
reference-aware disabled delete control — is **Epic E13** and is out of scope here; E5 delivers only
the API contract that E13 consumes. E5 introduces no roles, no team membership, and no per-team
authorization beyond the authenticated-versus-anonymous boundary E4 already enforces.

These requirements are derived from the E5 entry in `requirements/epics-catalog.md` and the product
requirements source `requirements/yet-another-jira.md` (primarily §4 — team fields, trimmed and
case-insensitively-unique names, and the no-cascade delete guard; §9 — all writes through the API,
`409` for delete-guard and uniqueness conflicts, UTC ISO-8601 timestamps, and UUID identifiers; and
§11 — protect authenticated endpoints and support loading / empty / success / error states).

## Decisions and Open Questions

The constraints below were under-specified by the source material. Each is captured as a concrete,
testable decision in the requirements that follow. Items marked **[CONFIRM]** change observable
behavior and should be confirmed before the design phase; the requirements encode the recommended
default so review can proceed. Unmarked items record a confirmed or non-behavioral choice.

1. **No creator or owner is recorded on a team (Requirement 3, 8). [CONFIRM]** The `teams` table has
   exactly four columns (`id`, `name`, `created_at`, `modified_at`) and **no `created_by` / owner
   column**, and the schema is locked. Consequently E5 does **not** stamp the creating user onto a
   team and the Team_Response carries no creator field. The `CurrentUserProvider` is therefore not
   consulted by the Team_Service for stamping; the requirement that "all teams are managed by every
   verified user" (§4) is satisfied purely by E4's authenticated-versus-anonymous enforcement, which
   makes every team route a Protected_Endpoint. Rationale: the product explicitly excludes team
   ownership and membership (§4, §12), and the schema reflects that. *Alternative:* add a `created_by`
   column — rejected, it would change the locked schema and introduce an ownership concept the product
   excludes.

2. **List ordering is by team name, case-insensitive, ascending (Requirement 2). [CONFIRM]** §4 does
   not state an order for `GET /api/v1/teams`. E5 returns teams ordered by Normalized_Team_Name
   ascending under a case-insensitive collation, so the list is deterministic and directly usable by
   the E13 team selector without client-side sorting. Rationale: a stable, human-meaningful order is
   the most useful default for a management list and a selector. *Alternative:* order by `created_at`
   — rejected as less useful for a pick list, though equally deterministic.

3. **"Unchanged name" for the `modified_at` rule is exact trimmed-string equality (Requirement 5, 6).
   [CONFIRM]** On rename, the Team_Service compares the incoming Normalized_Team_Name with the
   currently stored name using **exact (case-sensitive) string equality**. If they are byte-for-byte
   equal, the request is a no-op: the stored row is left untouched and `modified_at` does not advance.
   A **case-only** edit (for example `Platform` → `platform`) is treated as an **actual change** — the
   stored display value differs — so it is persisted, advances `modified_at`, and is **not** rejected
   as a self-conflict by the case-insensitive uniqueness check. Rationale: `name` is `citext`, so case
   does not affect identity or uniqueness, but the stored string is the display value a user chose to
   change, so a case edit is a real edit. *Alternative:* treat case-only edits as no-ops — rejected, it
   would silently discard a deliberate display-casing change.

4. **Uniqueness is enforced by a service pre-check, with the database constraint as a race backstop
   (Requirement 3, 5, 9). [CONFIRM]** The Team_Service checks case-insensitive name availability
   before insert/update and raises a Conflict_Error (`409`) on a clash, producing a clear message. The
   `teams.name` column is `citext NOT NULL UNIQUE`, so the database is the ultimate guarantor; if two
   concurrent requests pass the pre-check and one then trips the unique constraint, the resulting data
   integrity violation for the team-name constraint is also surfaced as a `409` Conflict_Error rather
   than a `500`. Rationale: the pre-check gives a clean, message-bearing `409` on the common path while
   the constraint closes the last-writer race the product accepts elsewhere (§9, last-write-wins).
   *Alternative:* rely solely on the constraint and translate the violation — rejected, it yields a
   less specific message and couples the contract to the database error shape.

5. **A malformed team id is a client error, not a server error (Requirement 9). [CONFIRM]** A request
   to `GET|PUT|DELETE /api/v1/teams/{id}` whose `{id}` path segment is not a syntactically valid `UUID`
   is rejected with HTTP `400` (a Validation_Error shape), not `404` and not `500`. This requires the
   path-variable type-mismatch to be mapped through the existing Problem machinery rather than falling
   through to the generic `500` handler. Rationale: a syntactically invalid identifier is a malformed
   request, which §9 expects to return a meaningful client-error status. *Alternative:* map it to `404`
   — rejected, it conflates "you asked for a nonexistent thing" with "your request was malformed".

6. **Delete and fetch of an unknown team id return `404` (Requirement 4, 7). [CONFIRM]** `GET`, `PUT`,
   and `DELETE` against a syntactically valid `UUID` that matches no team raise a Not_Found_Error
   (`404`). Delete is therefore **not** treated as idempotent over a missing resource. Rationale: a
   `404` tells the E13 client the row is already gone and lets it refresh, which is clearer for a
   management screen than a silent `204`. *Alternative:* idempotent delete returning `204` for a
   missing id — rejected, it hides the "already deleted by someone else" case the UI should reflect.

7. **`PUT` is the rename verb; there is no `PATCH` (Requirement 5).** The catalog specifies
   `PUT /api/v1/teams/{id}`. Because `name` is the team's only mutable field, a full `PUT` replacement
   and a partial update are equivalent, so E5 exposes only `PUT` and accepts a body carrying the new
   `name`. This is the contract named by the catalog; it is non-behavioral beyond the verb choice and
   so is not marked **[CONFIRM]**.

8. **`modified_at` becomes service-managed; the schema is unchanged (Requirement 5, 6).** To advance
   `modified_at` on an actual rename, the Team_Service sets the timestamp explicitly within its
   transaction (the entity mapping is adjusted so the application, not only the database default,
   writes `modified_at`). `created_at` remains immutable after creation. This is an internal mapping
   detail that changes no column and no table, so it is not marked **[CONFIRM]**.

9. **Name trimming preserves internal whitespace (Requirement 3, 5).** "Trim" means removing leading
   and trailing whitespace only; whitespace **inside** the name (for example `Core Platform`) is
   preserved. A name that is empty or consists solely of whitespace is rejected as a Validation_Error
   after trimming. No maximum length is imposed, consistent with the source leaving team-name length
   unbounded. Non-behavioral clarification; not marked **[CONFIRM]**.

## Glossary

- **Teams_Feature**: The complete E5 deliverable taken as a whole: the Team_Controller, the Team_Service, the Team_Mapper, the team request and response DTOs, and the team-name uniqueness and delete-guard rules layered over the existing Team_Entity and Team_Repository.
- **Team_Resource**: The team as exposed over HTTP under the base path `/api/v1/teams`.
- **Team_Entity**: The existing JPA entity `com.bovae.yaj.domain.model.Team`, mapping the `teams` table columns `id` (`UUID`), `name` (`citext`), `created_at`, and `modified_at`.
- **Team_Repository**: The existing Spring Data repository `com.bovae.yaj.domain.repository.TeamRepository` for the Team_Entity, extended by E5 with the case-insensitive name-existence query the uniqueness check requires.
- **Epic_Repository**: The existing `com.bovae.yaj.domain.repository.EpicRepository`, whose `existsByTeamId(UUID)` query the Delete_Guard consults.
- **Ticket_Repository**: The existing `com.bovae.yaj.domain.repository.TicketRepository`, whose `existsByTeamId(UUID)` query the Delete_Guard consults.
- **Team_Service**: The new `@Transactional` application-layer component that holds the team business rules (trim, non-empty, case-insensitive uniqueness, no-op rename detection, delete guard) and is the only layer that invokes the repositories for team writes.
- **Team_Controller**: The new HTTP-only controller exposing the five team endpoints; it performs request binding and response shaping and delegates all business rules to the Team_Service.
- **Team_Mapper**: The MapStruct mapper, residing in a `mapper` package, that converts between the Team_Entity and the team DTOs.
- **Create_Team_Request**: The request DTO for `POST /api/v1/teams`, carrying the submitted team name.
- **Update_Team_Request**: The request DTO for `PUT /api/v1/teams/{id}`, carrying the submitted new team name.
- **Team_Response**: The response DTO returned for a single team and as the element type of the team list, carrying the team `id`, `name`, `created_at`, and `modified_at` and no other fields.
- **Team_Name**: The raw `name` value as submitted in a Create_Team_Request or Update_Team_Request, before trimming.
- **Normalized_Team_Name**: The Team_Name after leading and trailing whitespace is removed; internal whitespace is preserved.
- **List_Teams_Endpoint**: The `GET /api/v1/teams` endpoint that returns every team.
- **Create_Team_Endpoint**: The `POST /api/v1/teams` endpoint that creates a team.
- **Get_Team_Endpoint**: The `GET /api/v1/teams/{id}` endpoint that returns a single team.
- **Rename_Team_Endpoint**: The `PUT /api/v1/teams/{id}` endpoint that updates a team's name.
- **Delete_Team_Endpoint**: The `DELETE /api/v1/teams/{id}` endpoint that deletes a team subject to the Delete_Guard.
- **Delete_Guard**: The Team_Service rule that refuses to delete a team referenced by at least one epic or at least one ticket, raising a Conflict_Error instead of deleting and never cascading.
- **Team_Reference**: An epic row whose `team_id` equals the team's id, or a ticket row whose `team_id` equals the team's id.
- **Created_At**: The `teams.created_at` value, server-set in UTC at creation and immutable thereafter, serialized in API responses as an ISO-8601 representation in UTC.
- **Modified_At**: The `teams.modified_at` value, server-managed in UTC, serialized in API responses as an ISO-8601 representation in UTC, advanced only on an actual change to the stored Normalized_Team_Name.
- **Protected_Endpoint**: An HTTP endpoint that requires a valid Access_Token under the E4 Security_Policy; all five team endpoints are Protected_Endpoints.
- **Access_Token**: The signed JWT bearer credential issued at login (E3) and presented on the `Authorization: Bearer` header, validated by the E4 authentication filter.
- **Current_User_Provider**: The existing `com.bovae.yaj.security.CurrentUserProvider`, implemented by E4; available to E5 but not consulted for team writes because teams record no creator (decision 1).
- **Validation_Error**: A `com.bovae.yaj.error.ValidationException`, mapped by the Problem_Handler to HTTP status `400`.
- **Not_Found_Error**: A `com.bovae.yaj.error.NotFoundException`, mapped by the Problem_Handler to HTTP status `404`.
- **Conflict_Error**: A `com.bovae.yaj.error.ConflictException`, mapped by the Problem_Handler to HTTP status `409`.
- **Unauthorized_Error**: A `com.bovae.yaj.error.UnauthorizedException`, mapped by the Problem_Handler to HTTP status `401`.
- **Problem_Handler**: The existing `com.bovae.yaj.web.error.GlobalExceptionHandler` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`) that maps the typed domain exceptions to RFC 9457 problem responses.
- **Problem_Detail_Factory**: The existing `com.bovae.yaj.web.error.ProblemDetailFactory` that builds and enriches problem details with a `correlationId` and an ISO-8601 UTC `timestamp`.
- **Project_Build**: The Maven build for the `be` module, including the Spotless, Error Prone with NullAway, and JaCoCo quality gates.
- **Coverage_Gate**: The JaCoCo 90% line and 90% branch coverage gate that excludes `**/*Application.*`, `**/config/**`, `**/mapper/*Impl*`, and `**/model/**`.
- **Integration_Test_Context**: A Spring application context started against a PostgreSQL instance (and the Valkey instance the auth flow relies on) provisioned by Testcontainers under the `bdd` profile.

## Requirements

### Requirement 1: Authenticated access to the team endpoints

**User Story:** As a security owner, I want every team endpoint to require a valid bearer token, so that only authenticated users can read or change team data. (§3, §11)

#### Acceptance Criteria

1. THE Teams_Feature SHALL expose the List_Teams_Endpoint, the Create_Team_Endpoint, the Get_Team_Endpoint, the Rename_Team_Endpoint, and the Delete_Team_Endpoint as Protected_Endpoints under the existing E4 Security_Policy.
2. IF a request to any team endpoint supplies no Access_Token — because the `Authorization` header is absent, carries a scheme other than Bearer, or carries an empty Bearer credential — or supplies an Access_Token that is malformed, expired, signature-invalid, or denylisted, THEN THE Security_Policy SHALL reject the request with HTTP status `401`, THE Team_Controller handler SHALL NOT run, and the Teams_Feature SHALL read, create, modify, or delete no team data.
3. WHEN a request to any team endpoint supplies an Access_Token that passes validation, THE Security_Policy SHALL NOT reject the request with HTTP status `401` and THE Teams_Feature SHALL pass the request to the Team_Controller handler for processing under that endpoint's own rules.
4. THE Teams_Feature SHALL NOT introduce role-based, membership-based, or ownership-based authorization, so that every authenticated user is authorized to invoke every team endpoint.

### Requirement 2: List all teams

**User Story:** As an authenticated user, I want to retrieve the full list of teams, so that I can choose a team to manage or board. (§4, §11)

#### Acceptance Criteria

1. WHEN an authenticated request is sent to the List_Teams_Endpoint, THE List_Teams_Endpoint SHALL respond with HTTP status `200` and a body that is an array containing exactly one Team_Response for each existing team and no other elements.
2. WHILE no team exists, WHEN an authenticated request is sent to the List_Teams_Endpoint, THE List_Teams_Endpoint SHALL respond with HTTP status `200` and an empty array containing zero Team_Response elements, so that the client can render an empty state.
3. WHEN the List_Teams_Endpoint returns one or more teams, THE List_Teams_Endpoint SHALL order the Team_Response elements by Normalized_Team_Name in case-insensitive ascending order, and because no two stored team names are equal case-insensitively, THE List_Teams_Endpoint SHALL produce a total ordering that is identical across repeated identical requests.
4. THE List_Teams_Endpoint SHALL include in each returned Team_Response exactly the team `id`, `name`, Created_At, and Modified_At, and SHALL exclude any other field.

### Requirement 3: Create a team

**User Story:** As an authenticated user, I want to create a team with a unique name, so that I can group epics and tickets under it. (§4, §9)

#### Acceptance Criteria

1. WHEN an authenticated request is sent to the Create_Team_Endpoint with a Team_Name whose Normalized_Team_Name is non-empty and matches no existing team name case-insensitively, THE Team_Service SHALL persist a new team whose stored name is exactly the Normalized_Team_Name, and THE Create_Team_Endpoint SHALL respond with HTTP status `201` and a body containing the created Team_Response.
2. WHEN the Create_Team_Endpoint persists a new team, THE Team_Service SHALL store the Normalized_Team_Name — the Team_Name with leading and trailing whitespace removed and internal whitespace preserved — rather than the raw Team_Name.
3. IF a request to the Create_Team_Endpoint supplies a Team_Name that is missing, null, an empty string, or whose Normalized_Team_Name is empty after trimming leading and trailing whitespace, THEN THE Team_Service SHALL raise a Validation_Error and THE Create_Team_Endpoint SHALL respond with HTTP status `400` and SHALL NOT persist a team.
4. IF a request to the Create_Team_Endpoint supplies a Team_Name whose Normalized_Team_Name matches an existing team name case-insensitively, THEN THE Team_Service SHALL raise a Conflict_Error and THE Create_Team_Endpoint SHALL respond with HTTP status `409` and SHALL NOT persist a team.
5. WHEN the Create_Team_Endpoint responds with `201`, THE Team_Response SHALL carry a server-generated `UUID` `id`, a `name` equal to the stored Normalized_Team_Name, a Created_At, and a Modified_At, and SHALL exclude any other field.
6. IF two concurrent Create_Team_Endpoint requests each pass the case-insensitive uniqueness pre-check and one then violates the `teams.name` unique constraint on insert, THEN THE Team_Service SHALL raise a Conflict_Error and THE Create_Team_Endpoint SHALL respond with HTTP status `409` and SHALL NOT respond with HTTP status `500` and SHALL NOT persist a second team.

### Requirement 4: Retrieve a single team

**User Story:** As an authenticated user, I want to fetch one team by its identifier, so that I can view its details. (§4)

#### Acceptance Criteria

1. WHEN an authenticated request is sent to the Get_Team_Endpoint with an `{id}` that is a valid `UUID` matching an existing team, THE Get_Team_Endpoint SHALL respond with HTTP status `200` and a body containing that team's Team_Response.
2. IF a request to the Get_Team_Endpoint supplies an `{id}` that is a valid `UUID` matching no existing team, THEN THE Team_Service SHALL raise a Not_Found_Error and THE Get_Team_Endpoint SHALL respond with HTTP status `404` and SHALL NOT respond with HTTP status `200`.
3. WHEN the Get_Team_Endpoint responds with `200`, THE Team_Response SHALL include exactly the team `id`, `name`, Created_At, and Modified_At, and SHALL exclude any other field.

### Requirement 5: Rename a team

**User Story:** As an authenticated user, I want to rename a team while keeping names unique, so that I can correct or update a team's name without creating duplicates. (§4, §9)

#### Acceptance Criteria

1. WHEN an authenticated request is sent to the Rename_Team_Endpoint with an `{id}` that is a valid `UUID` matching an existing team and an Update_Team_Request whose Normalized_Team_Name is non-empty and matches the stored name of no team whose id differs from `{id}` case-insensitively, THE Team_Service SHALL update the stored name to the Normalized_Team_Name and THE Rename_Team_Endpoint SHALL respond with HTTP status `200` and a body containing the updated Team_Response.
2. IF a request to the Rename_Team_Endpoint supplies an `{id}` that is a valid `UUID` matching no existing team, THEN THE Team_Service SHALL raise a Not_Found_Error and THE Rename_Team_Endpoint SHALL respond with HTTP status `404`.
3. IF a request to the Rename_Team_Endpoint supplies a Team_Name that is missing, null, empty, or whose Normalized_Team_Name is empty after trimming, THEN THE Team_Service SHALL raise a Validation_Error and THE Rename_Team_Endpoint SHALL respond with HTTP status `400` and SHALL NOT modify the team.
4. IF a request to the Rename_Team_Endpoint supplies a Normalized_Team_Name that matches the stored name of a team whose id differs from `{id}` case-insensitively, THEN THE Team_Service SHALL raise a Conflict_Error and THE Rename_Team_Endpoint SHALL respond with HTTP status `409` and SHALL NOT modify the team.
5. WHEN the submitted Normalized_Team_Name differs from the target team's stored name only in letter case, THE Team_Service SHALL treat the request as an actual change, SHALL persist the new casing, SHALL advance Modified_At as specified in Requirement 6, and SHALL NOT reject the request as a uniqueness conflict against the same team.
6. WHEN the submitted Normalized_Team_Name is byte-for-byte, case-sensitively equal to the target team's currently stored name, THE Team_Service SHALL treat the request as a no-op as specified in Requirement 6, leaving the stored row and Modified_At unchanged, and THE Rename_Team_Endpoint SHALL respond with HTTP status `200` and a body containing the team's existing Team_Response.

### Requirement 6: Modified timestamp advances only on an actual change

**User Story:** As a board user, I want a team's modified timestamp to change only when its name actually changes, so that an unchanged save does not disturb ordering or audit signals. (§4, §6)

#### Acceptance Criteria

1. WHEN the Rename_Team_Endpoint persists a Normalized_Team_Name that is not byte-for-byte, case-sensitively equal to the target team's currently stored name, THE Team_Service SHALL treat the request as an actual change and SHALL set Modified_At to the current server time in UTC.
2. WHEN a request to the Rename_Team_Endpoint supplies a Normalized_Team_Name that is byte-for-byte, case-sensitively equal to the target team's currently stored name, THE Team_Service SHALL leave the stored row unchanged, issuing no update to the team's name, Created_At, or Modified_At.
3. WHEN the Rename_Team_Endpoint treats a request as a no-op because the submitted Normalized_Team_Name is byte-for-byte, case-sensitively equal to the target team's currently stored name, THE Rename_Team_Endpoint SHALL respond with HTTP status `200` and a body containing the team's existing Team_Response carrying its unchanged Created_At and unchanged Modified_At.
4. WHEN the Rename_Team_Endpoint processes a request against an existing team, whether the request is an actual change or a no-op, THE Team_Service SHALL leave Created_At unchanged.

### Requirement 7: Delete a team behind a reference guard

**User Story:** As an authenticated user, I want a team to be deletable only when nothing references it, so that I never lose epics or tickets to a cascading delete. (§4, §9)

#### Acceptance Criteria

1. WHEN an authenticated request is sent to the Delete_Team_Endpoint with an `{id}` that is a valid `UUID` matching an existing team that has no Team_Reference, THE Team_Service SHALL delete the team and THE Delete_Team_Endpoint SHALL respond with HTTP status `204` and an empty response body.
2. IF a request to the Delete_Team_Endpoint targets an existing team for which the Epic_Repository reports at least one referencing epic, THEN THE Team_Service SHALL raise a Conflict_Error and THE Delete_Team_Endpoint SHALL respond with HTTP status `409`, SHALL NOT delete the team, and SHALL leave the team and every referencing epic unchanged.
3. IF a request to the Delete_Team_Endpoint targets an existing team for which the Ticket_Repository reports at least one referencing ticket, THEN THE Team_Service SHALL raise a Conflict_Error and THE Delete_Team_Endpoint SHALL respond with HTTP status `409`, SHALL NOT delete the team, and SHALL leave the team and every referencing ticket unchanged.
4. WHEN the Delete_Guard raises a Conflict_Error, THE Conflict_Error SHALL carry a message that states the team cannot be deleted because it is referenced by at least one epic or at least one ticket.
5. IF a request to the Delete_Team_Endpoint supplies an `{id}` that is a valid `UUID` matching no existing team, THEN THE Team_Service SHALL raise a Not_Found_Error and THE Delete_Team_Endpoint SHALL respond with HTTP status `404` and SHALL NOT respond with HTTP status `409`.
6. THE Team_Service SHALL resolve the target team's existence before evaluating the Delete_Guard, and SHALL evaluate the Delete_Guard before issuing any delete, so that no team that has a Team_Reference is removed even though the database `ON DELETE RESTRICT` foreign keys remain as a backstop.

### Requirement 8: Identifiers and timestamps

**User Story:** As an API consumer, I want stable UUID identifiers and UTC ISO-8601 timestamps, so that I can rely on consistent, unambiguous values across the API. (§4, §9)

#### Acceptance Criteria

1. WHEN a team is created, THE Teams_Feature SHALL assign the team a unique `id` that is a `UUID` generated by the database default.
2. WHEN a team is created, THE Teams_Feature SHALL set Created_At to the server's current time expressed in UTC.
3. THE Teams_Feature SHALL serialize Created_At and Modified_At in every Team_Response as an ISO-8601 combined date-and-time representation in UTC with a zero UTC offset (for example, a trailing `Z` designator).
4. WHEN a team create, rename, or delete operation succeeds, THE Teams_Feature SHALL durably commit the corresponding insert, update, or delete to the PostgreSQL database before returning a success status, so that a subsequent retrieval reflects the change and the PostgreSQL database, not any client-side storage, is the system of record.
5. THE Teams_Feature SHALL NOT change a team's `id` or Created_At after the team is created.

### Requirement 9: RFC 9457 error contract

**User Story:** As an API consumer, I want team errors to use consistent, meaningful status codes and problem bodies, so that I can detect and present validation, not-found, and conflict failures uniformly. (§9, §11)

#### Acceptance Criteria

1. WHEN the Team_Service raises a Validation_Error, a Not_Found_Error, or a Conflict_Error during a team request, THE Problem_Handler SHALL produce an RFC 9457 problem response whose HTTP response status code and whose body `status` member both equal `400` for a Validation_Error, `404` for a Not_Found_Error, and `409` for a Conflict_Error.
2. WHEN the Problem_Handler produces any team problem response, including the malformed-identifier response described in criterion 3, THE Problem_Detail_Factory SHALL enrich the body with a `correlationId` member that is a non-empty string and a `timestamp` member serialized as an ISO-8601 representation in UTC bearing an explicit UTC designator (`Z` or `+00:00`) and denoting the server instant at which the response was produced.
3. IF a request to the Get_Team_Endpoint, the Rename_Team_Endpoint, or the Delete_Team_Endpoint supplies an `{id}` path segment that is not a syntactically valid `UUID`, THEN THE Teams_Feature SHALL respond with an RFC 9457 problem response whose HTTP response status code and whose body `status` member both equal `400`, and SHALL NOT respond with HTTP status `404` and SHALL NOT respond with HTTP status `500`.
4. WHEN the Teams_Feature returns any problem response, THE Teams_Feature SHALL exclude stack traces, internal class or type names, SQL statements or fragments, and any Access_Token value from every member of the response body.
5. THE Teams_Feature SHALL emit, for any typed domain failure, no HTTP error status other than `400`, `404`, or `409`, reusing the status mapping already defined for the Validation_Error, Not_Found_Error, and Conflict_Error and introducing no additional status mapping.

### Requirement 10: Layered structure

**User Story:** As a maintainer, I want the team feature to follow the project's controller-service-repository layering, so that business rules stay testable and out of the HTTP layer. (§9; epic conventions)

#### Acceptance Criteria

1. THE Team_Controller SHALL restrict its responsibilities to HTTP concerns — request binding, HTTP status selection, and response shaping — and SHALL delegate the trim, non-empty validation, case-insensitive uniqueness check, no-op rename detection, and Delete_Guard rules to the Team_Service.
2. THE Team_Service SHALL hold the trim, non-empty validation, case-insensitive uniqueness, no-op rename detection, and Delete_Guard rules for every team create, rename, and delete.
3. THE Team_Controller SHALL accept a Create_Team_Request on the Create_Team_Endpoint and an Update_Team_Request on the Rename_Team_Endpoint, SHALL return a Team_Response — or a list of Team_Response on the List_Teams_Endpoint — to the caller, and SHALL neither accept nor return a Team_Entity across the HTTP boundary.
4. THE Teams_Feature SHALL convert between the Team_Entity and the team DTOs only through the Team_Mapper residing in a `mapper` package, and SHALL place no such conversion in the Team_Controller.
5. THE Teams_Feature SHALL confine all team persistence access — every invocation of the Team_Repository, the Epic_Repository, and the Ticket_Repository for a team operation — to the Team_Service, and SHALL place no such access in the Team_Controller.
6. WHEN the Team_Service executes a team create, rename, or delete, THE Team_Service SHALL perform that write within a single database transaction, so that a failure during the write persists no partial change.

### Requirement 11: Quality gates and tests

**User Story:** As a maintainer, I want the team feature verified against the project's quality gates, so that the first business CRUD is proven before epics and tickets build on it. (§11; E5 Definition of Done)

#### Acceptance Criteria

1. THE Project_Build SHALL include Team_Service unit tests asserting that the stored team name is the Normalized_Team_Name with leading and trailing whitespace removed and internal whitespace preserved; that a missing, null, empty, or whitespace-only Team_Name yields a Validation_Error; that a create whose Normalized_Team_Name matches an existing team name case-insensitively yields a Conflict_Error; that a rename whose Normalized_Team_Name matches a different team's name case-insensitively yields a Conflict_Error; that a rename whose Normalized_Team_Name is exactly equal, case-sensitively, to the target team's stored name leaves the stored row unchanged and does not advance Modified_At; that a rename which changes the stored name advances Modified_At; that a rename differing from the target team's stored name only in letter case is persisted, advances Modified_At, and is not rejected as a uniqueness conflict against the same team; that a get, rename, or delete against a valid `UUID` matching no team yields a Not_Found_Error; that deleting a team with at least one referencing epic or at least one referencing ticket yields a Conflict_Error and removes no row; and that deleting an unreferenced team succeeds.
2. THE Project_Build SHALL include Team_Controller tests asserting that the Create_Team_Endpoint responds with HTTP status `201`, the List_Teams_Endpoint, the Get_Team_Endpoint, and the Rename_Team_Endpoint respond with HTTP status `200`, and the Delete_Team_Endpoint responds with HTTP status `204`; that every success body carrying a team contains exactly the Team_Response members `id`, `name`, Created_At, and Modified_At and no other member; and that the Validation_Error, Not_Found_Error, and Conflict_Error paths respond with HTTP status `400`, `404`, and `409` respectively, each with a problem body whose `status` member equals the HTTP status.
3. THE Project_Build SHALL include a Cucumber BDD scenario, tagged `@teams` and executed under the Integration_Test_Context with Testcontainers PostgreSQL, in which a client authenticated with an Access_Token obtained by signing up, verifying, and logging in creates a team and receives HTTP status `201`, renames that team and receives HTTP status `200`, and then, against a team referenced by an epic or a ticket, issues a delete and receives HTTP status `409` with the team still present.
4. THE Project_Build SHALL satisfy the Coverage_Gate thresholds of at least 90% line coverage and at least 90% branch coverage over the non-excluded packages, covering the Team_Controller and the Team_Service, neither of which falls under a Coverage_Gate exclusion.
5. WHEN the Spotless check and the Error Prone with NullAway check run during the Project_Build, THE Project_Build SHALL report zero formatting violations and zero static-analysis violations.
6. IF line coverage or branch coverage over the non-excluded packages is below 90%, or IF the Spotless check detects a formatting violation, or IF the Error Prone with NullAway check detects a static-analysis violation, THEN THE Project_Build SHALL fail.
