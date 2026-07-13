# fe-auth-screens

## ADDED Requirements

### Requirement: Authenticated users are redirected off auth screens
When an already-authenticated user navigates to `/login` or `/signup`, the frontend SHALL redirect to `/` instead of rendering the form.

#### Scenario: Login page with active session
- **WHEN** an authenticated user opens `/login` or `/signup`
- **THEN** they are redirected to `/` without seeing the form
