# Precomputed Read Models per UI Page (Scoped CQRS Exception)

* Status: accepted
* Deciders: Chris
* Date: 2026-08-15

Technical Story: [#511 Precomputed Read Models per UI Page](https://github.com/christiangroth/james-platform/issues/511)

## Context and Problem Statement

UI pages that assemble their view by composing several on-demand queries/aggregations across collections
shaped for write-side concerns — not for what the page actually renders — tend to develop slow-query problems
as data volume grows. When that happens, the natural first response is to fix whichever query was just
reported slow, but that only treats the symptom: the next composed page hits the same structural cause and
produces the next slow-query report.

No page in this project has that problem today. But it is more useful to agree on the architectural answer
before it happens than to improvise one under time pressure the first time a page actually gets slow — an
ad-hoc fix decided in the middle of an incident tends to become the de facto pattern for the rest of the
codebase whether or not it was the best one.

There is an additional constraint specific to this project: an earlier persistent-outbox implementation was
deliberately removed ([#215](https://github.com/christiangroth/james-platform/pull/215): "the outbox pattern...
is no longer needed"). Any rebuild mechanism this decision picks must not reintroduce that complexity.

## Decision Drivers

* `role-architect.md` currently states "No CQRS, no event sourcing" — adopting this pattern requires an
  explicit, scoped exception rather than a quiet violation
* Outbox was explicitly removed from this project as unneeded complexity ([#215](https://github.com/christiangroth/james-platform/pull/215))
  — this decision must not be quietly walked back by whatever rebuild mechanism this ADR picks. CDI events
  are already this project's established async decoupling mechanism (see the existing "No message brokers –
  CDI events are sufficient" rule) and require no new infrastructure
* This is a pre-emptive architectural agreement, not a code change: no page needs it today, so nothing should
  be built until a page actually does

## Decision Outcome

Chosen option: allow **precomputed read models per UI page** as a scoped exception to "No CQRS" — one
document per UI page, rebuilt whenever the underlying data changes, so the page itself becomes a single cheap
document lookup instead of composing several queries per request. Applied only once a specific page is
demonstrably slow because it composes several queries across write-shaped collections — not pre-emptively for
pages that merely could grow slow.

### Rebuild trigger

* Where a read model has exactly one source write and that write isn't fanned out per item, the owning
  domain service calls the read-model rebuild directly, inline, in the same method that performs the write —
  no new event, no new infrastructure
* Where a read model is fed by several unrelated services, or by a write that fans out per item, each source
  fires a CDI event (`jakarta.enterprise.event.Event<T>`) that a single listener uses to trigger the rebuild
* First-time backfill for an existing deployment uses the existing `Starter` mechanism
  (`adapter-in-starter`), calling the same inbound port method the rebuild trigger uses
* Eventual consistency between a write and the next page view is an accepted, visible trade-off

### Naming Convention and Directory Structure

* Read-model repository ports live in `domain-api/port/out/readmodel/*RepositoryPort.kt`
* Adapters live in `adapter-out-mongodb`, one MongoDB collection per read model (`app_<page>_view` naming),
  following the existing `*RepositoryAdapter` pattern
* Rebuild logic lives in the existing domain service already responsible for the underlying data — no new
  service layer

### Positive Consequences

* If a page ever needs this, the fix is already agreed — no fresh architectural debate under incident
  pressure, no risk of an ad-hoc fix hardening into an inconsistent pattern
* Reuses infrastructure this project already has (CDI events, the `Starter` pattern) instead of reintroducing
  outbox, keeping the [#215](https://github.com/christiangroth/james-platform/pull/215) simplification intact

### Negative Consequences

* A second copy of the same data (write-side collections + read-model document) that must be kept in sync by
  the rebuild trigger — a missed trigger means a stale page until the next rebuild
* Inline, synchronous rebuild (the common case here) means the write-path request pays the rebuild cost
  directly; acceptable for this project's current traffic profile, but worth re-checking if that changes

## Links

* Constrained by [#215 Remove outbox entirely](https://github.com/christiangroth/james-platform/pull/215)
