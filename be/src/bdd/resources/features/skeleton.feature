@skeleton
Feature: Skeleton boot verification
  The application skeleton boots successfully with Testcontainers-provisioned
  Postgres and Valkey, executes Liquibase migrations, and serves the API.

  Background:
    Given the application is running

  # === Health Check ===
  Scenario: Application context starts successfully
    Then the health endpoint returns status 200

  # === Database Tables ===
  Scenario: Freshly migrated database has required tables with zero rows
    Then the following tables exist with zero rows:
      | table_name |
      | users      |
      | teams      |
      | epics      |
      | tickets    |
      | comments   |

  # === Board ===
  Scenario: The board returns five columns in canonical workflow order
    Given a registered and verified user with email "skeleton@example.com" and password "StrongPass123!"
    And the user is logged in with email "skeleton@example.com" and password "StrongPass123!"
    And the authenticated client has created a team named "Skeleton Team"
    When the authenticated client requests that team's board
    Then the response contains exactly 5 columns
    And the columns are in workflow order: new, ready_for_implementation, in_progress, ready_for_acceptance, done

  # === Correlation-Id ===
  Scenario: Correlation-id is echoed when provided in request header
    When the client sends a request with X-Correlation-Id "123e4567-e89b-12d3-a456-426614174000"
    Then the response header X-Correlation-Id is "123e4567-e89b-12d3-a456-426614174000"

  Scenario: Correlation-id is generated when not provided in request header
    When the client sends a request without X-Correlation-Id
    Then the response header X-Correlation-Id is non-blank
