# Create a sustainable domain-focused architecture

* Status: accepted
* Deciders: Chris
* Date: 2022-02-10

## Context and Problem Statement

The goal is to rewrite the software with more focus on the domain implementation and a much clearer separation of domain and technologies.
It should be possible to maintain the project over a long time and change/upgrade technologies without touching domain implementations.

## Decision Drivers

* Clear domain focus to develop business logic independent of implementation technologies
* Ability to change technologies without affecting business domain
* Good testability of business domain

## Considered Options

* Hexagonal Architecture
* Onion Architecture
* Clean Architecture

## Decision Outcome

Hexagonal Architecture: Although all three options are quite similar, the Hexagonal Architecture approach was the most intuitive for me.
This may of course change during implementation, but I think for now that I do not need any more layers or other concepts.
All in all it consists of a few simple concepts and allows exchanging technical implementations without changing core domain implementation.
If more structure or further concepts are needed when growing the core module, a mix of these architectures may be possible inside core module.

### Positive Consequences

* heavy focus on business domain
* lightweight (starter-) approach on DDD

### Negative Consequences

* needs a little practice getting the concepts right
* freedom in structuring core module also lacks guidelines which also takes additional time until a good design may be found

## Design decisions

As Hexagonal Architecture does give a lot of freedom to design the core module, the following guidelines should be used for a little finer grained design:

* one Gradle module per bounded context
* module naming is
  * application-* for packaging modules
  * adapter-in-* and adapter-out-* for driving and driven adapters
  * domain-* for domain logic and ports

## Further reading

* What a complete (and thus more complex) solution might look
  like: https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/
* Hexagonal Architecture overview and implementation strategies:
  * https://blog.codecentric.de/en/2020/07/hexagon-schmexagon-1/
  * https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749
  * https://reflectoring.io/spring-hexagonal/
* Hexagonal, Onion, Clean: https://www.maibornwolff.de/blog/ddd-architekturen-im-vergleich
