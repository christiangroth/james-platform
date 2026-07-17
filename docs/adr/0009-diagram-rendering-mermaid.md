# Diagram Rendering: Mermaid

* Status: accepted
* Deciders: Chris
* Date: 2026-07-17

## Context and Problem Statement

`docs/arc42/arc42.md` needed diagrams (Whitebox Overall System, Runtime View, Deployment View,
Business Context) but had none, or only hand-drawn ASCII box art. Two different surfaces render
this same Markdown file: GitHub's web UI (browsing the repo directly) and this application's
own in-app docs viewer (`docs.html`, `marked` WebJar rendering client-side). A diagram format
had to work acceptably on at least one of these, ideally both.

## Decision Drivers

* GitHub renders `​```mermaid` fenced code blocks as diagrams natively — no extra tooling needed
  for the GitHub-browsing use case.
* The in-app docs viewer uses `marked` for Markdown → HTML, which has no built-in diagram
  support; a `​```mermaid` block left untouched renders there as a plain code block, not a
  diagram.
* Diagrams should stay maintainable as plain text in the same Markdown file (matching how ADRs
  and arc42 are already authored), not as separately maintained image assets.
* Chris explicitly asked for both surfaces (GitHub and in-app) to render diagrams correctly,
  not just one.

## Considered Options

1. **Mermaid, GitHub-native only** — use `​```mermaid` blocks, accept that the in-app viewer
   shows raw text for them.
2. **Mermaid, GitHub-native + client-side `mermaid.js` in the in-app viewer** — same source
   diagrams, but post-process the in-app viewer's rendered HTML to also render them.
3. **Hand-drawn ASCII art in code fences** — renders identically (as preformatted text) on both
   surfaces, but is not an actual diagram on either, and is tedious to keep aligned by hand.

## Decision Outcome

Chosen option: **"Mermaid, GitHub-native + client-side `mermaid.js` in the in-app viewer"**.
Diagrams are authored once as `​```mermaid` fenced blocks, which:

- Render as diagrams automatically when browsing `arc42.md`/ADRs on github.com.
- Render as diagrams in the in-app docs viewer via a small module script in `docs.html`: after
  `marked.parse()` runs, it finds `<pre><code class="language-mermaid">` blocks (marked's
  standard output for an unrecognised fenced-code language), converts each to a `<div
  class="mermaid">` containing the raw source, and calls `mermaid.run()`. The `mermaid` WebJar
  (`org.webjars.npm:mermaid`) is added as a dependency and loaded as an ES module from
  `/webjars/mermaid/dist/mermaid.esm.min.mjs`, mirroring how `marked` itself is already loaded.
- Follow the app's dark/light theme: `mermaid.initialize({ theme: ... })` reads
  `document.documentElement`'s `data-theme` attribute, and diagrams are re-rendered (re-parsed
  from source, not re-run in place) when the theme toggle is clicked, since Mermaid bakes
  colors into the rendered SVG rather than using CSS custom properties.

### Positive Consequences

* Diagrams are plain, greppable, diffable text living next to the prose that describes them —
  no binary image assets, no separate diagramming tool to keep in sync.
* Correct rendering on both surfaces that actually matter for this project (GitHub review,
  in-app reading).
* The conversion glue is generic (looks for any `language-mermaid` block) — no per-diagram
  wiring needed when new diagrams are added later.

### Negative Consequences

* Adds a new runtime dependency (`mermaid` WebJar, a few hundred KB) loaded on every visit to
  the Docs page, even for pages without any diagrams (the `renderMermaidDiagrams()` function
  no-ops quickly if there are none, but the module is still fetched).
* Mermaid is loaded as an ES module (`dist/mermaid.esm.min.mjs`); older browsers without ES
  module support would silently fail to render diagrams in-app (GitHub rendering is
  unaffected). Not a concern for this project's actual user base.
* One more moving part in `docs.html` beyond the original single `marked.parse()` call.

## Pros and Cons of the Options

### Mermaid, GitHub-native only

* Good, because zero extra dependencies or code — just write `​```mermaid` blocks.
* Bad, because the in-app docs viewer — the primary way a logged-in user reads this
  documentation without leaving the app — would show raw, unrendered diagram source.

### Mermaid, GitHub-native + client-side `mermaid.js`

* Good, because both real-world reading surfaces render actual diagrams.
* Good, because the diagram source is identical either way — no dual-authoring.
* Bad, because it adds a WebJar dependency and ~35 lines of glue code in `docs.html`.

### Hand-drawn ASCII art

* Good, because it renders identically (as text) everywhere, no tooling required.
* Bad, because it is not a diagram on either surface, just monospaced text art.
* Bad, because keeping box-drawing characters aligned by hand does not scale as diagrams grow
  or change.

## Links

* [Mermaid documentation](https://mermaid.js.org/)
* [GitHub: creating diagrams with Mermaid](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams)
* [`docs.html`](../../adapter-in-web/src/main/resources/templates/docs.html)
* [ADR 0005: Markdown Rendering Library: marked](0005-markdown-rendering-library.md)
