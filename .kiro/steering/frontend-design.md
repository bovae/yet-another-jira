---
inclusion: fileMatch
fileMatchPattern: "fe/**"
description: Enforce the single frontend design language defined in fe/DESIGN.md
---

# Frontend Design System

All frontend work in `fe/` (React 19 + TypeScript + Vite) MUST follow the project's
single design language. The design spec below is the source of truth for every visual
decision — colors, typography, spacing, radius, elevation, and component chrome. Read it
before building or changing any UI, and map every value to an existing token instead of
inventing new ones.

#[[file:fe/DESIGN.md]]

## How to apply it

- Before writing or editing UI, consult the spec above and use its tokens (`{colors.*}`,
  `{typography.*}`, `{spacing.*}`, `{rounded.*}`). Never hardcode arbitrary colors, sizes,
  radii, or font values.
- If a value is not covered by a token, pick the nearest token rather than adding a one-off,
  and call out the gap.
- Keep one consistent style across all components and pages. Reuse existing component chrome
  before introducing a new pattern.

## Pay special attention to

The spec is long, so the highest-impact rules are easy to miss. Before finishing any UI
change, re-check it against these parts of `fe/DESIGN.md` — read the values from the spec
each time, do not copy them here:

- "Do's and Don'ts" — the hard rules that get violated most often.
- The `colors`, `typography`, `spacing`, `rounded`, and `components` token tables in the
  frontmatter — map every value you use to one of these.
- "Colors", "Typography", "Elevation & Depth", and "Shapes" — for the surface ladder, type
  hierarchy and weight ceiling, shadow recipes, and radius scale.

If you reach for a color, size, radius, or font value that isn't in those tables, stop and
pick the nearest token instead of adding a one-off.
