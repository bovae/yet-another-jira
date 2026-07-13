@comments
Feature: Comments — add and list under a ticket, oldest-first, ticket modified_at untouched

  Background:
    Given a registered user with email "comments@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "comments@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token

  Scenario: Add comments, list them oldest first, and leave the ticket's modified_at untouched
    Given a team with a ticket for commenting exists
    When the user adds a comment "First comment" to the ticket
    Then the response status is 201
    When the user adds a comment "Second comment" to the ticket
    Then the response status is 201
    When the user lists the ticket's comments
    Then the response status is 200
    And the comments are returned oldest first: "First comment" then "Second comment"
    And the ticket's modified timestamp is unchanged after commenting

  Scenario: A blank comment body is rejected
    Given a team with a ticket for commenting exists
    When the user adds a blank comment to the ticket
    Then the response status is 400

  Scenario: Commenting on an unknown ticket is rejected
    When the user adds a comment "Ghost" to a non-existent ticket
    Then the response status is 404
