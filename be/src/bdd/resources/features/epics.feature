@epics
Feature: Epic management — team-scoped CRUD with referential delete guard

  Background:
    Given a registered user with email "epics@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "epics@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token

  Scenario: Authenticated user creates, lists, updates, and deletes an epic with the delete guard enforced
    Given a team named "Platform" exists
    When the user creates an epic titled "Payments" under the team
    Then the response status is 201
    And the response body contains epic title "Payments"
    When the user lists epics filtered by the team
    Then the response status is 200
    And the epics list contains "Payments"
    When the user updates the epic to title "Billing"
    Then the response status is 200
    And the response body contains epic title "Billing"
    And the epic's team is unchanged
    And the epic's modified timestamp is later than at creation
    Given a ticket references the epic
    When the user deletes the epic
    Then the response status is 409
    Given the ticket on the epic is removed
    When the user deletes the epic
    Then the response status is 204
    When the user gets the epic
    Then the response status is 404

  Scenario: Creating an epic under an unknown team is rejected
    When the user creates an epic under a non-existent team
    Then the response status is 404
