# Backend: Hexagonal Architecture

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

The backend needs a clear structural pattern that separates domain logic from infrastructure concerns, makes the codebase testable, and enforces boundaries between adapters and the
domain.

## Decision Drivers

* Testability of domain logic without infrastructure dependencies
* Clear separation between external APIs, MongoDB, Slack, and domain logic
* Ability to swap or mock infrastructure components

## Considered Options

* Hexagonal (Ports and Adapters) Architecture
* Layered Architecture
* No explicit architecture pattern

## Decision Outcome

Chosen option: "Hexagonal Architecture", because it cleanly separates domain from infrastructure, enforces dependency direction (adapters depend on domain, not vice versa), and
enables isolated unit testing of business logic.

### Positive Consequences

* Domain logic is fully independent of Quarkus, MongoDB, and external concerns
* Adapter boundaries are explicit and enforced via Gradle module structure
* Easy to test domain services without any infrastructure setup

### Negative Consequences

* More modules and boilerplate than a simple layered architecture
* Requires discipline to maintain adapter boundaries

## Links

* [role-architect.md](../coding-guidelines/role-architect.md)
* [arc42.md](../arc42/arc42.md)
