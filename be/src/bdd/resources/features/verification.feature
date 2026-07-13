@auth
Feature: Email verification and resend
  An unverified account receives a verification email on sign-up. The embedded
  link (or its token submitted via POST) flips email_verified to true. A resend
  endpoint re-issues a fresh token, invalidating the prior one. Rate limiting
  and email-enumeration privacy are enforced.

  Background:
    Given the SMTP capture server is running

  # === End-to-end verify via POST ===
  Scenario: Valid token verifies account via POST
    Given a new user signs up with email "verify-post@example.com" and password "SecureP@ss1"
    And a verification email is captured for "verify-post@example.com"
    When the raw token is extracted from the captured verification link
    And the user submits POST /api/v1/auth/verify with the extracted token
    Then the response status is 200
    And the response body indicates verification succeeded
    And the user "verify-post@example.com" has email_verified true in the database

  # === GET-link verify redirects ===
  Scenario: Valid token verifies account via GET and redirects to login
    Given a new user signs up with email "verify-get@example.com" and password "SecureP@ss1"
    And a verification email is captured for "verify-get@example.com"
    When the raw token is extracted from the captured verification link
    And the user submits GET /api/v1/auth/verify with the extracted token as query param
    Then the response status is 303
    And the response Location header points to the configured redirect URL

  # === GET-link verify failure redirects to the error page ===
  Scenario: Expired token via GET redirects to the error page
    Given a new user signs up with email "verify-get-expired@example.com" and password "SecureP@ss1"
    And a verification email is captured for "verify-get-expired@example.com"
    And the raw token is extracted from the captured verification link
    And the token for "verify-get-expired@example.com" is expired in the database
    When the user submits GET /api/v1/auth/verify with the extracted token as query param
    Then the response status is 303
    And the response Location header points to the configured error redirect URL

  # === Consumed token returns 410 ===
  Scenario: Consumed token returns 410 on second use
    Given a new user signs up with email "consumed@example.com" and password "SecureP@ss1"
    And a verification email is captured for "consumed@example.com"
    And the raw token is extracted from the captured verification link
    And the user submits POST /api/v1/auth/verify with the extracted token
    When the user submits POST /api/v1/auth/verify with the same token again
    Then the response status is 410

  # === Expired token returns 410 ===
  Scenario: Expired token returns 410
    Given a new user signs up with email "expired@example.com" and password "SecureP@ss1"
    And a verification email is captured for "expired@example.com"
    And the raw token is extracted from the captured verification link
    And the token for "expired@example.com" is expired in the database
    When the user submits POST /api/v1/auth/verify with the extracted token
    Then the response status is 410

  # === Resend issues fresh email and invalidates prior token ===
  Scenario: Resend issues a new token and invalidates the prior one
    Given a new user signs up with email "resend@example.com" and password "SecureP@ss1"
    And a verification email is captured for "resend@example.com"
    And the raw token is extracted from the captured verification link
    And the captured messages are cleared
    When the user submits POST /api/v1/auth/verification/resend with email "resend@example.com"
    Then the response status is 202
    And a new verification email is captured for "resend@example.com"
    When the new raw token is extracted from the latest captured verification link
    And the user submits POST /api/v1/auth/verify with the old token
    Then the response status is 410
    When the user submits POST /api/v1/auth/verify with the new token
    Then the response status is 200
    And the user "resend@example.com" has email_verified true in the database

  # === Resend privacy — uniform 202 and no email for unknown/verified ===
  Scenario Outline: Resend returns uniform 202 and sends no email for <case>
    Given <precondition>
    And the captured messages are cleared
    When the user submits POST /api/v1/auth/verification/resend with email "<email>"
    Then the response status is 202
    And no verification email is captured for "<email>"

    Examples:
      | case             | precondition                                                  | email                |
      | unknown email    | no account exists for "ghost@example.com"                     | ghost@example.com    |
      | already verified | a user "verified@example.com" exists with email_verified true | verified@example.com |

  # === Resend rate limit ===
  Scenario: Resend rate limit returns 429 with Retry-After
    Given a new user signs up with email "ratelimit@example.com" and password "SecureP@ss1"
    And a verification email is captured for "ratelimit@example.com"
    And the captured messages are cleared
    When the user submits POST /api/v1/auth/verification/resend with email "ratelimit@example.com" 6 times
    Then the last response status is 429
    And the last response includes a Retry-After header
    And no further verification email is captured for "ratelimit@example.com" after the rate limit is hit
