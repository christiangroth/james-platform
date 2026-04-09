# Persistent Outbox for operations

* Status: accepted
* Deciders: Chris
* Date: 2026-02-24

## Context and Problem Statement

How should the application reliably dispatch external calls and internal domain events with
rate limit resilience, at-least-once delivery, and deduplication?

## Decision Drivers

* At-least-once delivery – no events must be lost on application restart
* Deduplication – the same entity must not be enriched multiple times concurrently
* Domain logic must not depend directly on HTTP or external concerns
* The solution must fit within the existing hexagonal architecture

## Considered Options

1. **Direct API calls from scheduler jobs / domain services**
2. **In-memory queue (CDI events + executor)**
3. **Persistent outbox pattern (MongoDB-backed task queue)**

## Decision Outcome

Chosen option: **"Persistent outbox pattern"**, because it provides at-least-once delivery
across restarts, per-partition rate limit handling (pause/resume on 429), and type-safe
deduplication via outbox event keys — all without coupling the domain to external HTTP concerns.

The outbox is provided as the external library `de.chrgroth.quarkus.outbox`
([christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox)).

### Positive Consequences

* External API calls are fully decoupled from domain logic; the domain only writes events.
* Partition-level throttling and pause-on-rate-limit prevent bulk limit breaches.
* Outbox tasks survive application restarts; at-least-once delivery is guaranteed.
* Deduplication keys prevent duplicate external API calls for the same entity.

### Negative Consequences

* Adds complexity: three partitions, event serialization, and an outbox task dispatcher.
* Adds an external dependency (`de.chrgroth.quarkus.outbox`).
* Debugging requires inspecting the `outbox` and `outbox_archive` MongoDB collections.

## Pros and Cons of the Options

### Direct API calls from scheduler jobs / domain services

* Good, because simple – no extra infrastructure.
* Bad, because rate limits are not handled; a single throttled call blocks the scheduler thread.
* Bad, because no retry on failure; missing data requires manual re-trigger.
* Bad, because violates hexagonal architecture (domain would depend on external HTTP).

### In-memory queue (CDI events + executor)

* Good, because no external dependency.
* Good, because CDI events are already used for internal triggers (SSE notifications).
* Bad, because in-memory queue is lost on restart; at-least-once delivery is not guaranteed.
* Bad, because no built-in deduplication or rate limiting.

### Persistent outbox pattern

* Good, because at-least-once delivery across restarts.
* Good, because partition-level throttling and pause-on-rate-limit.
* Good, because type-safe deduplication via outbox event keys.
* Bad, because adds external dependency and operational complexity.

## Links

* [Hexagonal architecture ADR](0002-backend-hexagonal-architecture.md)
* [quarkus-outbox library](https://github.com/christiangroth/quarkus-outbox)
