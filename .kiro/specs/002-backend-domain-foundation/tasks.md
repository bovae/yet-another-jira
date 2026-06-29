# Implementation Plan: Epic E0 — Backend domain foundation

## Overview

This plan implements the E0 persistence and shared-foundation layer described in `design.md` and
required by `requirements.md`. It adds, on top of the `001-skeleton-bootstrap` skeleton: the six JPA
entities (raw-`UUID` foreign keys, DB-generated id/timestamps), the `TicketType`/`TicketState` enums,
the six Spring Data repositories with reference/lookup queries, the four domain exceptions plus their
RFC 9457 mapping on the existing `GlobalProblemHandler`, the `CurrentUserProvider` seam, and the
reusable `SqlStatementCount` N+1 harness. E0 ships **no HTTP endpoints and no business CRUD**.

Implementation language is **Java 21 / Spring Boot 3.5.x** (taken directly from the design — no
pseudocode). Conventions follow the project's existing code, Spotless (palantir, no wildcard
imports), Error Prone + NullAway (`@NonNull`-by-default, explicit `@Nullable`), and the test
conventions (JUnit 5 + Mockito, parameterized tests, Cucumber for BDD).

Dependency order honored throughout: **error package → enums → entities → repositories →
exception-handler wiring → current-user seam → harness/base → integration tests → BDD → full gate.**

Key invariants the tasks must not break:
- The schema is **locked**; entities map existing columns exactly and Hibernate runs in `validate`
  mode only (Liquibase stays the sole schema owner).
- Entities live in `com.bovae.yaj.domain.model` (JaCoCo-excluded, Lombok boilerplate — **not**
  unit-tested directly). Enums live in `com.bovae.yaj.domain.enums` (coverage-counted — unit-tested).
- No `@PrePersist`/`@PreUpdate` anywhere; `Ticket.modified_at` is never advanced by persistence.
- Thin-jar enforcement must stay green (Testcontainers/Cucumber remain test-scoped).

## Tasks

- [x] 1. Build and configuration wiring
  - [x] 1.1 Declare the `commons-lang3` dependency in `be/pom.xml`
    - Add `org.apache.commons:commons-lang3` to the main `<dependencies>` with **no** `<version>`
      (Spring Boot BOM-managed); it is currently only transitive via `liquibase-core` but E0 imports
      `StringUtils.isBlank` directly in the enums (design Decision B).
    - Keep the POM sorted so the Spotless `sortPom` check passes.
    - _Requirements: 17.4; design "Configuration changes" + Decision B_

  - [x] 1.2 Switch Hibernate to schema-validation mode in `be/src/main/resources/application.yml`
    - Change `spring.jpa.hibernate.ddl-auto` from `none` to `validate` so entity↔table mismatches
      fail application startup; leave `spring.jpa.open-in-view: false` unchanged.
    - Do **not** add seed data or alter the schema.
    - _Requirements: 1.5, 1.6, 17.3, 19.5_

  - [x] 1.3 Make Testcontainers available to the `src/test/java` integration tests and align the local test command
    - The design places `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` integration
      tests and `AbstractPostgresIntegrationTest` in `src/test/java`, but the three Testcontainers
      deps (`spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`,
      `org.testcontainers:postgresql`) are declared only in the `it` and `bdd` profiles, so a
      profile-less `./mvnw test` cannot compile them.
    - Ensure those tests compile and run under a Testcontainers-providing profile: CI already runs
      `./mvnw verify -Pbdd,it`; update the `Makefile` `be-test` target to run under `-Pbdd,it` (e.g.
      `./mvnw test -Pbdd,it`) so the local default path compiles and runs the integration + BDD
      tests. Keep the `it` and `bdd` profile IDs intact (CI references them).
    - Confirm the deps stay **test-scoped** so the `enforce-thin-jar` antrun check still passes; do
      not package Testcontainers into the artifact.
    - _Requirements: 18.1; design "Testing Strategy" wiring nuance_

- [x] 2. Implement the domain exception hierarchy
  - [x] 2.1 Create the four domain exceptions in `com.bovae.yaj.error`
    - Add `NotFoundException`, `ConflictException`, `ValidationException`, `UnauthorizedException`,
      each `extends RuntimeException` (unchecked) with a single `String message` constructor.
    - These are referenced by the enums (`ValidationException`) and the seam contract
      (`UnauthorizedException`), so this package comes first.
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x] 2.2 Write unit tests for the domain exceptions
    - Parameterized (`@MethodSource` over the four types) asserting each is a `RuntimeException`
      and round-trips its detail message via `getMessage()`.
    - _Requirements: 14.3, 14.4_

