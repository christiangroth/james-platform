# App Version Migrations: Synchronous, In-Request Execution on Upgrade

* Status: partially superseded by [ADR 0019](0019-persistent-outbox-for-long-running-domain-operations.md) — see note below
* Deciders: Chris
* Date: 2026-08-17

Technical Story: [docs/app-version-migration.md](../app-version-migration.md), issue #607 (refs #327)

## Context and Problem Statement

A Developer can attach a Kotlin **migration script** to an Entity within an `AppVersion` (`EntityDefinition.migrationScript`), transforming existing `AppData` so it
keeps satisfying the schema of a newer Version. Nothing about *authoring* a migration script determines *when* it actually runs against an installation's data. When
should pending migrations execute relative to an installation's `installedVersionNumber` being advanced from Version A to Version B?

## Decision Drivers

* An `AppData` object must never be left in a state that fails its current `EntityDefinition`'s constraints — a migration must run (and be validated) before, not after,
  the installation is considered upgraded.
* Two independent call sites need the same behavior: `AppVersionManagementService.autoUpgradeInstallations()` (bulk, on publish of a non-breaking Version) and
  `UserAppStorePort.upgradeApp()` (single installation, User-triggered).
* Consistent with the project's constraints (single-developer/hobby-scale, no message brokers or job queues, see `docs/coding-guidelines/role-architect.md`): no new
  infrastructure should be introduced just for this feature.
* A failure must be isolated to the one installation it affects — `autoUpgradeInstallations()` processes many installations per publish and one bad script must not
  stall or corrupt the others.
* Reuses the same JSR-223 Kotlin sandbox and timeout guard already established for computed properties (see ADR [0008](0008-computed-property-script-execution.md)),
  so no new execution model needs to be introduced.

## Considered Options

1. **Synchronous, in-request migration as part of the upgrade call** (`upgradeApp` / `autoUpgradeInstallations`)
2. **Asynchronous background job**, queued at publish time and processed by a worker
3. **Lazy, read-time migration**, deferred until the `AppData` object is next read

## Decision Outcome

Chosen option: **"Synchronous, in-request migration as part of the upgrade call"**. `AppVersionMigrationService` (`AppVersionMigrationPort.migrateInstallation()`)
runs every pending Entity migration script for an installation's `AppData` — collecting all published Versions strictly between the old and new
`installedVersionNumber`, in publish order — directly inside `UserAppStoreService.upgradeApp()` and `AppVersionManagementService.autoUpgradeInstallations()`, before
either persists the new `installedVersionNumber`. Every migrated object is held in memory and validated via the existing `PropertyConstraintPort` checks; only if
**all** pending objects for that installation migrate and validate successfully are they persisted together with the version bump. A failure (script error, timeout,
or post-migration validation failure) aborts the upgrade for that installation only: nothing is persisted, `installedVersionNumber` stays unchanged, and (in the bulk
auto-upgrade case) processing continues independently for the remaining installations, with a warning logged for the failed one.

### Positive Consequences

* No new infrastructure (queue, worker, scheduler) — migrations run inside the same request that already exists for upgrading.
* Strong consistency: an installation is either fully upgraded (version + all migrated data) or not upgraded at all; there is no intermediate state visible to a User.
* Reuses `ComputedPropertyService`'s established script-execution pattern (timeout, virtual-thread executor, `ScriptMetrics`) — no new execution model to reason about.
* Failure isolation falls out naturally: each installation's migration result is independent, so a `forEach` in `autoUpgradeInstallations()` already isolates failures
  without extra coordination code.

### Relationship to ADR 0019 (Persistent Outbox for Long-Running Domain Operations)

**Partially superseded**, for one of the two call sites this decision covers. `UserAppStorePort.upgradeApp()` (single
installation, User-triggered) is **unchanged**: one installation's migration is bounded work, so synchronous,
in-request execution stays exactly as described below. `AppVersionManagementService.autoUpgradeInstallations()`
(bulk, triggered synchronously from `publishVersion()`) is **superseded**: this decision chose synchronous execution
there specifically because "no job queue/worker...exists in this codebase and is explicitly out of scope for a
single-developer/hobby project" (see Decision Drivers below) — [ADR 0019](0019-persistent-outbox-for-long-running-domain-operations.md)
changes that premise by reintroducing a persistent outbox, scoped narrowly to the operations in series
[#543](https://github.com/christiangroth/james-platform/issues/543). Bulk auto-upgrade now enqueues one outbox
event per installation instead of migrating all installations inline within the publish request; the per-installation
migration logic itself (`AppVersionMigrationPort.migrateInstallation()`, its all-or-nothing validation, and its
failure-isolation semantics) is reused unchanged inside the outbox dispatcher — only *where* it runs (in-request vs.
via the outbox worker) changes.

### Negative Consequences

* A slow migration (many `AppData` rows, or a script close to the timeout for each) directly extends the latency of the upgrade request; there is no progress UI for
  large datasets. Acceptable at the project's current single-user/personal scale (see Non-Goals in `docs/app-version-migration.md`), but would need revisiting if the
  platform ever serves many installations per App.
* `autoUpgradeInstallations()` (bulk, triggered synchronously from `publishVersion()`) now does meaningfully more work per installation than before; a Developer
  publishing a Version with a migration script pays that cost inline as part of the publish request.

## Pros and Cons of the Options

### Synchronous, in-request migration

* Good, because it requires no new infrastructure.
* Good, because "upgraded" is always an all-or-nothing, immediately consistent state.
* Good, because it directly reuses the existing script sandbox and its failure-handling conventions.
* Bad, because it adds latency to the upgrade request proportional to the amount of pending data to migrate.

### Asynchronous background job

* Good, because it would keep the upgrade request itself fast regardless of how much data needs migrating.
* Bad, because it requires a job queue/worker, which does not exist in this codebase and is explicitly out of scope for a single-developer/hobby project.
* Bad, because it introduces a window where `installedVersionNumber` and the actual migrated state of `AppData` are inconsistent, needing extra status/progress
  tracking that the synchronous option avoids entirely.

### Lazy, read-time migration

* Good, because it would spread migration cost across individual reads instead of one upgrade request.
* Bad, because every read path (not just upgrade) would need migration-awareness, multiplying the number of places that must handle script failure.
* Bad, because it contradicts `lastValidatedWithVersion`'s purpose (see `docs/app-version-migration.md` § 5.1) of cheaply knowing an object is already valid — reads
  would need to re-check migration pendingness on every access instead of trusting that field.

## Links

* [`AppVersionMigrationService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppVersionMigrationService.kt)
* [`AppVersionMigrationPort.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/port/in/app/AppVersionMigrationPort.kt)
* [docs/app-version-migration.md](../app-version-migration.md)
* Reuses the execution model from [ADR 0008](0008-computed-property-script-execution.md)
* Follows the error-handling convention from [ADR 0006](0006-error-handling-concept.md)
* Partially superseded by [ADR 0019](0019-persistent-outbox-for-long-running-domain-operations.md) (App Version
  Migrations: `autoUpgradeInstallations()` call site only) — see "Relationship to ADR 0019" above
