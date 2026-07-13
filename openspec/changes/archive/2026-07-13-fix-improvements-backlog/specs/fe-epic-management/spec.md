# fe-epic-management

## ADDED Requirements

### Requirement: Whitespace-only titles are not submitted
The create and edit forms SHALL trim the title before the required-check and SHALL NOT submit a title that is empty after trimming, matching the ticket form's behavior.

#### Scenario: Whitespace-only title blocked client-side
- **WHEN** the user enters a title consisting only of whitespace and submits
- **THEN** no request is issued and the form indicates the title is required
