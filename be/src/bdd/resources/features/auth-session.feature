@auth
Feature: Auth session — login, current user, and logout

  Scenario: Verified user can login, access current-user, and logout invalidates token
    Given a registered user with email "session@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "session@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    When the user requests current-user with the access token
    Then the response status is 200
    And the response body contains email "session@example.com"
    And the response body contains emailVerified true
    When the user logs out with the access token
    Then the response status is 204
    When the user requests current-user with the access token
    Then the response status is 401
