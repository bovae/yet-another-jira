# Requirements Document

## Introduction

This document specifies the requirements for **Epic E11 — Frontend foundation (routing, auth context,
design system)** (`008-frontend-foundation`), the first frontend epic for the yet-another-jira Kanban
ticket tracker. E11 builds directly on the backend authentication contract delivered by sign-up
(`003-signup-password-hashing`, "E1"), email verification (`004-email-verification-resend`, "E2"),
login / logout (`005-login-logout-jwt-denylist`, "E3"), and authentication enforcement
(`006-authn-authz-enforcement`, "E4"). After E4, every `/api/v1/**` route other than the four public
auth endpoints is a protected endpoint that demands a valid JWT bearer token and rejects an
unauthenticated request with an RFC 9457 `401` (never a redirect). E11 is the frontend half of that
boundary: it builds the application shell that obtains, carries, and clears that token.

The current frontend is a scaffold (React 19 + TypeScript + Vite + Tailwind v4). The DESIGN.md tokens
are already encoded as Tailwind v4 `@theme` variables in `fe/src/index.css` (with the stock palette
and type scale reset to `initial`), TanStack Query is configured in `fe/src/main.tsx`, and
`@dnd-kit/core` is installed but inert. `fe/src/api/client.ts` already exposes `apiFetch` with a 10s
`AbortController` timeout that attaches an `Authorization: Bearer` header from the `localStorage` key
`accessToken` (`TOKEN_KEY`) when a token is present. `fe/src/App.tsx` currently renders only the mock
`BoardPage` (which reads `GET /api/v1/mock/board`); there is **no router, no auth/session context, and
no shadcn/ui** yet, and the global `401` handling and the in-memory token layer do not exist.

E11 delivers the foundation that every later frontend epic (E12–E15) builds on, with seven behaviors:

1. **Client-side routing.** Introduce a router and register routes for all minimum screens (§10):
   sign-up, email-verification result, login, the Kanban board with team selector, ticket
   create/edit/details, team management, and epic management. The screens themselves are built later
   (E12–E15); E11 registers the route table, the public/protected split, and placeholder destinations,
   and slots the existing mock `BoardPage` in as the board placeholder.
2. **Authentication route guard.** Guard the business routes behind authentication so that an
   unauthenticated visit to a protected route redirects to the login route, while the public auth
   routes (sign-up, login, verification result) stay reachable without a session.
3. **Auth/session context.** Provide a session context that holds the JWT in memory and mirrors it to a
   refresh-safe browser store (which is **not** the system of record — §9) so the session survives a
   page refresh, exposes the authenticated state to the router and guard, and offers actions to start
   and clear a session.
4. **Authenticated API client.** Keep `apiFetch` attaching the bearer token (already implemented) and
   add **global `401` handling**: an authenticated request that receives a `401` clears the session and
   redirects to login, while a `401` from a public auth endpoint (no bearer token) is passed back to the
   caller untouched.
5. **Design system + shadcn/ui.** The Tailwind v4 token layer is already in place; E11 records it,
   bundles the Geist / Inter webfonts that the `font-sans` / `font-mono` tokens already reference (text
   currently falls back to `system-ui`), runs `shadcn init` as the source of accessible primitives, and
   **bridges** shadcn's semantic tokens onto the DESIGN.md tokens (required because the
   `--color-*: initial` reset means un-bridged shadcn classes render nothing).
6. **App shell + header user menu.** Build the application shell with a header carrying a collapsed user
   menu (a shadcn DropdownMenu — the first concrete shadcn consumer) that includes **Log out**; logging
   out calls the logout endpoint, clears the session, and returns the user to login.
7. **Reusable status states.** Provide a reusable loading / empty / success / error state pattern
   (§11) that later screens consume instead of hand-rolling each state.

E11 reuses, rather than re-implements, the foundation already shipped: the `apiFetch` wrapper and its
`TOKEN_KEY` (`accessToken`) `localStorage` key, the configured TanStack Query client, the Tailwind v4
`@theme` token bridge in `fe/src/index.css`, the existing mock `BoardPage` and its loading/error
chrome, and the backend auth contract — `POST /api/v1/auth/login` (returns the access token in the
response body), `GET /api/v1/auth/me` (returns `{ id, email, emailVerified }` for a valid token, `401`
otherwise), and `POST /api/v1/auth/logout` (idempotent `204`).

