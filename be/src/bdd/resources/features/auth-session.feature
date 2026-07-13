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

  Scenario: Protected endpoint rejects a request with no access token
    When the user requests current-user without an access token
    Then the response status is 401

  Scenario: Authenticated request to an unknown route returns a problem+json 404
    Given a registered user with email "notfound@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "notfound@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    When the user requests an unknown route with the access token
    Then the response status is 404
    And the response content type is "application/problem+json"
    And the response body contains members: status, title, correlationId, timestamp

  Scenario Outline: Login is rejected for <case>
    Given a registered user with email "<email>" and password "StrongPass123!"
    And the email-verified flag for "<email>" is "<verified>"
    When the user logs in with email "<email>" and password "<password>"
    Then the response status is <status>

    Examples:
      | case                      | email                  | verified | password       | status |
      | wrong password (verified) | wrongpass@example.com  | true     | WrongPass999!  | 401    |
      | unverified account        | unverified@example.com | false    | StrongPass123! | 403    |

  Scenario: Login rate limit returns 429 with Retry-After
    Given a registered user with email "loginrl@example.com" and password "StrongPass123!"
    When the user attempts to log in 6 times with email "loginrl@example.com" and password "WrongPass999!"
    Then the response status is 429
    And the response includes a Retry-After header
