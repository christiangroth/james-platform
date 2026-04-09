# Markdown Rendering Library: marked

* Status: accepted
* Deciders: Chris
* Date: 2026-02-25

## Context and Problem Statement

The application will serve documentation and release notes written in Markdown to the logged-in user. The Markdown must be rendered in a GitHub-like style inside the existing Qute SSR pages without introducing a build step, npm, or Node.js.

## Decision Drivers

* Must be available as a WebJar (consistent with Bootstrap, Font Awesome)
* Minimal bundle size – project complexity guidelines favour lightweight dependencies
* No build step, no npm – usable as a plain `<script>` include
* GitHub-Flavored Markdown (GFM) support (tables, fenced code blocks, task lists)
* Simple, stable API

## Considered Options

* `marked` (`org.webjars.npm:marked`)
* `markdown-it` (`org.webjars.npm:markdown-it`)
* `showdown` (`org.webjars.npm:showdown`)

## Decision Outcome

Chosen option: **`marked`** (`org.webjars.npm:marked`), because it is the most lightweight option with a minimal API surface, strong GFM support, and consistent WebJar availability. It is the de-facto standard for client-side Markdown rendering and requires no configuration for the use case at hand.

### Positive Consequences

* Single function call: `marked.parse(markdownString)` → HTML
* Bundle size ~22 KB minified – negligible impact on page weight
* GFM enabled by default – tables, fenced code, task lists render correctly
* Available as `org.webjars.npm:marked` on Maven Central
* No runtime configuration required for standard Markdown content

### Negative Consequences

* Client-side rendering – Markdown is sent as raw text and rendered in the browser (acceptable: docs are only shown to the authenticated user, no SEO concern)
* No built-in syntax highlighting for code blocks; `highlight.js` could be added as a future enhancement if needed

## Pros and Cons of the Options

### `marked`

* Good, because smallest bundle (~22 KB)
* Good, because GFM supported by default
* Good, because single-function API (`marked.parse`)
* Good, because available as WebJar (`org.webjars.npm:marked`)
* Bad, because no built-in syntax highlighting (can be added via `highlight.js` as a plugin)

### `markdown-it`

* Good, because highly extensible plugin architecture
* Good, because available as WebJar (`org.webjars.npm:markdown-it`)
* Bad, because larger bundle (~50 KB core + plugins)
* Bad, because GFM table support requires a separate plugin (`markdown-it-multimd-table` or similar)

### `showdown`

* Good, because bidirectional Markdown ↔ HTML conversion
* Bad, because bidirectional capability is not needed and adds complexity
* Bad, because GFM compliance is less consistent than `marked`
* Bad, because less actively maintained

## Links

* [marked.js GitHub](https://github.com/markedjs/marked)
* [org.webjars.npm:marked on Maven Central](https://mvnrepository.com/artifact/org.webjars.npm/marked)
