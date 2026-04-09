# Error Handling: Arrow Either<DomainError, T>

* Status: accepted
* Deciders: Chris
* Date: 2026-02-27

## Context and Problem Statement

The application mixes two incompatible error-signalling approaches: ad-hoc sealed result classes
(e.g. `LoginResult`) for some ports, and uncaught exceptions (`check()`, `require()`,
`IllegalStateException`) for others. Failures carry plain `String` messages with no enforced
naming convention, so the frontend cannot map errors to localised messages reliably, and technical
exceptions from infrastructure adapters leak across domain boundaries uncontrolled.

How should the application uniformly represent and propagate domain failures across all ports and
adapters?

## Decision Drivers

* All failures must be type-safe and enumerable at compile time.
* Error codes must be stable and unique so the frontend can map them to localised messages.
* Technical exceptions from infrastructure adapters must not cross port boundaries.
* The pattern must be simple and consistent with the existing hexagonal architecture.
* Rich combinator API for composing multiple fallible operations.

## Considered Options

1. **Custom `DomainResult<T>` + `DomainError` enums (no external dependency)**
2. **Arrow `Either<DomainError, T>` with Arrow Core library**
3. **Kotlin stdlib `Result<T>` with typed exceptions**

## Decision Outcome

Chosen option: **"Arrow `Either<DomainError, T>`"**, because it provides a widely understood
functional abstraction with a rich combinator API (`map`, `flatMap`, `fold`, `raise`), minimal
dependency footprint (only `arrow-core`), and the `either { }` DSL enables readable sequential
error handling that is idiomatic for Kotlin.

### Positive Consequences

* Single, consistent result type across all ports: `Either<DomainError, T>`.
* Errors are compile-time typed; the exhaustive `fold` in Kotlin enforces handling of both
  `Left` (failure) and `Right` (success).
* Each `DomainError` code (e.g. `AUTH-001`) is stable and unique – the frontend can map codes to
  messages without coupling to backend internals.
* Rich combinator API (`map`, `flatMap`, `fold`, `bind`) is provided out of the box.
* Exceptions from infrastructure adapters are caught at the adapter boundary and converted to
  typed `Either.Left` values before crossing the port.
* The `either { }` / `raise` DSL enables sequential, readable error handling in domain services.

### Negative Consequences

* Adds Arrow as an external dependency (`arrow-core`, ~1 MB added to classpath).
* Developers unfamiliar with functional programming must learn `Either` and the `raise` DSL.

## Pros and Cons of the Options

### Custom `DomainResult<T>` + `DomainError` enums

* Good, because no external dependency is added.
* Good, because consistent with existing Kotlin sealed class patterns in the codebase.
* Bad, because combinators (`flatMap`, `zip`, `recover`) must be added manually if needed.

### Arrow `Either<DomainError, T>`

* Good, because `Either` is a widely understood functional abstraction.
* Good, because Arrow provides a rich combinator API and the `either { }` DSL out of the box.
* Good, because `bind()` enables sequential, exception-style code that is still type-safe.
* Good, because only `arrow-core` is required; no heavyweight transitive dependencies.
* Bad, because adds Arrow as an external dependency (~1 MB added to classpath).

### Kotlin stdlib `Result<T>` with typed exceptions

* Good, because no external dependency; `Result<T>` is part of the Kotlin stdlib.
* Bad, because `Result.Failure` carries a `Throwable`, not a typed `DomainError`; error codes
  require an exception hierarchy rather than plain enums.
* Bad, because exception hierarchies for domain errors conflict with the principle that
  domain failures are expected outcomes, not exceptional conditions.

## Links

* [Hexagonal architecture ADR](0002-backend-hexagonal-architecture.md)
* [Arrow documentation](https://arrow-kt.io)
