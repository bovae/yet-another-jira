# fe-design-primitives

## ADDED Requirements

### Requirement: Destructive actions meet contrast
Destructive controls (e.g. the button `destructive` variant) SHALL pair the error surface color with a foreground token that meets WCAG AA contrast (≥ 4.5:1), using bridged DESIGN.md tokens — never a stock-palette utility that the Tailwind palette reset resolves to nothing.

#### Scenario: Delete button is readable
- **WHEN** a destructive button (e.g. "Delete" in a confirmation dialog) renders
- **THEN** its text color resolves to a bridged token value with at least 4.5:1 contrast against the button surface
