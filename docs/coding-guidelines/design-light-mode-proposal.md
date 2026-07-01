# Design Proposal: Light Mode Color Palette

> **Status: proposal only.** No light mode / theme switch is implemented. This document defines a candidate light palette and the reasoning behind it, for future
> implementation once approved. See [issue #392](https://github.com/christiangroth/james-platform/issues/392).

## Goal

James Platform currently ships a single, dark, hardcoded theme (see "Design Principles" in [role-frontend-developer.md](role-frontend-developer.md)). This document proposes a
parallel light palette that preserves the same visual hierarchy, semantic color roles and depth conventions, so that a light mode can be added later (e.g. via
`prefers-color-scheme` detection with a manual override stored per-user) without redesigning the theme from scratch.

## Proposed CSS custom properties

All values below are candidates for a second `:root` block (e.g. under a `.light-mode` class or `@media (prefers-color-scheme: light)`), mirroring the variable names already
defined in `layout.html`.

| Variable | Dark (current) | Light (proposed) | Notes |
|---|---|---|---|
| `--color-bg-page` (new) | `#121212` (hardcoded on `body`) | `#f4f5f7` | Page background; slightly recessed compared to card surfaces, same relationship as today |
| `--color-bg-card` | `#1e1e1e` | `#ffffff` | Card / panel surface |
| `--color-bg-nested` | `#161616` | `#eef0f2` | Nested object-property container background |
| `--color-border` | `#3d3d44` | `#ccced2` | Card outlines, table/list dividers |
| `--color-border-muted` | `#54545c` | `#9599a1` | Form control / secondary button borders – kept more visible than `--color-border`, same as today |
| `--color-text-primary` | `#e0e0e0` | `#1a1a1e` | Body text |
| `--color-text-muted` | `#9a9aa2` | `#5f6368` | Secondary/muted text |
| `--color-action` | `#0d6efd` | `#0d6efd` (unchanged) | Kept identical – already close to the AA text-contrast threshold on white; see note below |
| `--color-action-hover` | `#0b5ed7` | `#0b5ed7` (unchanged) | |
| `--color-danger` | `#dc3545` | `#dc3545` (unchanged) | |
| `--color-danger-hover` | `#bb2d3b` | `#bb2d3b` (unchanged) | |
| `--spotify-green` | `#1db954` | `#1db954` (unchanged for buttons) | See note below – do not reuse as text/link color in light mode |
| `--color-bg-depth-albums` | `#161616` | `#eef0f2` | Level-1 nested `OBJECT` property |
| `--color-bg-depth-tracks` | `#111111` | `#e2e5e9` | Level-2 nested `OBJECT` property |
| `--color-border-depth-albums` | `#2a2a2a` | `#dde0e4` | Intentionally subtle, close to its own background – depth is conveyed by the background step, not the border |
| `--color-border-depth-tracks` | `#1a1a1a` | `#cfd3d8` | Same rationale as above |

Semantic color roles (blue = primary action, red = destructive, green = Spotify-only, muted gray = secondary) stay exactly the same in both themes, so the meaning of a color
never changes when switching modes – only its concrete shade.

## Contrast check (WCAG 2.1)

Approximate contrast ratios computed against the relevant background:

- `--color-text-primary` on `--color-bg-page`: **~16:1** (AAA)
- `--color-text-muted` on `--color-bg-page` / `--color-bg-card`: **~5.5:1 / ~6:1** (AA)
- `--color-border-muted` on `--color-bg-card`: **~2.9:1** (meets the 3:1 non-text minimum for interactive borders)
- `--color-border` on `--color-bg-card`: **~1.6:1** (intentionally subtle divider, consistent with the dark theme's original `--color-border` before the contrast fix applied in
  this same change – see below)
- `--color-action` (`#0d6efd`) as text/link on white: **~4.5:1** – right at the AA threshold for normal text; prefer `--color-action-hover` (`#0b5ed7`, ~5.2:1) for small link
  text if a stricter margin is wanted
- `--spotify-green` (`#1db954`) as text on white: **~2.6:1** – **fails AA for text.** It works well in dark mode (~7.2:1 on `#121212`) but must **not** be reused as link/text
  color in light mode (e.g. `.docs-content a`). Keep it for solid-background buttons only (`.btn-spotify`, black text on green), and pick a different accent (e.g.
  `--color-action`) for links in the light theme.

## Open decisions before implementation

1. **Navbar treatment** – recommend keeping `.app-navbar` dark (e.g. `#14161a`) in both themes for brand consistency (a common pattern for developer tools), rather than
   inverting it to white. Needs a visual mockup/sign-off since it is a deliberate deviation from a "fully inverted" light theme.
2. **`.docs-content a` color** – must move off `--spotify-green` in light mode per the contrast finding above.
3. All values above are calculated, not yet visually validated in the running app – expect minor tuning once implemented and reviewed on real pages (dashboard, tables, forms,
   modals, accordions).

## Out of scope for this proposal

- The actual theme-switching mechanism (OS/browser detection via `prefers-color-scheme`, manual override, persistence in local storage or the user profile) is a separate,
  follow-up implementation task once this palette is approved.
- No code in `layout.html` implements this palette yet.
