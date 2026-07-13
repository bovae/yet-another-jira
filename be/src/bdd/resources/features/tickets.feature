@tickets
Feature: Ticket management — CRUD, validation, and change-only modified_at semantics

  Background:
    Given a registered user with email "tickets@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "tickets@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token

  Scenario: Full ticket lifecycle with modified_at semantics and cascading delete
    Given a ticket team named "Platform" exists
    When the user creates a ticket titled "Fix login" under the team
    Then the response status is 201
    When the user updates the ticket title to "Fix login button"
    Then the response status is 200
    And the ticket's modified timestamp advanced since the last change
    When the user re-saves the ticket with unchanged values
    Then the response status is 200
    And the ticket's modified timestamp is unchanged since the last change
    When the user changes the ticket state to "in_progress"
    Then the response status is 200
    And the ticket's current state is "in_progress"
    And the ticket's modified timestamp advanced since the last change
    When the user changes the ticket state to "in_progress"
    Then the response status is 200
    And the ticket's modified timestamp is unchanged since the last change
    Given the ticket has a comment
    When the user deletes the ticket
    Then the response status is 204
    And the ticket's comments no longer exist
    When the user gets the ticket
    Then the response status is 404

  Scenario: A ticket cannot reference an epic from another team
    Given a ticket team named "Alpha" exists
    And a second ticket team named "Beta" exists
    And an epic exists under the second team
    When the user creates a ticket under the first team referencing the second team's epic
    Then the response status is 400
