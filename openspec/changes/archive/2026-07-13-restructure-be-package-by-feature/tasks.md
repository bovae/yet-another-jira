# Tasks — Restructure Backend to Package-by-Feature

## 1. Move feature controllers and DTOs (main sources)

- [x] 1.1 Move `TicketController` + `TicketCreateRequest`, `TicketUpdateRequest`, `TicketStateChangeRequest`, `TicketResponse` from `web/` into `tickets/`; fix package/imports
- [x] 1.2 Move `BoardController` + `BoardResponse`, `BoardColumnResponse`, `BoardCardResponse` into `board/`; fix package/imports
- [x] 1.3 Move `CommentController` + `CommentCreateRequest`, `CommentResponse` into `comments/`; fix package/imports
- [x] 1.4 Move `EpicController` + `EpicCreateRequest`, `EpicUpdateRequest`, `EpicResponse` into `epics/`; fix package/imports
- [x] 1.5 Move `TeamController` + `TeamRequest`, `TeamResponse` into `teams/`; fix package/imports
- [x] 1.6 Move `AuthController` into `auth/`; move DTOs to sub-features: `LoginRequest`/`LoginResponse` → `auth/login/`, `SignupRequest`/`SignupResponse` → `auth/signup/`, `MeResponse` → `auth/me/`, `VerifyRequest`/`VerifyResponse`/`ResendRequest`/`ResendResponse` → `auth/verification/`; fix package/imports
- [x] 1.7 Delete now-empty `web/controller/` and `web/dto/`; confirm `web/` retains only `filter/` and `error/`

## 2. Mirror moves in test sources

- [x] 2.1 Move controller tests from `test/.../web/controller/` next to their features: `TicketControllerTest` → `tickets/`, `CommentControllerTest` → `comments/`, `EpicControllerTest` → `epics/`, `TeamControllerTest` → `teams/`, `AuthControllerTest` → `auth/`; fix packages/imports
- [x] 2.2 Decide placement for `AuthEnforcementSliceTest` (cross-feature security slice → `security/` or keep under `web/`); move and fix imports
- [x] 2.3 Update DTO imports in remaining unit tests, `src/it` integration tests, and BDD step classes

## 3. Tighten visibility

- [x] 3.1 Make controllers and feature services package-private where no other package references them (per design D3); leave DTOs public where tests/BDD in other packages need them

## 4. Validate

- [x] 4.1 Run `make fmt`, then `make be-lint` and `make be-test`; fix any fallout until green
