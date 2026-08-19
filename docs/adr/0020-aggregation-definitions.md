# Aggregation Definitions: Combining Precomputed Read Models and the Outbox

* Status: accepted
* Deciders: Chris
* Date: 2026-08-19

Technical Story: [#640](https://github.com/christiangroth/james-platform/issues/640) (1/3 of series [#366](https://github.com/christiangroth/james-platform/issues/366))

## Context and Problem Statement

Developers currently have no way to declare a rollup value on an `EntityDefinition` (e.g. "total kilometers run
per running shoe, overall and per month"). Today the closest equivalent, the legacy Report feature, computes
such numbers on demand, at report-execution time, by scanning the underlying `AppData` on every run – acceptable
for a one-off report view, but wasteful and slow once the same rollup is wanted on installation pages or reused
across reports, and it offers no way to keep a value updated incrementally as new data comes in.

`ComputedProperty` ([ADR 0008](0008-computed-property-script-execution.md)) looks superficially similar but
solves a different problem: it computes a value for a single `AppData` instance from a free-form Kotlin script,
run again in full whenever that instance changes. An aggregation is fundamentally different in shape – it
combines many instances (potentially of a *different* Entity, reached via a reference) into one number – and a
free-form script cannot answer "which aggregation is affected by this write?" without executing it, which rules
out both incremental updates on individual writes and static dependency analysis. How should the platform let
Developers declare such rollups, in a way that is inherently inspectable (so writes can trigger only the
aggregations they actually affect) and whose storage and update mechanism reuses this project's two already
-accepted, deliberately narrow exceptions to "no CQRS" rather than opening a third, broader one?

## Decision Drivers

* `role-architect.md` states "No general CQRS or event sourcing beyond the precomputed read models" – an
  aggregation feature must fit inside the two exceptions already carved out ([ADR 0013](0013-precomputed-read-models-per-ui-page.md),
  [ADR 0019](0019-persistent-outbox-for-long-running-domain-operations.md)), not become a third, broader one
* Series [#366](https://github.com/christiangroth/james-platform/issues/366) explicitly ruled out a scripted
  approach (unlike `ComputedProperty`/ADR 0008): an aggregation must be declarative enough that (a) a single
  write can be mapped to exactly the aggregations it affects, for incremental updates in 2/3, and (b) its
  dependency on entities/properties can be determined statically, without executing anything
  * Only `Ref`-depth 1 cross-entity aggregation is in scope for the first iteration – transitive reference
    chains are deliberately deferred, keeping the dependency analysis in this ticket bounded (a fixed set of
    direct references per `AggregationDefinition`, not an open-ended graph walk)
  * True percentiles are deliberately out of scope for the first iteration – they cannot be maintained
    incrementally without either storing the full underlying sample set (defeating the point of a precomputed
    rollup) or an approximating structure (e.g. t-digest), which is more machinery than this project's
    single-developer/hobby scale currently justifies
* This ticket is explicitly foundation-only ("Deklaration + Speicherung"): computing/updating values is
  [#366 2/3](https://github.com/christiangroth/james-platform/issues/366), displaying them is
  [#366 3/3](https://github.com/christiangroth/james-platform/issues/366) – the decision recorded here must not
  presuppose choices that belong to those follow-up tickets (e.g. exactly when a recompute fires)
* Must fit the existing hexagonal architecture: read-model storage is a port in `domain-api`, MongoDB in
  `adapter-out-mongodb`, zero infrastructure leaking into the domain model itself

## Considered Options

1. **Aggregation = read model (ADR 0013) + outbox (ADR 0019), no new exception.** A declarative
   `AggregationDefinition` (function, source property, optional single-hop `Ref` path, optional time bucketing,
   optional group-by) stored as part of `EntityDefinition`; its computed values stored as precomputed read-model
   documents (ADR 0013 storage convention) per `(AggregationDefinition, group, time bucket)`; bulk
   recomputation (e.g. on `AppVersion` publish, or backfill) run through the outbox (ADR 0019) instead of
   in-request, since it can touch an unbounded number of `AppData` objects.
2. **Scripted aggregation, reusing `ComputedProperty`/ADR 0008.** Let Developers write a Kotlin script that
   scans related `AppData` and returns a number.
3. **Full event sourcing / CQRS for aggregate rebuilding.** Introduce a general event stream of domain writes
   that arbitrary read models, including aggregations, subscribe to and replay.
4. **On-demand computation, status quo.** Keep computing aggregation-like numbers at report-execution time, as
   the legacy Report feature already does, without any new declarative model or storage.

## Decision Outcome

Chosen option: **"Aggregation = read model (ADR 0013) + outbox (ADR 0019), no new exception"**. An aggregation
is modeled as the composition of two mechanisms this project already accepted, each for its own narrow reason,
not as a new architectural exception:

* **Declaration and storage side (ADR 0013):** `AggregationDefinition` is added to `EntityDefinition`, analogous
  to `computedProperties` – part of the Entity's declarative shape, versioned with the `AppVersion` draft like
  every other Entity concept. Its resolved values are stored as precomputed read-model documents, following ADR
  0013's existing convention (`domain-api/port/out/readmodel/AggregationRepositoryPort.kt`, MongoDB adapter in
  `adapter-out-mongodb`), each carrying a `status` (`UP_TO_DATE` / `STALE`) so a page can show "update pending"
  instead of a silently outdated number – the same pattern ADR 0013 already established for read models in
  general.
* **Bulk recomputation side (ADR 0019):** whenever a change requires recomputing aggregation values across
  many `AppData` objects at once (e.g. a structural change to an `AggregationDefinition` on `AppVersion`
  publish, or first-time backfill for an existing installation), that recomputation runs through the outbox,
  exactly like the other long-running domain operations ADR 0019 already covers – not synchronously in-request.
  Incremental updates from a single `AppData` write (2/3) are expected to stay inline/CDI-event-driven, the same
  as any other single-source read-model rebuild under ADR 0013; only the *bulk* case needs the outbox.

**Dependency index, not per-write full recompute.** Because `AggregationDefinition` is declarative (a fixed
function + source property + optional single-hop `Ref` path + optional time bucket + optional group-by, never a
script), the set of `AggregationDefinition`s any given write can affect is statically derivable from the
`AppVersion`'s `EntityDefinition`s alone, before any write happens. This ticket builds that dependency index
(entity → aggregations it owns; referenced entity → aggregations that group by it) as a foundation for 2/3, so
that ticket can look up "which aggregations does this write affect" instead of recomputing every
`AggregationDefinition` in the `AppVersion` on every write – the concrete mechanism a scripted approach
(option 2) could never have supported, since a script's dependencies are only knowable by running it.

**No transitive `Ref` chains, no percentiles, in this first iteration.** Both are deliberately out of scope for
the reasons under "Decision Drivers" above; neither is a permanent exclusion, but each would need its own
follow-up decision (unbounded-depth dependency analysis for the former, an approximating data structure for the
latter) before being added.

### Positive Consequences

* No third, broader exception to "no CQRS" – aggregations are explained entirely in terms of the two mechanisms
  already accepted for their own, narrower reasons; anyone who understands ADR 0013 and ADR 0019 already
  understands how aggregations are stored and recomputed.
* The dependency index this ticket introduces lets 2/3 update only the aggregations a write actually affects,
  instead of a full-`AppVersion` recompute per write – a direct, structural consequence of choosing a
  declarative model over a scripted one.
* `status: UP_TO_DATE | STALE` on every read-model value reuses ADR 0013's storage convention outright and gives
  3/3 a ready-made way to show "update pending" instead of inventing new storage semantics.

### Negative Consequences

* Aggregations are visibly less flexible than `ComputedProperty` scripts – no arbitrary logic, only the fixed
  function set (SUM, COUNT, AVG, MIN, MAX, with optional TAG/WOCHE/MONAT/JAHR time bucketing) and single-hop
  `Ref` traversal. A Developer who needs a shape this model cannot express has no scripted fallback within
  aggregations.
* This ticket alone introduces no observable behavior – nothing computes or displays a value yet – its value is
  only realized once 2/3 (computation) and 3/3 (display) land, the same trade-off ADR 0019 accepted for the bare
  outbox port.
* No percentiles and no multi-hop `Ref` chains in the first iteration is a real functional gap versus what a
  Developer might expect from an "aggregations" feature; each would need its own follow-up decision to add.

## Pros and Cons of the Options

### Aggregation = read model (ADR 0013) + outbox (ADR 0019)

* Good, because it introduces zero new architectural surface – both mechanisms, and their constraints, already
  exist and are documented.
* Good, because a declarative model is statically analyzable, which is what makes the dependency index (and
  therefore incremental updates in 2/3) possible at all.
* Bad, because the fixed function set is less expressive than a script – see "Negative Consequences" above.

### Scripted aggregation, reusing `ComputedProperty`/ADR 0008

* Good, because it reuses an existing, already-implemented execution mechanism (the ADR 0008 script sandbox).
* Bad, because a script's dependencies are only knowable by running it – no static "which aggregation does this
  write affect" analysis, which series [#366](https://github.com/christiangroth/james-platform/issues/366)
  explicitly required.
* Bad, because a script also cannot be incrementally updated (e.g. "add this run's kilometers to the running
  total") without re-executing the full aggregation from scratch on every write.

### Full event sourcing / CQRS for aggregate rebuilding

* Good, because a general event stream would support aggregations, and any future read model, uniformly.
* Bad, because it is exactly the "general CQRS/event sourcing" `role-architect.md` rules out, and wildly
  disproportionate to a single-developer/hobby-scale project – the same reasoning ADR 0013 already applied when
  it scoped precomputed read models narrowly instead of adopting general CQRS.

### On-demand computation, status quo

* Good, because it requires no new storage or infrastructure at all.
* Bad, because it does not solve the actual problem – report execution stays slow as data volume grows, and
  there is no way to show a rollup value outside of running a report.

## Links

* Builds on [ADR-0013](0013-precomputed-read-models-per-ui-page.md) (Precomputed Read Models per UI Page) for
  storage of computed aggregation values
* Builds on [ADR-0019](0019-persistent-outbox-for-long-running-domain-operations.md) (Persistent Outbox) for
  bulk recomputation
* Distinguished from [ADR-0008](0008-computed-property-script-execution.md) (Computed Property Script
  Execution) – aggregations are declarative, not scripted, specifically so writes can be mapped to affected
  aggregations statically
* Series: [#366](https://github.com/christiangroth/james-platform/issues/366); this ticket:
  [#640](https://github.com/christiangroth/james-platform/issues/640) (1/3); followed by 2/3 (computation/
  incremental update) and 3/3 (display)
