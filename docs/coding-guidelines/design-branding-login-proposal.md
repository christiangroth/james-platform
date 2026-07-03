# Design Reference: Branding & Login/Landing Page

> **Status: partially implemented.** The logo mark and the login page hero panel described below are implemented (`favicon.svg`, `#icon-nav-app` in `layout.html`, `login.html`).
> The alternative background *options* are documented for reference; only "Option A" is actually wired up — and, per user feedback, it is now the default `body` background for
> every page rather than only the login screen. See [issue #392](https://github.com/christiangroth/james-platform/issues/392).

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
  a green accent (`#1db954`) — the platform's own two accent colors, rather than an arbitrary new color. (Note: this hex happens to match Spotify's brand green, but the app has
  no Spotify integration — it's simply the platform's chosen accent green, and the unused `--spotify-green`/`.btn-spotify` CSS leftover from the starting template has been removed.)
- A single white stroked "J" glyph, simple enough to stay legible at 16×16 (browser tab) size.
- A small white dot accent near the top-right of the stroke, echoing the live-indicator dot already used elsewhere in the UI (see "Live indicators" in
  [role-frontend-developer.md](role-frontend-developer.md)).

The mark is defined once as `#icon-nav-app` in the shared SVG sprite in `layout.html` (with its gradient `<defs>`) and reused via `<use href="#icon-nav-app"/>` in the navbar and
on the login page — the sprite already covers icon reuse for the rest of the app, so no new pattern is introduced. `favicon.svg` embeds the identical shape standalone, since a
favicon file is loaded outside the page document and cannot reference the sprite's `<defs>`.

Because the mark now carries its own fixed brand colors (not `currentColor`/`var(--color-text-primary)`), it intentionally renders identically in dark and light mode, the same
way product logos (Spotify, Slack, etc.) typically don't reflow with theme — a logo is brand identity, not themed UI chrome.

## Login page → landing-page treatment

`login.html` splits into two panels, stacked in a single column below the `md` breakpoint and shown side by side above it:

- **Hero panel** (`.login-hero`, top on narrow screens, left from `md` up): logo mark at a larger size, the app name, the one-line product description already used in
  `README.md` ("A personal low-code platform for building and running data-centric apps"), and three short feature bullets pulled directly from the real feature set (custom
  data models, installing apps without infra boilerplate, built-in docs/health/monitoring) — no invented marketing copy.
- **Login card** (below the hero on narrow screens, right from `md` up): the existing login card, unchanged in behavior/markup for form fields, `data-testid`s, and error
  handling (`LoginPageTests`/`LoginRedirectTests` assert on `data-testid="login-button"`, field `name`s, and the `James Platform` string, all still present).

On mobile the hero panel is shown above the login card instead of beside it (`flex-column flex-md-row` on `.login-shell`), so the app name, tagline and feature bullets are
visible on every screen size, not just from `md` up.

## Alternative backgrounds considered

The plain `var(--color-bg-page)` fill was originally replaced on the login screen only; per user feedback ("der Hintergrund mit Verlauf auf der Login Seite ist top. Bitte
einfach als Default für alle Seiten übernehmen") the same treatment (Option A) is now applied to `body` and used as the default background across the whole app, not just the
login screen. Three CSS-only options were considered, since no external imagery/photography is used anywhere else in the app and introducing raster assets would be inconsistent
with the SVG-sprite-only approach used today:

| Option | Description | Verdict |
|---|---|---|
| **A — Soft radial mesh (implemented, now site-wide)** | Two low-opacity radial gradients (blue top-left, green bottom-right, ~10% opacity) over the existing background variable, applied to `body`; the login hero panel keeps its own stronger ~25% variant plus a faint dot-grid texture (`radial-gradient` dots, 24px grid, 25% opacity) to stay visually distinct from the rest of the app. | Chosen — subtle, on-brand (reuses the two existing accent colors), reads as "designed" without being loud, still fully sober for a technical audience. Works in both themes since it layers on top of `--color-bg-page`/`--color-bg-nested`. |
| **B — Animated gradient** | Same mesh as A but with a slow `background-position` keyframe animation. | Rejected for now — motion on an auth screen can be distracting/accessibility-unfriendly (`prefers-reduced-motion` would need to be respected), and the user's own brief said "nüchtern ist ok" (sober is fine); a static treatment fits that better. Worth revisiting as a purely optional enhancement behind a `prefers-reduced-motion: no-preference` guard if desired later. |
| **C — Line/circuit pattern** | A repeating SVG pattern of thin connected lines/nodes (circuit-board aesthetic), tiled as a `background-image`. | Rejected for the initial pass — a busier pattern risks competing with the login form and needs more careful contrast tuning per theme than a mesh gradient; flagged here as a follow-up if Option A turns out too plain in practice. |

## Follow-ups / open questions

- The hero copy is in English to match the rest of the UI (all existing labels/buttons are English even though the product is operated by a German-speaking user); revisit if the
  app should be bilingual.
- **Favicon is SVG-only and will not display in every mobile browser.** `favicon.svg` (`<link rel="icon" type="image/svg+xml" ... sizes="any">`) is the only icon served. Firefox
  for iOS is built on WebKit (Apple requires all iOS browsers to use it) and its tab-icon fetcher does not support `type="image/svg+xml"` favicons at all — with no raster
  fallback present, it shows a generic placeholder ("?") instead. The standard fix is to also serve raster fallbacks (`favicon.ico` and/or `favicon-32x32.png` /
  `favicon-16x16.png` referenced via `<link rel="icon" type="image/png" sizes="32x32" href="...">`) generated from the same `#icon-nav-app` mark, since virtually every mobile
  browser (Firefox iOS, and historically Safari/Chrome iOS too) needs a raster icon regardless of SVG support elsewhere in the app. **Not implemented in this change**: this repo
  has no image-rasterization step (no ImageMagick/`rsvg-convert`/Node/Python SVG tooling in the build or the sandbox this was authored in), so no PNG/ICO asset could be
  generated here. Needs either (a) a raster asset produced externally and committed, or (b) a build-time SVG→PNG step added to the Gradle build.
- If a raster app icon is needed for other purposes too (e.g. `apple-touch-icon` for iOS home-screen bookmarks, PWA manifest icons), it should be generated from the same mark at
  the same time as the favicon fallback above.
- Option B/C above are documented but not implemented — pick them up if Option A doesn't feel distinctive enough after a real visual pass.
