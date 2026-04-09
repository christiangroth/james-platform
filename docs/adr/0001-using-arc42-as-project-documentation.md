# Using arc42 as Project Documentation

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

The project needs a documentation structure that is practical for a solo developer, covers architecture concerns, and can serve as reference context for AI coding agents.

## Decision Drivers

* Need for structured architecture documentation
* Must be lightweight enough for a solo project
* Should be a recognized standard, usable as context for AI coding agents

## Considered Options

* arc42
* Custom documentation structure
* No formal documentation structure

## Decision Outcome

Chosen option: "arc42", because it provides a proven, well-structured template that covers all relevant documentation aspects without imposing overhead, and is broadly understood.

### Positive Consequences

* Clear structure for capturing architecture decisions, constraints, and concepts
* Widely recognized template – usable as context for AI coding agents
* Markdown-based – lives alongside code in the repository

### Negative Consequences

* Some arc42 sections are not applicable for a single-user project and remain as "work in progress"

## Links

* [arc42 documentation](https://arc42.org)
* [arc42.md](../arc42/arc42.md)