- [x] 3. Implement the TicketType and TicketState enums (`com.bovae.yaj.domain.enums`)
  - [x] 3.1 Implement `TicketType`
    - Constants `BUG("bug")`, `FEATURE("feature")`, `FIX("fix")`; `code()` accessor; static
      unmodifiable `BY_CODE` map; `parse(@Nullable String)` using
      `org.apache.commons.lang3.StringUtils.isBlank` for the null/blank guard and throwing
      `ValidationException` (blank → "required"; unknown → message naming the rejected code).
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [x] 3.2 Implement `TicketState`
    - Constants declared in ascending position order `NEW("new",1)` … `DONE("done",5)`; `code()` and
      `position()` accessors; static unmodifiable `BY_CODE` map; `parse(@Nullable String)` mirroring
      `TicketType` and throwing `ValidationException`.
    - Store `position` explicitly (not derived from `ordinal()`) to match `ticket_states.position`.
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_

  - [x] 3.3 Write unit test — TicketType round-trip (in `TicketTypeTest`)
    - `@EnumSource` over all constants: `parse(c.code()) == c` and `parse(c.code()).code().equals(c.code())`.
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 18.4_

  - [x] 3.4 Write unit test — TicketType invalid-input rejection (in `TicketTypeTest`)
    - `@NullAndEmptySource` + `@ValueSource`/`@MethodSource` of blanks and unknown codes; assert
      `ValidationException`; blank → "required" message, unknown → message names the rejected code.
    - _Requirements: 9.5, 9.6, 18.4_

  - [x] 3.5 Write unit test — TicketState round-trip (in `TicketStateTest`)
    - `@EnumSource` over all constants: `parse(c.code()) == c` and code round-trips.
    - _Requirements: 10.1, 10.2, 10.5, 10.6, 18.4_

  - [x] 3.6 Write unit test — TicketState invalid-input rejection (in `TicketStateTest`)
    - `@NullAndEmptySource` + curated unknown/blank inputs; assert `ValidationException` with the
      appropriate message.
    - _Requirements: 10.7, 10.8, 18.4_

  - [x] 3.7 Write unit test — TicketState position and ordering (in `TicketStateTest`)
    - `@EnumSource`: each constant's `position()` equals its canonical value and `ordinal()+1 == position()`.
    - _Requirements: 10.3, 10.4_

- [x] 4. Implement the JPA entities (`com.bovae.yaj.domain.model`)
  - [x] 4.1 Implement `User`, `VerificationToken`, and `Team`
    - Map every column exactly per the design's Data Models tables. PK is
      `@Id @Generated @ColumnDefault("gen_random_uuid()") UUID` (no `@GeneratedValue`/`@UuidGenerator`);
      `created_at`/`modified_at` are `@Generated @ColumnDefault("now()") Instant` (`created_at` also
      `updatable=false`); `VerificationToken`/`Team` have no `modified_at`.
    - Raw `UUID` foreign keys (`VerificationToken.userId`); explicit `@Nullable` on
      `User.deletedAt` and `VerificationToken.consumedAt`; `boolean emailVerified` primitive.
    - Lombok `@Getter @Setter @NoArgsConstructor @ToString`; `@ToString.Exclude` on
      `User.passwordHash` and `VerificationToken.tokenHash`. No `@EqualsAndHashCode`/`@Builder`, no
      `@PrePersist`/`@PreUpdate`.
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 8.1, 8.2, 17.5, 19.7_

  - [x] 4.2 Implement `Epic`, `Ticket`, and `Comment`
    - Same PK/timestamp/Lombok conventions as 4.1; raw `UUID` foreign keys (`Epic.teamId`,
      `Ticket.teamId`/`createdBy`, nullable `Ticket.epicId`, `Comment.ticketId`/`authorId`);
      `Comment` has no `modified_at`.
    - `Ticket.type`/`Ticket.state` are raw `String` lookup codes (design Decision B — no
      `@Enumerated`); `@Nullable` on `Epic.description` and `Ticket.epicId`.
    - `Ticket.modifiedAt` stays updatable but has **no** lifecycle callback advancing it (E7 owns it).
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 7.1, 7.2, 7.3, 8.1, 8.5, 8.6, 17.5, 19.7_

