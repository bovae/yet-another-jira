# fe-team-management

## ADDED Requirements

### Requirement: Whitespace-only names are not submitted
The create and rename forms SHALL trim the name before the required-check and SHALL NOT submit a name that is empty after trimming, matching the ticket form's behavior.

#### Scenario: Whitespace-only name blocked client-side
- **WHEN** the user enters a name consisting only of whitespace and submits
- **THEN** no request is issued and the form indicates the name is required
