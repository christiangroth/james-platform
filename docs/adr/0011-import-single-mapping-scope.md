# Data Import Mapping: Single Mapping, Find-Only Reference Lookups

* Status: accepted
* Deciders: Chris
* Date: 2026-07-25

## Context and Problem Statement

An import job holds the detected schema of one fetched data source and needs to be turned
into objects of one target Entity. Two related design questions came up while building the
mapping step of the Data Import (ETL) feature:

1. Should one import job be able to hold more than one mapping (e.g. to fan the same
   source data out into several target Entities, or to chain a mapping's output into another
   mapping's input)?
2. When a target property is a `ref` to another Entity, should the mapping be able to *create*
   a missing referenced object on the fly (`findOrCreate`), or only look up an existing one
   (`find`)?

An earlier iteration of the mapping UI already exposed a "Find" / "Find or Create" selector for
reference properties (see `docs/releasenotes/RELEASENOTES.md`, v0.71.0) before either question
was conclusively settled.

## Decision Drivers

* An import must not have side effects a User did not explicitly review — creating a referenced
  object as an incidental side effect of mapping a different Entity is surprising and bypasses
  the normal create-form validation/UX for that referenced Entity.
* Implementation and conceptual simplicity: chained/multi-target mappings and `findOrCreate`
  both require additional cycle- and ordering-safety reasoning (e.g. what happens if two
  mappings in the same document reference each other) that a single-target, find-only design
  avoids entirely.
* The same outcome as `findOrCreate` is already reachable without it: a User can run a separate
  import (or manual creation) for the referenced Entity first, then import the referencing
  Entity with a `find` lookup against the now-existing data.
* Consistency with the rest of the platform's Entity/Property model, where `ref` properties
  already assume the referenced object exists at write time (see [Data
  types](../arc42/arc42.md#supported-data-types)).

## Considered Options

1. **Single mapping per import job; reference properties resolved via `find`-only lookup**
   (shipped)
2. **Single mapping per import job; reference properties resolved via `find` or
   `findOrCreate`**
3. **Multiple, possibly chained mappings per import job** (fan-out to several target
   Entities, or one mapping's output feeding another)

## Decision Outcome

Chosen option: **"Single mapping per import job; `find`-only reference lookups"**. An
`ImportJob` (named `ImportDocument` until the Import/ETL concept was split into a reusable
`ImportConnection` and a per-run `ImportJob`, see [issue
#514](https://github.com/christiangroth/james-platform/issues/514)) holds at most one `Mapping`
(`List<FieldMapping>`) against its own fixed target Entity; there is no list-of-mappings or
chaining concept. A `FieldMapping` for a
`ref` property may configure a `ReferenceLookup`: every configured criterion's source value must
equal the referenced Entity's corresponding property value for a record to match; on no match,
an optional static `fallbackValue` is used instead. There is deliberately no `findOrCreate`
equivalent — a lookup never creates a referenced object as a side effect. The earlier "Find" /
"Find or Create" selector was removed from the mapping UI in v0.73.1 once it was confirmed to
have no effect on how imports actually ran (`findOrCreate` had never been implemented
differently from `find`).

### Positive Consequences

* No import can create data the User did not explicitly map and review through the normal
  create/dry-run path — every persisted object comes from the same validated Accept step.
* No cycle-detection is needed between lookups, since each lookup is independent and read-only
  (matches the existing "no cyclic `ref` graphs" constraint, see [Architecture
  Constraints](../arc42/arc42.md#architecture-constraints), without adding a second cycle
  concern specific to imports).
* Smaller, easier-to-reason-about domain model and UI: one target Entity, one mapping, per
  import job.

### Negative Consequences

* Importing data that references entities not yet present requires a separate import (or manual
  creation) pass first, then a second import for the referencing Entity — multi-step for the
  User compared to a hypothetical one-shot `findOrCreate` import.
* Importing into several target Entities from a single fetched payload requires triggering the
  import multiple times (once per target Entity), re-fetching or re-using the same source URL
  each time, rather than one import job fanning out to all of them.

## Pros and Cons of the Options

### Single mapping per import job; `find`-only reference lookups

* Good, because no import can create unreviewed data as a side effect.
* Good, because it needs no cycle- or ordering-safety handling between mappings or lookups.
* Bad, because multi-entity imports from one payload require multiple import runs.

### Single mapping per import job; `find` or `findOrCreate`

* Good, because it would let one import fully materialize referenced data that doesn't exist
  yet, in one step.
* Bad, because a lookup could then have a persistence side effect on an Entity the User did not
  map or review in this import — inconsistent with how every other write path on the platform
  works.
* Bad, because it was not pursued past a UI selector: `findOrCreate` was never implemented with
  different behavior from `find`, and the selector was removed once that was confirmed
  (`docs/releasenotes/RELEASENOTES.md`, v0.73.1).

### Multiple, possibly chained mappings per import job

* Good, because it would let one fetched payload populate several target Entities without
  re-fetching.
* Bad, because chaining introduces an ordering/cycle-safety problem not present anywhere else in
  the mapping model.
* Bad, because it was not pursued — no such implementation exists; the shipped domain model
  (`ImportJob.mapping: Mapping?`) only ever holds a single mapping.

## Links

* [`Mapping.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/Mapping.kt)
* [`ImportJob.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportJob.kt)
* [`DryRunExecutor.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/DryRunExecutor.kt)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
