# fe-design-primitives — Delta

## ADDED Requirements

### Requirement: Modal overlays dim the underlying page
Modal primitives (Dialog, AlertDialog) SHALL render a backdrop overlay that visibly darkens the page content behind the modal, using a DESIGN.md token color at partial opacity — never a stock-palette utility that the Tailwind palette reset resolves to nothing.

#### Scenario: Open dialog darkens the background
- **WHEN** any dialog or alert dialog opens
- **THEN** the page behind it is visibly darkened by a semi-transparent backdrop, and the backdrop disappears when the modal closes
