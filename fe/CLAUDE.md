# Frontend Design System

All frontend work in `fe/` (React 19 + TypeScript + Vite + Tailwind v4) MUST follow the
project's single design language. **`DESIGN.md`** (in this directory) is the source of truth
for every visual decision — colors, typography, spacing, radius, elevation, component chrome.
**Read `DESIGN.md` before building or changing any UI**, and map every value to an existing
token instead of inventing new ones.

## How to apply it

- Before writing or editing UI, consult `DESIGN.md` and use its tokens (`{colors.*}`,
  `{typography.*}`, `{spacing.*}`, `{rounded.*}`). Never hardcode arbitrary colors, sizes,
  radii, or font values.
- If a value is not covered by a token, pick the nearest token rather than adding a one-off,
  and call out the gap.
- Keep one consistent style across all components and pages. Reuse existing component chrome
  before introducing a new pattern.

## Implementation: Tailwind v4

`DESIGN.md` tokens are wired into code as Tailwind v4 `@theme` variables in
`fe/src/index.css` — that file is the single spec→code bridge. Build UI from
token-named utilities whose names match the `DESIGN.md` keys (`bg-canvas-soft`,
`text-ink`, `text-display-lg`, `rounded-md`, `font-mono`, `shadow-card`); spacing
uses the native 4px numeric scale (`p-6` = 24px = `lg`). See `index.css` for the
full set.

- The stock palette and type scale are **reset** (`--color-*` / `--text-*: initial`),
  so off-token utilities (`bg-blue-500`, `text-base`) render nothing — by design.
- Need a token that isn't defined yet? Add it to `@theme` in `index.css`,
  transcribed from `DESIGN.md`. Don't reach for a Tailwind default, and don't use an
  arbitrary value (`bg-[#…]`, `text-[14px]`) for anything `DESIGN.md` tokenizes —
  arbitrary values are only for genuine layout one-offs (e.g. grid track sizes).
- Type roles already bundle line-height / letter-spacing / weight; don't stack a
  separate `font-*` / `leading-*` / `tracking-*` on top.

## shadcn/ui primitives

shadcn/ui is the source of accessible primitives (DropdownMenu, and Dialog/Select/…
as later epics consume them). It landed in **E11**. Config: `components.json` (new-york
style, `neutral` base, `@/` alias), the `cn()` helper in `src/lib/utils.ts`, and the
`@/*` path alias in `tsconfig.json` + `vite.config.ts`.

**Semantic-token bridge (mandatory).** The stock palette reset (`--color-*: initial`)
leaves shadcn's semantic classes with no value, so `src/index.css` bridges them onto
`DESIGN.md` tokens:

- The `@theme` block has a "shadcn/ui semantic bridge" section mapping `--color-background`,
  `--color-foreground`, `--color-popover`, `--color-accent`, `--color-muted`,
  `--color-destructive`, `--color-border`, `--color-ring`, … onto DESIGN tokens
  (`--color-primary` is already a DESIGN token and doubles as shadcn's primary).
- `:root { --radius }` anchors shadcn's bare `--radius` to the DESIGN md radius.
- A `@layer base` rule sets the default `border-color` to `--color-border`, since shadcn
  components use a bare `border` utility; DESIGN components always name their border color,
  so they're unaffected.

**Type & shadow scales are reset too.** shadcn's generated components use stock `text-sm`
and `shadow-md`, which don't exist here. When adding a primitive, replace those with the
DESIGN role tokens (`text-body-sm` / `text-body-sm-strong`, `shadow-card`) — see
`src/components/ui/dropdown-menu.tsx` for the pattern.

**Adding more primitives.** Run `shadcn add <name>` (YAGNI — only when a screen consumes
it), then apply the type/shadow adaptation above. Enter/exit animation utilities
(`animate-in`, `fade-in-0`, …) come from `tw-animate-css`, imported at the top of
`index.css`.

## Pay special attention to

The spec is long, so the highest-impact rules are easy to miss. Before finishing any UI
change, re-check it against these parts of `DESIGN.md` — read the values from the spec
each time, do not copy them here:

- "Do's and Don'ts" — the hard rules that get violated most often.
- The `colors`, `typography`, `spacing`, `rounded`, and `components` token tables in the
  frontmatter — map every value you use to one of these.
- "Colors", "Typography", "Elevation & Depth", and "Shapes" — for the surface ladder, type
  hierarchy and weight ceiling, shadow recipes, and radius scale.

If you reach for a color, size, radius, or font value that isn't in those tables, stop and
pick the nearest token instead of adding a one-off.
