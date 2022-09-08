# Using Architecture Decision Records to document decisions in a sustainable manner

* Status: accepted
* Deciders: Chris
* Date: 2022-01-31

## Context and Problem Statement

To avoid forgetting about made decisions, especially when not working continuously on a project, the decision process should be documented. 
As there is no team involved in my personal spare-time projects, the decision-making should be directed in a structured but still lightweight way.
The process of all decisions should be documented as close to the code as possible, e.g. in SCM.

## Decision Drivers

* Documenting decisions and their process
* Find a way to support structured decision-making
* Getting hands-on experience in software architecture documentation

## Considered Options

* [arc42](https://www.arc42.de/)
* [ADR](https://adr.github.io/)

## Decision Outcome

ADR, because it is focused on the core of decision-making , is more lightweight than arc42 and therefore feasible for spare-time projects.

### Positive Consequences

* Structured template and guidance for decision-making
* It is possible to use existing or build own tooling to further support the process or generate visualizations
* Lightweight and process fully integrated into SCM and project tooling

### Negative Consequences

* Experience in software architecture documentation is only focused on decision-making, all other parts are left out for the moment

## Links

* Defines [ADR project template](000-template.md)
