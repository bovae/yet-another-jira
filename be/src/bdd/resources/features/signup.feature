@auth
Feature: Signup endpoint

  Scenario: Valid signup creates an unverified account
    Given a new user with email "new-user@example.com" and password "SecureP@ss1"
    When the user submits a signup request
    Then the response status is 201
    And the response body contains the email "new-user@example.com"
    And the response body shows emailVerified is false
    And the user exists in the database with email "new-user@example.com" and email_verified false

  Scenario: Signing up with an already-registered email is rejected
    Given a new user with email "dup@example.com" and password "SecureP@ss1"
    When the user submits a signup request
    Then the response status is 201
    When the user submits a signup request
    Then the response status is 409
