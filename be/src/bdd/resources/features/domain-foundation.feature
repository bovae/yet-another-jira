@domain-foundation
Feature: Domain foundation persistence

  Scenario: Ticket persists with DB-generated id and audit timestamps
    Given a user exists with email "bdd-user@example.com"
    And a team exists with name "bdd-team"
    When a ticket is created for that team by that user with type "bug" and state "new"
    Then the ticket has a non-null DB-generated id
    And the ticket has non-null created_at and modified_at timestamps
    And the ticket type is "bug" and state is "new"
