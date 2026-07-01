# Design Reference: Light Mode Color Palette

> **Status: implemented.** The light palette below is implemented in `layout.html` under the `[data-theme="light"]` selector, and the theme switch (OS/browser detection, manual
> override, `localStorage` persistence) is implemented via the inline head script + `theme-utils.js`, see "Theme switching" in [role-frontend-developer.md](role-frontend-developer.md).
> This document is kept as the design rationale and contrast-ratio reference. See [issue #392](https://github.com/christiangroth/james-platform/issues/392).

## Goal

James Platform originally shipped a single, dark, hardcoded theme (see "Design Principles" in [role-frontend-developer.md](role-frontend-developer.md)). This document defines a
parallel light palette that preserves the same visual hierarchy, semantic color roles and depth conventions, so that light mode looks like a first-class theme rather than an
afterthought.

## CSS custom properties

All values below are implemented in a `[data-theme="light"]` block in `layout.html`, overriding the dark defaults defined in `:root` for the same variable names.

| Variable | Dark (default) | Light (`[data-theme="light"]`) | Notes |
|---|---|---|---|
| `--color-bg-page` | `#121212` | `#f4f5f7` | Page background; slightly recessed compared to card surfaces, same relationship as today |
| `--color-bg-card` | `#1e1e1e` | `#ffffff` | Card / panel surface |
| `--color-bg-nested` | `#161616` | `#eef0f2` | Nested object-property container background |
| `--color-border` | `#686870` | `#ccced2` | Card outlines, table/list dividers |
| `--color-border-muted` | `#797981` | `#9599a1` | Form control / secondary button borders – kept more visible than `--color-border`, same as today |
| `--color-text-primary` | `#e0e0e0` | `#1a1a1e` | Body text |
| `--color-text-muted` | `#9a9aa2` | `#5f6368` | Secondary/muted text |
| `--color-action` | `#0d6efd` | `#0d6efd` (unchanged) | Kept identical – already close to the AA text-contrast threshold on white; see note below |
| `--color-action-hover` | `#0b5ed7` | `#0b5ed7` (unchanged) | |
| `--color-danger` | `#dc3545` | `#dc3545` (unchanged) | |
| `--color-danger-hover` | `#bb2d3b` | `#bb2d3b` (unchanged) | |
| `--color-bg-depth-albums` | `#161616` | `#eef0f2` | Level-1 nested `OBJECT` property |
| `--color-bg-depth-tracks` | `#111111` | `#e2e5e9` | Level-2 nested `OBJECT` property |
| `--color-border-depth-albums` | `#2a2a2a` | `#dde0e4` | Intentionally subtle, close to its own background – depth is conveyed by the background step, not the border |
| `--color-border-depth-tracks` | `#1a1a1a` | `#cfd3d8` | Same rationale as above |
| `--color-required` | `#f87171` | `#dc3545` | Required-field asterisk; the lighter dark-mode red would drop below AA on a white background |
| `--color-code-text` | `#cccccc` | `#495057` | Monospace preview/diff text on `--color-bg-nested` |
| `--color-warning-text` | `#ff9900` | `#997404` | Inline warning/slow-query counters |
| `--color-badge-muted-bg` | `#555555` | `#6c757d` | `.app-badge-muted` background |
| `--color-alert-success-bg/-border/-text` | `#1a2a1a` / `#226b22` / `#d4f0d4` | `#d1e7dd` / `#a3cfbb` / `#0f5132` | `.app-alert-success` |
| `--color-alert-danger-bg/-border/-text` | `#2a1a1a` / `#6b2222` / `#f8d7da` | `#f8d7da` / `#f1aeb5` / `#842029` | `.app-alert-danger` |
| `--color-diff-added/-removed/-modified-*` | see `layout.html` | see `layout.html` | Diff badges/lines in `tags/diff-section.html`; light values reuse Bootstrap's default alert palette |

Semantic color roles (blue = primary action, red = destructive, muted gray = secondary) stay exactly the same in both themes, so the meaning of a color never changes when
switching modes – only its concrete shade.

## Contrast check (WCAG 2.1)

Approximate contrast ratios computed against the relevant background:

- `--color-text-primary` on `--color-bg-page`: **~16:1** (AAA)
- `--color-text-muted` on `--color-bg-page` / `--color-bg-card`: **~5.5:1 / ~6:1** (AA)
- `--color-border-muted` on `--color-bg-card`: **~2.9:1** (meets the 3:1 non-text minimum for interactive borders)
- `--color-border` on `--color-bg-card`: **~1.6:1** – intentionally a subtle hairline divider rather than a high-contrast outline; light-mode UIs commonly use low-ratio
  dividers (e.g. GitHub's light theme border is ~1.3:1) because a thin 1px line reads clearly against a large, uniform white field even below the WCAG non-text minimum, unlike
  dark mode where the same subtlety made borders disappear against noisier dark surfaces – hence the dark theme's `--color-border` was raised to ~3:1 while light's stays subtle
  by design
- `--color-action` (`#0d6efd`) as text/link on white: **~4.5:1** – right at the AA threshold for normal text; prefer `--color-action-hover` (`#0b5ed7`, ~5.2:1) for small link
  text if a stricter margin is wanted

## Decisions made during implementation

1. **Navbar treatment** – `.app-navbar` stays dark (`#000000`) in both themes for brand consistency, as recommended below. Its white text/icons are not theme-variable-driven
   since they are only ever seen on the fixed-dark navbar.
2. `.btn-close-white` (used inside every modal's dismiss button) is reset to Bootstrap's default (dark) close icon in light mode via `[data-theme="light"] .btn-close-white { filter: none; }`,
   since modal surfaces are light in light mode.
3. A handful of additional variables not anticipated by the original palette proposal (`--color-required`, `--color-code-text`, `--color-warning-text`, `--color-badge-muted-bg`,
   the `--color-alert-*` and `--color-diff-*` families) were added to remove hardcoded hex colors that were found scattered across `health.html`, `mongodb-viewer.html`,
   `publish-version.html`, `version-editor.html`, `edit-property.html` and `tags/diff-section.html` – these would otherwise have rendered unreadable or visually inconsistent
   once light mode became reachable.
4. The unused `--spotify-green` variable and `.btn-spotify` button style (leftovers from the starting template – there is no Spotify integration in this app) were removed, along
   with `.docs-content a`'s dependency on it (now uses `--color-action` in both themes).
