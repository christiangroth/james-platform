# No Separate Frontend Project

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

The application requires a web UI. The question is whether to build a separate frontend project (SPA with a JavaScript framework) or integrate the UI into the backend application.

## Decision Drivers

* Single developer, single user – no team requiring frontend/backend separation
* Avoid frontend build toolchain (npm, webpack, Node.js)
* Keep deployment simple – one artifact, one process
* No need for a rich SPA; server-side rendering is sufficient

## Considered Options

* Separate frontend project (React, Vue, or similar SPA)
* Server-side rendering within the Quarkus application (Qute templates + vanilla JS)

## Decision Outcome

Chosen option: "Server-side rendering within the Quarkus application", because it eliminates frontend build toolchain complexity, reduces deployment surface, and is fully sufficient for a single-user developer tool.

### Positive Consequences

* Single deployable artifact
* No npm, Node.js, or JavaScript build step
* Simple dependency management via WebJars
* Vanilla JS (fetch API) provides sufficient interactivity without a JavaScript framework

### Negative Consequences

* Less rich client-side interactivity compared to a full SPA
* UI templates are tightly coupled to the backend application

## Links

* [role-frontend-developer.md](../coding-guidelines/role-frontend-developer.md)
* [arc42.md](../arc42/arc42.md)