E11 is **foundation only**. It builds **no** auth form screens — the sign-up, login, and
verification-result forms and the resend action are **Epic E12**; E11 only registers their routes and
renders placeholders. It builds **no** team, epic, ticket, or board feature screens (**E13–E15**), and
it does **not** remove the mock board or wire a real board endpoint (the mock removal and real board are
**E9 / E15**). E11 keeps the mock board reachable as the board-route placeholder so the app shell and
routing can be proven end to end before any feature screen exists.

These requirements are derived from the E11 entry in `requirements/epics-catalog.md` and the product
requirements source `requirements/yet-another-jira.md` (primarily §3 — which endpoints require or skip
authentication; §9 — tokens never in URLs and browser local storage never the system of record; §10 —
the minimum screens that drive the route table; and §11 — security, reliability across refresh,
loading/empty/success/error states, current-desktop-Chrome compatibility, and the maintainability and
testing gates).

## Decisions and Open Questions

The constraints below were under-specified by the source material. Each is captured as a concrete,
testable decision in the requirements that follow. Items marked **[CONFIRM]** change observable
behavior and should be confirmed before the design phase; the requirements encode the recommended
default so review can proceed. Unmarked items record a confirmed or non-behavioral choice.

1. **Router library (Requirement 1). [CONFIRM]** The catalog says "add a router" without naming one;
   the frontend has no routing dependency today. **Decision:** add a single client-side routing library
   and confirm its exact coordinates and API during the design phase per the shared-library rules. The
   requirements fix only the **observable routing behavior** (a route table covering the §10 screens, a
   public/protected split, redirect-to-login on guarded access, and history-based navigation without a
   full page reload), not the library. **Recommended default:** React Router (the de-facto SPA router
   for React, already compatible with the React 19 + Vite stack). *Alternative:* TanStack Router —
   type-safe and same-vendor as the configured TanStack Query, but newer and heavier than this
   foundation needs. The library choice does not change any acceptance criterion below.

2. **Token storage mechanism (Requirement 3, 4). [CONFIRM]** The catalog requires "in memory + a
   refresh-safe store that is not the system of record." **Decision:** the in-memory session value is
   the primary source the running app reads; it is mirrored to the existing `localStorage` key
   `accessToken` (`TOKEN_KEY`) that `apiFetch` already reads, so a refresh rehydrates the session and the
   already-shipped bearer-attachment behavior keeps working unchanged. Browser storage holds **only**
   the credential (a token), never application data, and is never treated as the authority on identity —
   the backend (`GET /api/v1/auth/me`, and any `401`) remains the system of record (§9). **Recommended
   default:** keep `localStorage` (survives both refresh and tab restart, matching the current
   `apiFetch` contract). *Alternative:* `sessionStorage` (cleared on tab close) — more conservative but
   it breaks the existing `apiFetch` `localStorage` read and forces re-login on every new tab.

3. **Scope of global `401` handling (Requirement 4, 5). [CONFIRM]** A `401` must not be treated as a
   session expiry in every case: `POST /api/v1/auth/login` legitimately returns `401` for bad
   credentials (E12), and that must surface to the calling screen, not trigger a redirect loop.
   **Decision:** the global session-expiry handler reacts only to a `401` on a request that **carried a
   bearer token** (i.e., an active session that the server has now rejected); a `401` on a request with
   no `Authorization` header — every public auth endpoint call — is returned to the caller unchanged.
   *Alternative:* treat every `401` as session expiry (rejected — it would hijack login/sign-up failures
   and prevent E12 from rendering credential errors).

4. **Restored-session confirmation against the server (Requirement 3, 5). [CONFIRM]** Because browser
   storage is not the system of record (§9), a token rehydrated from storage on app load is treated as a
   **provisional** session for routing, and its validity is confirmed by the server: the foundation
   confirms the restored session against `GET /api/v1/auth/me`, and a `401` from that confirmation (or
   from any later authenticated request) clears the session and redirects to login. **Recommended
   default:** confirm via `GET /api/v1/auth/me` on load. *Alternative:* trust the stored token until the
   first business request fails (rejected — it would briefly show protected UI for an expired token and
   leans on storage as the authority, contrary to §9).

