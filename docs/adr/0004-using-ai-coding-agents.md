# Using AI Coding Agents

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

The project is developed by a single developer. AI coding agents can significantly accelerate development. The question is how to integrate them in a way that maintains code quality and architectural integrity.

## Decision Drivers

* Solo developer wanting to move faster without sacrificing quality
* Need for consistent code style and architecture adherence
* Risk of AI agents introducing inconsistencies or violating architectural boundaries

## Considered Options

* Use AI coding agents for all tasks autonomously
* Use AI coding agents for isolated, well-defined tasks with mandatory review
* Do not use AI coding agents

## Decision Outcome

Chosen option: "Use AI coding agents for isolated, well-defined tasks with mandatory review", because it captures the productivity benefits while maintaining quality control through consistent human code review.

### Positive Consequences

* Faster implementation of isolated, well-scoped tasks
* AI agents benefit from structured role documentation and architecture docs
* Consistent coding style enforced through role documentation

### Negative Consequences

* Requires maintaining up-to-date role documentation for AI agents
* Code review overhead for every AI-generated change

## Working Agreements

* **Isolated tasks:** AI agents are used for clearly scoped, isolated tasks – not for open-ended architectural changes
* **Mandatory code review:** Every AI-generated change is reviewed by the developer before merging
* **Consistent coding style:** AI agent role documentation (`coding-guidelines/`) defines the expected style and is kept up to date
* **Documentation updates:** AI agents may be delegated to update documentation (arc42, ADRs, role files) when architectural changes are made

## Links

* [role-architect.md](../coding-guidelines/role-architect.md)
* [role-backend-developer.md](../coding-guidelines/role-backend-developer.md)
* [role-frontend-developer.md](../coding-guidelines/role-frontend-developer.md)
* [role-test-engineer.md](../coding-guidelines/role-test-engineer.md)
