Feature: Protected API endpoints reject unauthenticated requests

  # Collapses the former per-feature "reject an unauthenticated request" scenarios into one
  # outline. AuthEnforcementSliceTest owns the full endpoint/method matrix; this pins the
  # 401-by-default behavior end-to-end through the running security filter.
  Scenario Outline: <method> <endpoint> without a token is rejected
    When an unauthenticated user requests "<method>" "<endpoint>"
    Then the response status is 401

    Examples:
      | method | endpoint                                                      |
      | GET    | /api/v1/teams                                                 |
      | GET    | /api/v1/epics                                                 |
      | GET    | /api/v1/tickets                                               |
      | GET    | /api/v1/teams/11111111-1111-1111-1111-111111111111/board      |
      | GET    | /api/v1/tickets/11111111-1111-1111-1111-111111111111/comments |
