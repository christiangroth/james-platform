# Design Reference: Branding & Login/Landing Page

> **Status: partially implemented.** The logo mark and the login page hero panel described below are implemented (`favicon.svg`, `#icon-nav-app` in `layout.html`, `login.html`).
> The alternative background *options* are documented for reference; only "Option A" is actually wired up. See [issue #392](https://github.com/christiangroth/james-platform/issues/392).

## Goal

James Platform is a technical tool for a technically versed audience, so the UI stays sober by design (see "Design Principles" in
[role-frontend-developer.md](role-frontend-developer.md)) — no gimmicks, no stock illustrations. Within that constraint, three things were plain placeholders rather than
considered design:

1. The "logo" was a generic person-silhouette-in-a-circle glyph, unrelated to the product.
2. The login page was a bare centered card on a flat single-color background — functional, but not a first impression.
3. There was no distinct app icon for favicon/browser tab/bookmark use beyond that same placeholder glyph.

This document proposes a small, coherent identity built from assets already available (no external image generation was used) and a login page treatment that reads more like a
landing page without adding marketing fluff.

## Logo mark

A single SVG mark (`viewBox="0 0 32 32"`) replaces the placeholder glyph everywhere it was used (navbar, login page, `favicon.svg`):

- Rounded-square "app icon" shape (`rx="9"`, matching the squircle convention of modern OS app icons), filled with a diagonal gradient from `--color-action` blue (`#0d6efd`) to
  `--spotify-green` (`#1db954`) — the platform's own two accent colors (general actions / Spotify integration), rather than an arbitrary new color.
- A single white stroked "J" glyph, simple enough to stay legible at 16×16 (browser tab) size.
- A small white dot accent near the top-right of the stroke, echoing the live-indicator dot already used elsewhere in the UI (see "Live indicators" in
  [role-frontend-developer.md](role-frontend-developer.md)).

The mark is defined once as `#icon-nav-app` in the shared SVG sprite in `layout.html` (with its gradient `<defs>`) and reused via `<use href="#icon-nav-app"/>` in the navbar and
on the login page — the sprite already covers icon reuse for the rest of the app, so no new pattern is introduced. `favicon.svg` embeds the identical shape standalone, since a
favicon file is loaded outside the page document and cannot reference the sprite's `<defs>`.

Because the mark now carries its own fixed brand colors (not `currentColor`/`var(--color-text-primary)`), it intentionally renders identically in dark and light mode, the same
way product logos (Spotify, Slack, etc.) typically don't reflow with theme — a logo is brand identity, not themed UI chrome.

## Login page → landing-page treatment

`login.html` now splits into two panels above the `md` breakpoint (single column below it, unchanged behavior on mobile):

- **Left "hero" panel** (`.login-hero`, hidden below `md`): logo mark at a larger size, the app name, the one-line product description already used in `README.md` ("A personal
  low-code platform for building and running data-centric apps"), and three short feature bullets pulled directly from the real feature set (custom data models, installing apps
  without infra boilerplate, built-in docs/health/monitoring) — no invented marketing copy.
- **Right panel**: the existing login card, unchanged in behavior/markup for form fields, `data-testid`s, and error handling (`LoginPageTests`/`LoginRedirectTests` assert on
  `data-testid="login-button"`, field `name`s, and the `James Platform` string, all still present).

On mobile the hero panel is dropped entirely (`d-none d-md-flex`) and the login card falls back to the original compact single-column layout with its own small logo, so nothing
is lost on small screens.

## Alternative backgrounds considered

The plain `var(--color-bg-page)` fill was replaced on the login screen specifically (not site-wide — the rest of the app intentionally stays flat/utilitarian per the "no
clutter" design principle). Three CSS-only options were considered, since no external imagery/photography is used anywhere else in the app and introducing raster assets would be
inconsistent with the SVG-sprite-only approach used today:

| Option | Description | Verdict |
|---|---|---|
| **A — Soft radial mesh (implemented)** | Two low-opacity radial gradients (blue top-left, green bottom-right, ~10% opacity on the page shell / ~25% on the hero panel) over the existing background variable, plus a faint dot-grid texture (`radial-gradient` dots, 24px grid, 25% opacity) on the hero panel only. | Chosen — subtle, on-brand (reuses the two existing accent colors), reads as "designed" without being loud, still fully sober for a technical audience. Works in both themes since it layers on top of `--color-bg-page`/`--color-bg-nested`. |
| **B — Animated gradient** | Same mesh as A but with a slow `background-position` keyframe animation. | Rejected for now — motion on an auth screen can be distracting/accessibility-unfriendly (`prefers-reduced-motion` would need to be respected), and the user's own brief said "nüchtern ist ok" (sober is fine); a static treatment fits that better. Worth revisiting as a purely optional enhancement behind a `prefers-reduced-motion: no-preference` guard if desired later. |
| **C — Line/circuit pattern** | A repeating SVG pattern of thin connected lines/nodes (circuit-board aesthetic), tiled as a `background-image`. | Rejected for the initial pass — a busier pattern risks competing with the login form and needs more careful contrast tuning per theme than a mesh gradient; flagged here as a follow-up if Option A turns out too plain in practice. |

## Follow-ups / open questions

- The hero copy is in English to match the rest of the UI (all existing labels/buttons are English even though the product is operated by a German-speaking user); revisit if the
  app should be bilingual.
- No new image/logo files beyond SVG were introduced; if a raster app icon (e.g. `apple-touch-icon`, PWA manifest icons) is needed later, it should be generated from the same
  mark defined here to stay consistent.
- Option B/C above are documented but not implemented — pick them up if Option A doesn't feel distinctive enough after a real visual pass.
