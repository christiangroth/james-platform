# [short title of solved problem and solution]

* Status: proposed
* Deciders: Chris
* Date: 2022.02.01

## Context and Problem Statement

The goal is to rewrite the software with more focus on the domain implementation and a much clearer separation of domain and technologies.
It should be possible to maintain the project over a long time and change/upgrade technologies without touching domain implementations.

## Decision Drivers

* Clear domain focus to develop business logic independent of implementation technologies
* Ability to change technologies without affecting business domain
* Good testability of business domain

## Considered Options

* Hexagonal architecture
* TODO

## Decision Outcome

Chosen option: "[option 1]",
because [justification. e.g., only option, which meets k.o. criterion decision driver | which resolves force force | … | comes out best (see below)]
.

### Positive Consequences <!-- optional -->

* [e.g., improvement of quality attribute satisfaction, follow-up decisions required, …]
* …

### Negative Consequences <!-- optional -->

* [e.g., compromising quality attribute, follow-up decisions required, …]
* …

## Pros and Cons of the Options <!-- optional -->

### [option 1]

[example | description | pointer to more information | …] <!-- optional -->

* Good, because [argument a]
* Good, because [argument b]
* Bad, because [argument c]
* … <!-- numbers of pros and cons can vary -->

### [option 2]

[example | description | pointer to more information | …] <!-- optional -->

* Good, because [argument a]
* Good, because [argument b]
* Bad, because [argument c]
* … <!-- numbers of pros and cons can vary -->

### [option 3]

[example | description | pointer to more information | …] <!-- optional -->

* Good, because [argument a]
* Good, because [argument b]
* Bad, because [argument c]
* … <!-- numbers of pros and cons can vary -->

## TODOs

* describe
* refine naming XYZService implements XYZUseCases
* refine ports to be domain driven
* think about value objects (Commands) for UseCases
* think how security is added to services
* service parameters and return types: domain objects vs DTOs vs combination
* think about validation and maybe use JSR-303?
* think about separating bounded contexts using ports/adapters

Describe:

* current structure
* intelligent domain objects and dumb services
* error handling / codes

Links:

* https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749
* https://blog.codecentric.de/en/2020/07/hexagon-schmexagon-1/
* https://reflectoring.io/spring-hexagonal/
* https://vaadin.com/learn/tutorials/ddd/ddd_and_hexagonal