5. **Authenticated user visiting a public auth route (Requirement 2). [CONFIRM]** **Decision:** when an
   authenticated session exists and the user navigates to the login or sign-up route, the guard redirects
   them to the board route, so a logged-in user is never shown the login form. *Alternative:* allow an
   authenticated user to view the login route (rejected — confusing and lets a stale form re-submit).

6. **shadcn primitive set installed in E11 (Requirement 7). [CONFIRM]** The catalog lists Dialog,
   DropdownMenu, Select, Popover, and Tooltip as primitives "consumed by E13–E15," with the header user
   menu (DropdownMenu) as the only E11 consumer. **Decision:** E11 runs `shadcn init`, establishes the
   semantic-token bridge, and adds **only the DropdownMenu primitive** (the one E11 actually consumes);
   E13–E15 add their own primitives on top of the established bridge as they need them, avoiding unused
   components in the foundation. *Alternative:* add the full set now (rejected — the un-consumed
   primitives would be dead code until their epics land, and each still needs the same one-time bridge
   E11 establishes). The bridge, not the component count, is the reusable deliverable.

7. **Webfont hosting (Requirement 8). [CONFIRM]** The Geist / Inter webfonts must be bundled so text
   renders in the intended typeface instead of the `system-ui` fallback. **Decision:** self-host the
   font files within the frontend bundle and declare them with `@font-face`, so the app needs no
   runtime third-party CDN and stays self-contained under `docker compose up --build` (§2, §11).
   *Alternative:* load from a public font CDN (rejected — adds a runtime external dependency and a
   privacy/availability coupling the self-contained deployment model avoids).

8. **Logout when the logout request fails (Requirement 6). [CONFIRM]** **Decision:** the Log out action
   always clears the local session and returns the user to login, **even if** the `POST
   /api/v1/auth/logout` call fails or times out, so a user can never get stuck appearing logged in
   locally; the server-side denylisting is best-effort from the client's perspective. *Alternative:*
   block the logout until the server confirms (rejected — a network blip would trap the user in an
   authenticated shell).

9. **Mock board as the board placeholder (Requirement 9).** The board route renders the existing mock
   `BoardPage` (`GET /api/v1/mock/board`) as its placeholder so routing and the guard are demonstrable
   end to end. E11 neither removes the mock nor introduces a real board endpoint — that is E9 / E15.
   This is a scope statement, not a behavioral choice, so it is not marked **[CONFIRM]**.

## Glossary

