# Data Import: Separate Reusable Definition from Per-Run Job

* Status: accepted
* Deciders: Chris
* Date: 2026-08-20

Technical Story: [issue #662](https://github.com/christiangroth/james-platform/issues/662)

## Context and Problem Statement

ADR [0012](0012-import-connection-job-split.md) pulled the reusable source connection (URL,
Bearer token) out of `ImportJob` into its own `ImportConnection`, but a job's filter rules and
mapping - the configuration that actually decides *which* records survive and *how* they map onto
the target Entity - still live directly on the `ImportJob`. Since a job is deleted once its dry run
is accepted (`ImportService.handle`, via the `AcceptDryRun` outbox event), that configuration is
lost with it: re-importing from the same source and Entity means rebuilding the filter and mapping
from scratch every time. How should filter/mapping configuration be modeled so it survives an
accepted job and can be reused for a later run?

This is the first of three planned, independently releasable steps (see the issue for parts 2/3
and 3/3): this step only introduces the aggregate split; it does not yet expose reuse in the UI -
`ImportService.triggerImport` still creates a fresh `ImportDefinition` for every job, same as it
always created a job outright.

## Decision Drivers

* Filter rules and mapping represent real user effort (working through field-by-field mapping and
  constraint validation); losing them on every accept forces that work to be redone for a routine
  re-import from the same source.
* An accepted job's raw payload must still be deleted promptly (it can be arbitrarily large and
  data-import users' own responsibility for GDPR-relevant data ends where the target Entity's data
  begins) - only the job's *runtime* state should be deleted on accept, not durable configuration.
* Mirroring the established `ImportConnection`/`ImportJob` split (ADR 0012) keeps the domain model
  consistent: one aggregate for reusable configuration, one for a single run's ephemeral state.

## Considered Options

1. **Split into a reusable `ImportDefinition` (connection, URL postfix, target Entity, selected
   data path, filter rules, mapping) and a per-run `ImportJob` that references a definition and
   keeps only its fetch snapshot and status** (shipped)
2. **Keep filter rules and mapping on `ImportJob`, but skip deleting them on accept and instead
   let a new job "adopt" a prior job's configuration**
3. **Snapshot the filter/mapping configuration into `ImportConnection` itself**

## Decision Outcome

Chosen option: **"Split into `ImportDefinition` and `ImportJob`"**. `ImportDefinition` is a new
aggregate with its own `import_definition` MongoDB collection, repository port, and adapter,
mirroring `ImportConnection`. It holds everything that configures how a source is turned into
target Entity records: `connectionId`, `urlPostfix`, `targetEntityDefinitionId`,
`selectedDataPath`, `filterRules`, and `mapping`. `ImportJob` keeps only what is specific to one
fetch: `payload`, `detectedDataPaths`, `detectedSchema`/`filteredSchema`, `status`, and a reference
`importDefinitionId`. `ImportService.updateFilter`/`updateMapping` now write to the definition
instead of the job; `handle` (the `AcceptDryRun` outbox handler) continues to delete only the
`ImportJob` - the definition is left untouched, ready to be reused once part 2/3 exposes that in
the UI. This step is purely structural: the web layer's external behavior (routes, forms, DTOs) is
unchanged, since `FilterView`/`MappingView` now simply carry the definition alongside the job the
same way they already carry the joined-in `ImportConnection`.

### Positive Consequences

* Filter rules and mapping now have a lifecycle independent of any one fetch, laying the
  groundwork for the reuse UX planned in parts 2/3 and 3/3 without further data-model churn.
* `ImportJob` becomes a strictly smaller, more clearly "ephemeral run state" aggregate, symmetric
  with how `ImportConnection` already relates to it.

### Negative Consequences

* A third entity (and MongoDB collection, `import_definition`) in the Data Import area, alongside
  `ImportConnection` and `ImportJob`.
* Until parts 2/3 and 3/3 ship, every triggered import still creates a new, unreferenced
  `ImportDefinition` rather than reusing one - definitions accumulate with no cleanup, same as
  `ImportConnection` already never being deleted automatically.

## Pros and Cons of the Options

### Split into `ImportDefinition` and `ImportJob`

* Good, because it gives filter/mapping configuration the same independent lifecycle credentials
  already got in ADR 0012, via the same proven pattern.
* Good, because the accept flow (`ImportService.handle`) needs no new outbox dependency - it
  already only ever deleted the `ImportJob`, so leaving the definition alone falls out for free.
* Bad, because it is the larger change of the three options (new aggregate, port, adapter).

### Adopt a prior job's configuration into a new job

* Good, because it requires no new aggregate.
* Bad, because "adopt" still needs somewhere to read a prior configuration from once its job is
  deleted on accept - it either re-derives this ADR's outcome or is limited to jobs that happen to
  still be sitting around unaccepted.

### Snapshot filter/mapping onto `ImportConnection`

* Good, because it avoids a third entity.
* Bad, because a connection can already be paired with many different target Entities and
  filter/mapping configurations (e.g. one API feeding two different Entity types); collapsing them
  onto the connection would force a 1:1 relationship that does not match how connections are
  actually reused today.

## Links

* [`ImportDefinition.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportDefinition.kt)
* [`ImportJob.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportJob.kt)
* Refines [ADR-0012](0012-import-connection-job-split.md)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
