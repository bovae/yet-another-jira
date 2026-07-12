# Tasks — add-teams-crud

## 1. Repository & DTOs

- [x] 1.1 Add `existsByName(String name)` and `existsByNameAndIdNot(String name, UUID id)` to `TeamRepository` (citext makes both case-insensitive)
- [x] 1.2 Add `TeamRequest` record (`@NotBlank String name`) and `TeamResponse` record (id, name, createdAt, modifiedAt; static `from(Team)`) in `web/dto`

## 2. Service

- [x] 2.1 Create `com.bovae.yaj.teams.TeamService` (`@Transactional`, injects `TeamRepository`, `EpicRepository`, `TicketRepository`): `list()`, `get(id)` (404), `create(name)` — trim, empty→`ValidationException`, duplicate→`ConflictException`
- [x] 2.2 `rename(id, name)` — same name rules excluding self, 404 on unknown id; `modified_at` advanced by entity `@UpdateTimestamp` on flush (see design decision 5)
- [x] 2.3 `delete(id)` — 404 on unknown id; `ConflictException` with clear message when `existsByTeamId` on epics or tickets; otherwise delete

## 3. Controller

- [x] 3.1 Create `TeamController` (`/api/v1/teams`): `GET` list (200), `POST` create (201 + Location), `GET /{id}` (200), `PUT /{id}` (200), `DELETE /{id}` (204); delegate everything to the service

## 4. Unit Tests

- [x] 4.1 `TeamServiceTest` (Mockito): create trims; `@ParameterizedTest` blank names→ValidationException; duplicate create/rename→ConflictException; rename-to-own-name succeeds; rename trims/applies new name; unknown id→NotFoundException (get/rename/delete); delete with epics→409, with tickets→409, clean→repository delete invoked (rename `modified_at` bump verified in BDD — persistence behavior, see design decision 5)
- [x] 4.2 `TeamControllerTest` (standalone MockMvc): status codes 200/201/204 and blank-name→400 via `@NotBlank`

## 5. BDD

- [x] 5.1 Add `be/src/bdd/resources/features/teams.feature` (`@teams`): authenticated create→list→rename→delete-guard (409 while an epic exists — insert reference directly or via repository since E6 API doesn't exist yet)→clean delete→404 after; plus 401 without token
- [x] 5.2 Step definitions reusing the existing auth steps for token setup

## 6. Gate

- [x] 6.1 `make be-lint` + full backend build green (Spotless, Error Prone/NullAway, JaCoCo 90/90, Cucumber)
