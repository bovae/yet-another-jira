@teams
Feature: Team management — CRUD lifecycle with referential delete guard

  Scenario: Authenticated user creates, lists, renames, and deletes a team with the delete guard enforced
    Given a registered user with email "teams@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "teams@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    When the user creates a team named "Platform"
    Then the response status is 201
    And the response body contains team name "Platform"
    When the user lists teams
    Then the response status is 200
    And the teams list contains "Platform"
    When the user renames the team to "Payments"
    Then the response status is 200
    And the response body contains team name "Payments"
    And the team's modified timestamp is later than at creation
    Given an epic references the team
    When the user deletes the team
    Then the response status is 409
    Given the epic on the team is removed
    When the user deletes the team
    Then the response status is 204
    When the user gets the team
    Then the response status is 404

  Scenario: Team endpoints reject an unauthenticated request
    When an unauthenticated user lists teams
    Then the response status is 401