- [x] 5. Implement the Spring Data repositories (`com.bovae.yaj.domain.repository`)
  - [x] 5.1 Create the six `JpaRepository<T, UUID>` interfaces with reference and lookup queries
    - `UserRepository.findByEmail(String) -> Optional<User>` (relies on `citext`; no
      `IgnoreCase`/`LOWER`); `VerificationTokenRepository.findByTokenHash(String) -> Optional<VerificationToken>`;
      `TeamRepository` (CRUD only); `EpicRepository.existsByTeamId`; `TicketRepository.existsByTeamId`
      + `existsByEpicId`; `CommentRepository.existsByTicketId` + `countByTicketId`.
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 13.1, 13.2, 13.3, 13.4_

- [x] 6. Map the domain exceptions to RFC 9457 problem responses
  - [x] 6.1 Add the four `@ExceptionHandler` methods to the existing `com.bovae.yaj.web.error.GlobalExceptionHandler`
    - Map `NotFoundException`→404, `ConflictException`→409, `ValidationException`→400,
      `UnauthorizedException`→401 via a private `domainProblem(...)` helper that builds the body with
      `ProblemDetailFactory.create(status, title, ex.getMessage())` and routes through
      `handleExceptionInternal` (the single enrichment point for `correlationId` + UTC `timestamp`).
    - Build the body only from status + fixed title + author message so no stack trace, type name, or
      SQL leaks. Do not modify the existing catch-all `handleUnexpected`.
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7_

  - [x] 6.2 Write unit test — domain-exception → status mapping (in `GlobalProblemHandlerTest`)
    - Extend the existing standalone-MockMvc setup (throwing controller + advice) or
      `@MethodSource` over the four types; assert each yields exactly its status.
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 18.5_

  - [x] 6.3 Write unit test — problem body enrichment, detail, and non-leakage (in `GlobalProblemHandlerTest`)
    - Assert each mapped body carries `correlationId` + UTC `timestamp`, `detail` equals the
      exception message, and the body contains no stack trace, internal type name, or SQL fragment.
    - _Requirements: 15.5, 15.6, 15.7, 18.5_

- [x] 7. Implement the current-user seam (`com.bovae.yaj.security`)
  - [x] 7.1 Create the `CurrentUserProvider` interface
    - Single operation `UUID requireCurrentUserId()`; Javadoc states it throws `UnauthorizedException`
      when no authenticated user is present (including never-attempted). Interface only — **no bean**
      in E0 (JWT impl deferred to E4).
    - _Requirements: 16.1, 16.4_

  - [x] 7.2 Write unit test — current-user seam contract (in `CurrentUserProviderTest`)
    - Use a test double: returns the `UUID` when present; throws `UnauthorizedException` when absent /
      never-authenticated.
    - _Requirements: 16.2, 16.3_

- [x] 8. Checkpoint — compile and run the unit suite
  - Ensure the pure-logic layer compiles and `./mvnw test -Pbdd,it` runs the enum, exception,
    handler, and seam unit tests green. Ensure all tests pass, ask the user if questions arise.

