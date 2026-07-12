# Tasks — add-epics-crud

## 1. Repository & DTOs

- [x] 1.1 Add `List<Epic> findByTeamId(UUID teamId)` to `EpicRepository` (`TicketRepository.existsByEpicId` already exists)
- [x] 1.2 Add `EpicCreateRequest` record (`@NotNull UUID teamId`, `@NotBlank @Size(max = 200) String title`, `@Nullable @Size(max = 10000) String description`), `EpicUpdateRequest` record (same title/description rules, **no team field**), and `EpicResponse` record (id, teamId, title, description, createdAt, modifiedAt; static `from(Epic)`) in `web/dto`

## 2. Service

- [x] 2.1 Create `com.bovae.yaj.epics.EpicService` (`@Transactional`, injects `EpicRepository`, `TeamRepository`, `TicketRepository`): `list(@Nullable teamId)` — `findByTeamId` when present else `findAll`; `get(id)` (404)
- [x] 2.2 `create(teamId, title, description)` — team must exist (`NotFoundException`), title trimmed non-empty→`ValidationException` when blank/over-length, blank description collapses to null
- [x] 2.3 `update(id, title, description)` — 404 on unknown id; same normalize rules; sets title + description only (team untouched); `modified_at` advanced by `@UpdateTimestamp` on flush (design decision 7)
- [x] 2.4 `delete(id)` — 404 on unknown id; `ConflictException` when `ticketRepository.existsByEpicId`; delete + flush inside try translating `DataIntegrityViolationException`→`ConflictException` (race, design decision 5)

## 3. Controller

- [x] 3.1 Create `EpicController` (`/api/v1/epics`): `GET` list with optional `teamId` query param (200), `POST` create (201 + Location), `GET /{id}` (200), `PUT /{id}` (200), `DELETE /{id}` (204); delegate everything to the service

## 4. Unit Tests

- [x] 4.1 `EpicServiceTest` (Mockito): create trims title; `@ParameterizedTest` blank titles→ValidationException; over-length title→ValidationException; unknown team on create→NotFoundException; blank description stored null; update leaves teamId untouched and clears omitted description; unknown id→NotFoundException (get/update/delete); delete with tickets→ConflictException; delete race (`DataIntegrityViolationException`)→ConflictException; clean delete invokes repository; list with/without teamId filter
- [x] 4.2 `EpicControllerTest` (standalone MockMvc): status codes 200/201/204, Location header on create, blank-title→400, missing teamId→400

## 5. BDD

- [x] 5.1 Add `be/src/bdd/resources/features/epics.feature` (`@epics`): authenticated create under team→list filtered by teamId→update (team unchanged, modified_at advanced)→delete-guard 409 while a ticket references (insert ticket via repository since E7 API doesn't exist yet)→clean delete→404 after; unknown team create→404; plus 401 without token
- [x] 5.2 Step definitions reusing the existing auth + teams steps for token/team setup

## 6. Gate

- [x] 6.1 `make be-lint` + full backend build green (Spotless, Error Prone/NullAway, JaCoCo 90/90, Cucumber)