- **Frontend_Foundation**: The complete E11 deliverable taken as a whole: the router and route table, the route guard, the auth/session context, the global `401` handling layered onto the API client, the shadcn/ui primitive layer and its token bridge, the bundled webfonts, the application shell with the header user menu, and the reusable status-state pattern.
- **App_Router**: The client-side router introduced by E11 that maps the browser location to a rendered screen using history-based navigation, without a full document reload between in-app routes.
- **Route_Table**: The set of named routes the App_Router registers, one for each Minimum_Screen, each classified as a Public_Route or a Protected_Route.
- **Minimum_Screens**: The screens required by §10 — the Signup_Screen, the Verification_Result_Screen, the Login_Screen, the Board_Screen, the Ticket_Detail_Screen (create / edit / details), the Team_Management_Screen, and the Epic_Management_Screen.
- **Public_Route**: A route reachable without an Authenticated_Session: the login route, the sign-up route, and the verification-result route, corresponding to the Auth_Public_Endpoints.
- **Protected_Route**: A route that requires an Authenticated_Session: the board route, the ticket create/edit/details routes, the team-management route, and the epic-management route.
- **Route_Guard**: The component that decides, for a Protected_Route, whether the current request has an Authenticated_Session and, if not, redirects to the login route.
- **Login_Route**: The route at which the Login_Screen is (later) rendered, and the redirect target of the Route_Guard for an unauthenticated access to a Protected_Route.
- **Board_Route**: The Protected_Route that renders the Board_Screen; in E11 its destination is the existing Mock_Board_Page placeholder.
- **Placeholder_Screen**: A minimal route destination rendered by E11 for a Minimum_Screen whose real implementation belongs to a later epic (E12–E15), present so the Route_Table and Route_Guard are complete and testable.
- **Mock_Board_Page**: The existing `fe/src/pages/BoardPage.tsx` that reads `GET /api/v1/mock/board`, used unchanged by E11 as the Board_Route placeholder.
- **Auth_Session_Context**: The React context introduced by E11 that holds the Session_State, exposes it to the App_Router and Route_Guard, and provides the Start_Session and Clear_Session actions.
- **Session_State**: The current authentication state exposed by the Auth_Session_Context: whether an Authenticated_Session exists and, when confirmed, the Current_User identity.
- **Authenticated_Session**: The state in which the Frontend_Foundation holds an Access_Token and treats the user as logged in for routing purposes.
- **Access_Token**: The signed JWT bearer credential issued by `POST /api/v1/auth/login` and presented on the `Authorization: Bearer` header for authenticated requests.
- **Token_Store**: The storage strategy for the Access_Token — an in-memory value (the primary source the running app reads) mirrored to a Refresh_Safe_Store.
- **Refresh_Safe_Store**: The browser `localStorage` key `accessToken` (`TOKEN_KEY`) that `apiFetch` already reads, holding only the Access_Token so an Authenticated_Session survives a page refresh; never the system of record for identity (§9).
- **Start_Session**: The Auth_Session_Context action that records a supplied Access_Token in the Token_Store and transitions the Session_State to an Authenticated_Session.
- **Clear_Session**: The Auth_Session_Context action that removes the Access_Token from the Token_Store (in-memory and Refresh_Safe_Store) and transitions the Session_State to unauthenticated.
- **Api_Client**: The existing `apiFetch` wrapper in `fe/src/api/client.ts` (10s `AbortController` timeout; attaches `Authorization: Bearer` from the Refresh_Safe_Store when a token is present), extended by E11 with global `401` handling.
- **Session_Expiry_Handler**: The global behavior added to the Api_Client that, on a `401` response to a request that carried a bearer token, invokes Clear_Session and redirects to the Login_Route.
- **Current_User_Endpoint**: The backend endpoint `GET /api/v1/auth/me` that returns `{ id, email, emailVerified }` for a valid Access_Token and `401` otherwise.
- **Logout_Endpoint**: The backend endpoint `POST /api/v1/auth/logout` that revokes the presented Access_Token and responds with `204` (idempotent).
- **Auth_Public_Endpoints**: The backend endpoints reachable without authentication — `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `GET|POST /api/v1/auth/verify`, and `POST /api/v1/auth/verification/resend`.
- **Current_User**: The identity `{ id, email, emailVerified }` returned by the Current_User_Endpoint, surfaced through the Session_State once a session is confirmed.
- **App_Shell**: The persistent application chrome that wraps the Protected_Route content, including the Header.
- **Header**: The top region of the App_Shell that contains the User_Menu.
- **User_Menu**: The collapsed user menu in the Header, implemented as a shadcn DropdownMenu, containing the Logout_Action.
- **Logout_Action**: The Header control that invokes the Logout_Endpoint, runs Clear_Session, and navigates to the Login_Route.
- **Design_Tokens**: The DESIGN.md design language transcribed as Tailwind v4 `@theme` variables in `fe/src/index.css` (colors, typography, spacing, radius, elevation), the single source of visual values for the frontend; the stock palette and type scale are reset to `initial`.
- **Shadcn_Primitive_Layer**: The shadcn/ui setup introduced by E11 (`shadcn init` plus the DropdownMenu primitive) that supplies accessible interactive primitives for E11 and later epics.
- **Token_Bridge**: The mapping of shadcn's semantic tokens (`--background`, `--foreground`, `--primary`, `--border`, `--ring`, `--radius`) onto the Design_Tokens, required because the `--color-*: initial` reset leaves un-bridged shadcn classes rendering nothing.
- **Webfont_Bundle**: The self-hosted Geist and Inter (and Geist Mono) font files declared with `@font-face`, matching the `font-sans` / `font-mono` Design_Tokens.
- **Status_State_Pattern**: The reusable presentation pattern for the loading, empty, success, and error states (§11) consumed by later screens.
- **Lint_Gate**: The frontend ESLint check run by `npm run lint` (via `make fe-lint`) together with the Prettier `format:check`.
- **Typecheck_Gate**: The TypeScript `npm run typecheck` (`tsc --noEmit` over the app and e2e configs) and the `vite build`.
- **Vitest_Suite**: The frontend unit/component test suite run by `npm test` (`vitest run`).

## Requirements

### Requirement 1: Client-side routing for the minimum screens

**User Story:** As a user, I want the application to present distinct screens for each part of the product at stable in-app locations, so that I can navigate the app and bookmark or refresh a screen without a full page reload. (§10, §11)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL introduce an App_Router that renders a screen based on the current browser location and navigates between in-app routes without triggering a full document reload.
2. THE App_Router SHALL register a Route in the Route_Table for each Minimum_Screen: the Login_Screen, the Signup_Screen, the Verification_Result_Screen, the Board_Screen, the Ticket_Detail_Screen create / edit / details views, the Team_Management_Screen, and the Epic_Management_Screen.
3. WHERE a Route in the Route_Table targets a Minimum_Screen whose real implementation belongs to a later epic, THE App_Router SHALL render a Placeholder_Screen for that Route.
4. THE App_Router SHALL render the existing Mock_Board_Page as the destination of the Board_Route.
5. WHEN the browser location matches a registered Route, THE App_Router SHALL render that Route's destination within the App_Shell for a Protected_Route and without the App_Shell for a Public_Route.
6. WHEN the browser location matches no registered Route, THE App_Router SHALL redirect to the Board_Route if an Authenticated_Session exists and to the Login_Route otherwise.

### Requirement 2: Authentication route guard

**User Story:** As a security owner, I want business routes reachable only by an authenticated user, so that protected screens are never shown to a visitor without a session. (§3, §11)

#### Acceptance Criteria

1. WHEN a request without an Authenticated_Session navigates to a Protected_Route, THE Route_Guard SHALL redirect to the Login_Route and SHALL NOT render the Protected_Route destination.
2. WHEN a request with an Authenticated_Session navigates to a Protected_Route, THE Route_Guard SHALL render that Protected_Route destination.
3. THE Route_Guard SHALL treat the Board_Route, the Ticket_Detail_Screen routes, the Team_Management_Screen route, and the Epic_Management_Screen route as Protected_Routes.
4. THE Route_Guard SHALL permit access to the Login_Route, the Signup_Screen route, and the Verification_Result_Screen route without an Authenticated_Session.
5. WHEN a request with an Authenticated_Session navigates to the Login_Route or the Signup_Screen route, THE Route_Guard SHALL redirect to the Board_Route.

### Requirement 3: Auth/session context and token storage

**User Story:** As a logged-in user, I want my session to persist across a page refresh without the browser store being trusted as the source of truth, so that I stay signed in on reload while the server remains the authority on my identity. (§9, §11)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL provide an Auth_Session_Context that exposes the Session_State and provides the Start_Session and Clear_Session actions.
2. WHEN Start_Session is invoked with an Access_Token, THE Auth_Session_Context SHALL record that Access_Token in the in-memory Token_Store and in the Refresh_Safe_Store and SHALL transition the Session_State to an Authenticated_Session.
3. WHEN Clear_Session is invoked, THE Auth_Session_Context SHALL remove the Access_Token from the in-memory Token_Store and from the Refresh_Safe_Store and SHALL transition the Session_State to unauthenticated.
4. WHEN the application initializes and the Refresh_Safe_Store holds an Access_Token, THE Auth_Session_Context SHALL restore a provisional Authenticated_Session from that Access_Token.
5. WHERE a provisional Authenticated_Session is restored from the Refresh_Safe_Store, THE Auth_Session_Context SHALL confirm it against the Current_User_Endpoint and SHALL populate the Session_State Current_User from a successful confirmation.
6. IF the Current_User_Endpoint confirmation of a restored session returns HTTP status `401`, THEN THE Auth_Session_Context SHALL invoke Clear_Session.
7. THE Frontend_Foundation SHALL store no application data other than the Access_Token in the Refresh_Safe_Store and SHALL NOT treat the Refresh_Safe_Store as the system of record for the Current_User identity.

### Requirement 4: Authenticated API client

**User Story:** As a developer, I want every API request to carry the current bearer token automatically and keep tokens out of URLs, so that authenticated calls work without each call site re-implementing auth. (§3, §9)

#### Acceptance Criteria

1. WHEN the Api_Client issues a request and the Token_Store holds an Access_Token, THE Api_Client SHALL attach an `Authorization: Bearer` header carrying that Access_Token.
2. WHEN the Api_Client issues a request and the Token_Store holds no Access_Token, THE Api_Client SHALL issue the request without an `Authorization` header.
3. THE Api_Client SHALL place the Access_Token only in the `Authorization` request header and SHALL NOT place the Access_Token in any request URL or query parameter.
4. THE Api_Client SHALL preserve the existing request timeout that aborts a request that exceeds 10 seconds.

### Requirement 5: Global 401 session-expiry handling

**User Story:** As a logged-in user whose session has expired, I want the app to return me to login automatically when the server rejects my token, so that I am not left interacting with a broken authenticated screen. (§3, §11)

#### Acceptance Criteria

1. WHEN a request that carried an `Authorization: Bearer` header receives an HTTP status `401` response, THE Session_Expiry_Handler SHALL invoke Clear_Session and SHALL redirect to the Login_Route.
2. WHEN a request that carried no `Authorization` header receives an HTTP status `401` response, THE Session_Expiry_Handler SHALL return that response to the calling code without invoking Clear_Session and without redirecting.
3. WHEN the Session_Expiry_Handler redirects to the Login_Route after a `401`, THE Frontend_Foundation SHALL leave no Access_Token in the in-memory Token_Store or the Refresh_Safe_Store.
4. THE Session_Expiry_Handler SHALL apply uniformly to every authenticated request made through the Api_Client, regardless of the calling screen.

### Requirement 6: Application shell and header user menu

**User Story:** As a logged-in user, I want a header with a user menu that lets me log out, so that I can end my session from anywhere in the authenticated app. (§10, §11; §15 wireframes — the collapsed user menu includes Log out)

#### Acceptance Criteria

1. THE App_Shell SHALL render a Header containing a collapsed User_Menu for every Protected_Route.
2. THE User_Menu SHALL be implemented with the shadcn DropdownMenu primitive from the Shadcn_Primitive_Layer and SHALL include a Logout_Action.
3. WHEN the user opens the User_Menu, THE User_Menu SHALL reveal the Logout_Action.
4. WHEN the user activates the Logout_Action, THE Logout_Action SHALL send a request to the Logout_Endpoint, SHALL invoke Clear_Session, and SHALL navigate to the Login_Route.
5. IF the request to the Logout_Endpoint fails or exceeds the Api_Client timeout, THEN THE Logout_Action SHALL still invoke Clear_Session and navigate to the Login_Route.

### Requirement 7: shadcn/ui primitive layer and design-token bridge

**User Story:** As a frontend developer, I want shadcn/ui set up and re-themed to the project's design tokens, so that later epics can use accessible primitives that render in the project's visual language. (§11; E11 Definition of Done)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL introduce a Shadcn_Primitive_Layer by running the shadcn initialization and adding the DropdownMenu primitive.
2. THE Frontend_Foundation SHALL define a Token_Bridge mapping shadcn's semantic tokens `--background`, `--foreground`, `--primary`, `--border`, `--ring`, and `--radius` onto the corresponding Design_Tokens.
3. WHEN a Shadcn_Primitive_Layer component renders, THE Token_Bridge SHALL resolve that component's semantic-token colors, border, focus ring, and radius to Design_Tokens values rather than leaving them unstyled.
4. THE Shadcn_Primitive_Layer SHALL source every color, radius, and typography value it renders from the Design_Tokens and SHALL NOT introduce a color, radius, or font value that is absent from the Design_Tokens.

### Requirement 8: Design system tokens and webfont bundling

**User Story:** As a user, I want the interface to render in the intended typeface using the project's design tokens, so that the app looks consistent and matches the design language rather than falling back to system fonts. (§11)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL build all E11 user interface from the Design_Tokens defined in `fe/src/index.css` and SHALL NOT use an off-token color, radius, or type value for anything the Design_Tokens cover.
2. THE Frontend_Foundation SHALL include a self-hosted Webfont_Bundle for the Geist, Geist Mono, and Inter families referenced by the `font-sans` and `font-mono` Design_Tokens.
3. THE Frontend_Foundation SHALL declare the Webfont_Bundle with `@font-face` so that the bundled fonts load from the application's own assets without a runtime third-party font service.
4. WHEN the application renders text under the `font-sans` or `font-mono` Design_Token, THE Frontend_Foundation SHALL apply the bundled Geist / Geist Mono / Inter typeface ahead of the `system-ui` fallback.

### Requirement 9: Reusable status-state pattern and board placeholder

**User Story:** As a user, I want consistent loading, empty, success, and error feedback across the app, so that I always know what the application is doing. (§11)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL provide a reusable Status_State_Pattern that presents a loading state, an empty state, a success state, and an error state.
2. WHEN the Status_State_Pattern presents a loading state, THE Status_State_Pattern SHALL expose it with an accessible `status` role, consistent with the existing Mock_Board_Page loading chrome.
3. WHEN the Status_State_Pattern presents an error state, THE Status_State_Pattern SHALL expose it with an accessible `alert` role.
4. THE Status_State_Pattern SHALL render every state from the Design_Tokens.
5. THE Frontend_Foundation SHALL keep the Mock_Board_Page reachable at the Board_Route as a placeholder and SHALL NOT remove the mock board or introduce a real board endpoint.

### Requirement 10: Scope boundary with later frontend epics

**User Story:** As a maintainer, I want E11 to deliver only the foundation and not the feature screens, so that the app shell can land and be verified before the auth and feature screens are built. (§10)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL implement no sign-up, login, or verification-result form and no verification-resend action, rendering a Placeholder_Screen for each Auth_Public_Endpoints route and deferring those forms to Epic E12.
2. THE Frontend_Foundation SHALL implement no team, epic, ticket, or board feature screen beyond the Mock_Board_Page placeholder, deferring those screens to Epics E13 through E15.
3. THE Frontend_Foundation SHALL register the Route_Table entry and apply the Route_Guard classification for every Minimum_Screen route whose screen is deferred, so that the routing and guard are complete even where the screen is a Placeholder_Screen.

### Requirement 11: Compatibility and reliability

**User Story:** As a QA engineer, I want the app to run correctly on a current desktop Chrome and to survive a refresh, so that the mandated browser works and persisted sessions are not lost on reload. (§9, §11)

#### Acceptance Criteria

1. THE Frontend_Foundation SHALL function on a current desktop version of Chrome.
2. THE Frontend_Foundation SHALL require no legacy-browser support beyond a current desktop version of Chrome in its build target.
3. WHEN a user with an Authenticated_Session refreshes the browser on a Protected_Route, THE Frontend_Foundation SHALL restore the Authenticated_Session from the Refresh_Safe_Store and SHALL keep the user on that Protected_Route, subject to the Current_User_Endpoint confirmation in Requirement 3.
4. WHEN a user without an Authenticated_Session refreshes the browser on a Protected_Route, THE Route_Guard SHALL redirect to the Login_Route.

### Requirement 12: Quality gates

**User Story:** As a maintainer, I want the foundation verified against the project's frontend quality gates, so that routing, the API client, and the user menu are proven before the feature epics build on them. (§11; E11 Definition of Done)

#### Acceptance Criteria

1. THE Vitest_Suite SHALL include a Route_Guard test asserting that navigating to a Protected_Route without an Authenticated_Session redirects to the Login_Route and that navigating with an Authenticated_Session renders the Protected_Route destination.
2. THE Vitest_Suite SHALL include an Api_Client test asserting that a request made with an Access_Token in the Token_Store carries the `Authorization: Bearer` header and that a request receiving a `401` while carrying a bearer token invokes Clear_Session.
3. THE Vitest_Suite SHALL include a User_Menu test asserting that opening the User_Menu reveals the Logout_Action and that activating the Logout_Action invokes the Logout_Endpoint request and Clear_Session.
4. WHEN the Lint_Gate runs, THE Frontend_Foundation SHALL complete `npm run lint` and the Prettier `format:check` without errors.
5. WHEN the Typecheck_Gate runs, THE Frontend_Foundation SHALL complete `npm run typecheck` and `vite build` without errors.
6. IF the Lint_Gate reports an error, or IF the Typecheck_Gate reports an error, or IF a Vitest_Suite test fails, THEN the frontend build SHALL fail.