- [x] 9. Build the persistence verification foundation and DB-backed tests
  - [x] 9.1 Implement the reusable `SqlStatementCount` harness in `src/test/java/com/bovae/yaj/support`
    - Use Hibernate `Statistics` via
      `entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount()`;
      `flush()`+`clear()` before measuring to defeat L1-cache masking. Provide `around(...)`,
      `assertSingleStatement(...)`, and `assertCountIndependentOfRows(...)`. No new runtime dependency.
    - _Requirements: 19.8, 19.9_

  - [x] 9.2 Implement the `AbstractPostgresIntegrationTest` base in `src/test/java`
    - `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` against a singleton
      `PostgreSQLContainer("postgres:16-alpine")` (Boot `@ServiceConnection`), so Liquibase builds the
      real schema and Hibernate `validate` runs against it.
    - Enable `hibernate.generate_statistics=true` **scoped to integration tests only** (via
      `@TestPropertySource`/`@DynamicPropertySource` on the base, or `application-test.yml` +
      `@ActiveProfiles("test")`). Do **not** create a bare `src/test/resources/application.yml` — it
      would shadow the main config.
    - _Requirements: 18.1_

  - [x] 9.3 Write integration test — entity persist/reload and DB-default audit timestamps
    - Persist and reload all six entities; assert DB-generated `id` and non-null
      `created_at`/`modified_at` (where present), and that nullable fields round-trip with null and
      non-null values.
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 4.1, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 7.1, 7.2, 7.3, 8.2, 8.3, 8.5_

  - [x] 9.4 Write integration test — foreign-key violation rejections
    - Inserting a row with a dangling `user_id`/`team_id`/`epic_id`/`ticket_id`/`created_by`/`author_id`
      raises a database constraint violation (referential integrity enforced by the DB, not code).
    - _Requirements: 3.3, 5.2, 6.4, 6.5, 6.6, 7.2, 7.3, 17.2_

  - [x] 9.5 Write integration test — reference-query soundness
    - Seeded data: `existsByTeamId`/`existsByEpicId`/`existsByTicketId` true iff a referencing row
      exists; `countByTicketId` exact; unreferenced id → false / 0.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [x] 9.6 Write integration test — lookup-query correctness (citext)
    - `findByEmail` returns the user for every letter-case variation of the stored email (citext) and
      empty for a miss; `findByTokenHash` hits the exact hash and is empty for a miss.
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

  - [x] 9.7 Write integration test — single-statement reference/lookup execution
    - Use `SqlStatementCount.assertSingleStatement(...)` on a chosen `exists`/`count`/lookup query;
      assert exactly one SQL statement and no per-row lazy loading.
    - _Requirements: 18.10, 19.11_

  - [x] 9.8 Write integration test — statement count independent of row count
    - Use `SqlStatementCount.assertCountIndependentOfRows(...)`: count with one referencing row equals
      count with N referencing rows.
    - _Requirements: 18.11, 19.3, 19.10_

  - [x] 9.9 Write integration test — ticket `modified_at` not advanced by persistence
    - Persist a `Ticket`, read `modified_at`, re-save it unchanged, and assert `modified_at` is
      unchanged (no lifecycle callback advances it).
    - _Requirements: 8.6_

  - [x] 9.10 Write the primary-persistence BDD scenario (`src/bdd`, `bdd` profile)
    - One `Scenario` (tagged with a domain tag, following the existing `TestcontainersConfig` /
      `CucumberSpringConfig` / step conventions): persist a `User` and a `Team`, then a `Ticket`
      referencing both with a valid lookup `type`/`state` code, reload it, and assert the DB-generated
      `id` and audit timestamps are present and `type`/`state` round-trip. Steps resolve records
      through the autowired repositories (E0 has no endpoints).
    - _Requirements: 18.6_

- [x] 10. Final checkpoint — run the full quality gate and close any gaps
  - Run `./mvnw spotless:check verify -Pbdd,it` (the `make be-lint` target): Spotless (palantir, no
    wildcard imports) clean, Error Prone + NullAway clean, JaCoCo **90% line + 90% branch** over the
    non-excluded packages (the `bdd` profile aggregates unit + BDD coverage), and the full context
    starts under `validate` with every entity loaded and every repository bean resolved.
  - Watch for the design-flagged `citext`-vs-`validate` risk: the full-context integration test is
    where any Hibernate strictness against the `citext` columns will surface — fix per the design's
    mitigation if it does. Ensure all tests pass, ask the user if questions arise.
  - _Requirements: 1.6, 18.1, 18.2, 18.3, 18.7, 18.8, 18.9_

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP, but the
  Task 10 quality gate (90/90 coverage, plus Req 18.4/18.5/18.6/18.10/18.11) will not pass without
  them — they are the means by which the mandatory gate is satisfied.
- Entities (`domain.model`) are JaCoCo-excluded Lombok/JPA boilerplate and are **not** unit-tested
  directly (Req 18.7); their behavior is verified through the Testcontainers integration tests and
  the BDD scenario.
- Pure-logic tests (enums, exceptions, handler, seam) sit next to their implementation to catch
  errors early; DB-backed tests are grouped under Task 9 because they require the entities,
  repositories, harness, and Testcontainers base to exist first.
- Each test sub-task references the requirement clauses it covers.
- Checkpoints (Tasks 8 and 10) provide incremental and final validation.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "2.1", "4.1", "4.2", "9.1"] },
    { "id": 1, "tasks": ["3.1", "3.2", "2.2", "5.1", "6.1", "7.1", "9.2"] },
    { "id": 2, "tasks": ["3.3", "3.5", "6.2", "7.2", "9.3", "9.4", "9.5", "9.6", "9.7", "9.8", "9.9", "9.10"] },
    { "id": 3, "tasks": ["3.4", "3.6", "6.3"] },
    { "id": 4, "tasks": ["3.7"] }
  ]
}
```
