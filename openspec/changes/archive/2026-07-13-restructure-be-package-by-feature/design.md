# Design — Restructure Backend to Package-by-Feature

## Context

The backend currently splits each feature across five packages: `<feature>/` holds only the service, while controllers sit in `web/controller/`, DTOs in `web/dto/`, entities in `domain/model/`, and repositories in `domain/repository/`. Only `auth/` is fully feature-organized (sub-packages `jwt`, `login`, `logout`, `me`, `signup`, `token`, `verification`).

Import analysis constrains the design:

- **Repositories are cross-feature.** `TicketRepository` is used by 5 features (board, comments, epics, teams, tickets); `EpicRepository` by 4. Entities form one connected JPA graph (Ticket→Epic→Team→User).
- **Feature services never import each other.** The web-tier slice (controller + DTOs + service) is cleanly vertical per feature.

## Goals / Non-Goals

**Goals:**

- One package per feature owning its controller, service, and DTOs.
- `web/` reduced to cross-cutting HTTP infrastructure (filters, global error handling).
- Package-private visibility where co-location now allows it.
- Test/IT/BDD packages mirror the new structure.

**Non-Goals:**

- No behavior, route, or contract changes.
- No splitting of `domain/` into feature packages — entities and repositories stay a shared kernel (cross-feature usage makes per-feature ownership worse, not better).
- No changes to `config/`, `error/`, `security/`, `support/`.
- No renaming of classes; package moves only.

## Decisions

### D1: Feature packages own the vertical web slice; `domain/` stays shared kernel

Move controllers and DTOs into feature packages; keep entities, enums, and repositories in `domain/`.

- *Alternative — pure layered*: would dissolve `auth/`, the best-structured and most complex part of the codebase, backwards. Rejected.
- *Alternative — full feature packages including entities/repos*: every feature would import the internals of other features (`tickets` → `epics` → `teams` → `auth` for User). The JPA graph is one unit; splitting it creates worse coupling than a declared shared kernel. Rejected.

### D2: Target layout

```
com.bovae.yaj
├── auth/                AuthController (spans sub-features)
│   ├── login/           + LoginRequest, LoginResponse
│   ├── signup/          + SignupRequest, SignupResponse
│   ├── me/              + MeResponse
│   ├── verification/    + VerifyRequest, VerifyResponse, ResendRequest, ResendResponse
│   ├── jwt/  logout/  token/        (unchanged)
├── board/               BoardController, BoardService, BoardResponse, BoardColumnResponse, BoardCardResponse
├── comments/            CommentController, CommentService, CommentCreateRequest, CommentResponse
├── epics/               EpicController, EpicService, EpicCreateRequest, EpicUpdateRequest, EpicResponse
├── teams/               TeamController, TeamService, TeamRequest, TeamResponse
├── tickets/             TicketController, TicketService, TicketCreateRequest, TicketUpdateRequest,
│                        TicketStateChangeRequest, TicketResponse
├── domain/              model/, enums/, repository/ — shared kernel (unchanged)
├── web/
│   ├── filter/          CorrelationIdFilter, MdcCleanupFilter (unchanged)
│   └── error/           GlobalExceptionHandler, ProblemDetailFactory, ProblemAuthenticationEntryPoint (unchanged)
├── config/  error/  security/  support/   (unchanged)
```

`AuthController` sits at `auth/` root because it fronts all auth sub-features; each DTO lands in the sub-feature that consumes it (mirrors where the corresponding service already lives).

### D3: Visibility tightening is best-effort, not exhaustive

After moves, drop `public` where a class is referenced only within its package (typically controllers and services). DTOs referenced by BDD/IT tests in other packages stay public. Spring handles package-private `@RestController`/`@Service` beans fine.

- *Alternative — leave everything public*: loses the main encapsulation win of the move for one keyword per class. Rejected.

### D4: Single mechanical commit, IDE-style move semantics

Whole restructure lands as one commit: move files, update package declarations and imports, run `make fmt`. No incremental per-feature commits — a half-moved tree is the worst of both structures.

## Risks / Trade-offs

- [Merge conflicts with in-flight branches touching moved files] → land immediately after current work is merged; the change is import-only so conflicts resolve mechanically.
- [Missed import updates in tests/BDD steps] → `make be-lint` + `make be-test` compile everything (unit, BDD, IT); compilation failure catches any miss.
- [Git history readability across renames] → `git log --follow` handles pure moves; avoid content edits beyond package/import lines and visibility keywords in the same commit.

## Migration Plan

1. Move files per D2 mapping; update `package`/`import` statements.
2. Mirror moves in `src/test`, `src/it`, and BDD step imports.
3. Tighten visibility per D3.
4. `make fmt`, then `make be-lint` and `make be-test` as the gate.

Rollback: revert the single commit.

## Open Questions

None.
