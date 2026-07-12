@board
Feature: Board read — per-team Kanban board with 5 ordered columns and server-side filters

  Scenario: Board groups tickets into 5 ordered columns with epic titles and recency ordering
    Given a registered user with email "board@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "board@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    Given a board team named "Platform" exists
    And an epic named "Onboarding" exists on the board team
    And a ticket "Fix login flow" of type "bug" in state "new" with no epic
    And a ticket "Add onboarding wizard" of type "feature" in state "new" on epic "Onboarding"
    And a ticket "Refactor auth" of type "fix" in state "in_progress" with no epic
    When the user requests the board
    Then the response status is 200
    And the board has 5 columns in workflow order
    And column "new" contains cards: "Add onboarding wizard, Fix login flow"
    And column "in_progress" contains cards: "Refactor auth"
    And column "done" is empty
    And the card "Add onboarding wizard" shows epic title "Onboarding"
    And the card "Fix login flow" has no epic
    When the user modifies the ticket "Fix login flow"
    And the user requests the board
    Then column "new" contains cards: "Fix login flow, Add onboarding wizard"

  Scenario: Filters narrow the board and combine with AND
    Given a registered user with email "board@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "board@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    Given a board team named "Platform" exists
    And an epic named "Onboarding" exists on the board team
    And a ticket "Fix login flow" of type "bug" in state "new" on epic "Onboarding"
    And a ticket "Login rate limit" of type "feature" in state "new" on epic "Onboarding"
    And a ticket "Unrelated crash" of type "bug" in state "new" with no epic
    When the user requests the board filtered by type "bug"
    Then column "new" contains cards: "Unrelated crash, Fix login flow"
    When the user requests the board filtered by epic "Onboarding"
    Then column "new" contains cards: "Login rate limit, Fix login flow"
    When the user requests the board with search "login"
    Then column "new" contains cards: "Login rate limit, Fix login flow"
    When the user requests the board filtered by type "bug" and epic "Onboarding" and search "login"
    Then column "new" contains cards: "Fix login flow"

  Scenario: Title search is a case-insensitive substring and treats wildcards literally
    Given a registered user with email "board@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "board@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    Given a board team named "Platform" exists
    And a ticket "Reach 100% coverage" of type "feature" in state "new" with no epic
    And a ticket "Reach 100 users" of type "feature" in state "new" with no epic
    And a ticket "Fix Login flow" of type "bug" in state "new" with no epic
    When the user requests the board with search "100%"
    Then column "new" contains cards: "Reach 100% coverage"
    When the user requests the board with search "login"
    Then column "new" contains cards: "Fix Login flow"

  Scenario: Board rejects an invalid type code and an unknown team
    Given a registered user with email "board@example.com" and password "StrongPass123!"
    And the user's email is verified
    When the user logs in with email "board@example.com" and password "StrongPass123!"
    Then the response status is 200
    And the response body contains an access token
    Given a board team named "Platform" exists
    When the user requests the board filtered by type "banana"
    Then the response status is 400
    When the user requests the board of an unknown team
    Then the response status is 404

  Scenario: Unauthenticated request is rejected
    When an unauthenticated user requests the board of any team
    Then the response status is 401
