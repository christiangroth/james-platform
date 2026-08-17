# DURATION Migration and Removal: One-Time Breaking Migration, No Parallel Operation

* Status: accepted
* Deciders: Chris
* Date: 2026-08-17

Technical Story: [#561 Property Units (5/5): Migration von Duration und Entfernung des alten Typs](https://github.com/christiangroth/james-platform/issues/561), refs [#517 Property Units](https://github.com/christiangroth/james-platform/issues/517)

## Context and Problem Statement

Property Units (#556–#559, see [ADR 0016](0016-property-units-storage-granularity.md)) generalize the standalone `duration`
property type into `LONG` + `PropertyUnit(family = TIME)`. Once Property Units are fully usable, `duration` is redundant and
should be removed — but existing `EntityDefinition`s (published and draft) and `AppData` may already contain `duration`
properties and values, stored via the now-superseded `DurationFormat.kt` text format. How should existing data be carried
forward, and should the old and new representations coexist for some transition period, or not at all?

## Decision Drivers

* [ADR 0016](0016-property-units-storage-granularity.md) already establishes that Property Units store a converted plain
  number in a fixed `storageGranularity`, not raw text like `duration` did — the two formats are structurally incompatible,
  so *reading* old `duration` data through the new representation requires an actual data transformation, not just a
  reinterpretation.
* The project is a single-developer/hobby-scale platform (see `docs/coding-guidelines/role-architect.md`) with one real
  deployment — there is no multi-tenant rollout requiring a gradual, backward-compatible transition window.
* Keeping both `DURATION` and `LONG`+`PropertyUnit(TIME)` alive simultaneously would double the number of branches in every
  constraint check, import/mapping conversion, and UI input path this ticket would otherwise delete outright, for a
  transition period that serves no real user.
* Once `PropertyType.DURATION` is removed from the Kotlin enum, a stored `"DURATION"` string can no longer be deserialized
  via `PropertyType.valueOf(...)` — so *some* one-time step has to run before normal read paths are safe again regardless of
  how long a parallel-operation period would otherwise last.

## Considered Options

* **One-time, breaking migration with no parallel operation** (chosen): a startup migration rewrites all stored `DURATION`
  schema and data to `LONG` + `PropertyUnit(TIME)` once; `PropertyType.DURATION` and all its supporting code are deleted in
  the same change.
* **Temporary dual-read support**: keep `DURATION` parsing alive behind a compatibility shim in the Mongo adapter for some
  transition period, migrating lazily on read, before removing it in a later release.
* **Manual, Developer-triggered migration**: ship the new type alongside the old one and require Developers to manually
  convert each `DURATION` field via the UI before `DURATION` is eventually removed in a future breaking release.

## Decision Outcome

Chosen option: **"One-time, breaking migration with no parallel operation"**. A new one-time `Starter`
(`DurationPropertyMigrationStarter`, following the established `EntityDisplayTextMigrationStarter` pattern from
`AppDataMigrationPort`/`AppDataMigrationService`) runs `AppVersionRepositoryAdapter.migrateDurationProperties()`: it walks
the raw Mongo storage documents (`AppVersionDocument`/`PropertyDocument`/`ConstraintDocument`) directly — not through the
domain model — since a stored `"DURATION"` value can no longer be parsed once the enum constant is gone. For every property
still typed `DURATION` (scalar, `LIST` item, or nested inside an `OBJECT` property, at any depth, across every stored
`AppVersion` including drafts), it rewrites `type`/`listItemType` to `LONG`, attaches `PropertyUnit(family = TIME,
storageGranularity = SECONDS, defaultGranularity = MINUTES)` where the domain model supports a unit on that position, and
converts `MinDuration`/`MaxDuration` constraints to `MinLong`/`MaxLong` (value in seconds). It returns the set of migrated
value locations, which `AppDataMigrationService.migrateDurationProperties()` then uses to convert the corresponding stored
`AppData` values from legacy `duration` text (colon-separated or unit-suffixed, the format `DurationFormat.kt` used to
accept) to a plain seconds number — the same representation `AppDataService.unitConvertedValue()` already produces for
Property Unit fields. Both passes are idempotent: re-running finds no remaining `"DURATION"` schema entries or
already-numeric values to convert, so nothing is double-converted.

`PropertyType.DURATION`, `PropertyConstraint.MinDuration`/`MaxDuration`, `DurationFormat.kt`, and every DURATION-specific
branch in the constraint/app-data/version-management services, import mapping (`LONG_TO_DURATION`,
`DurationConversionUnit`), and the developer/user UI are deleted in the same change — there is no code path left that can
read or write the old format after this ships.

Two scoped limitations, both inherited directly from [ADR 0016](0016-property-units-storage-granularity.md)'s decision that
`Property.unit` only applies to top-level `LONG`/`DOUBLE` properties: a `LIST` property whose item type was `DURATION`
migrates its items to plain `LONG` numbers (seconds) **without** a `PropertyUnit`, since the domain model has no per-item
unit; likewise a computed property (`ComputedProperty`) typed `DURATION` migrates to plain `LONG`, since `ComputedProperty`
has no `unit` field at all. Both are pre-existing structural limitations of Property Units, not something this migration
could reasonably fix — extending unit support to list items or computed properties is out of scope for this ticket.

### Positive Consequences

* No permanent dual-format code path — every DURATION-specific branch this ticket found (service layer, import mapping, UI,
  i18n) is deleted outright instead of kept alive behind a compatibility flag indefinitely.
* The migration is self-contained and idempotent, matching the established one-time `Starter` pattern already used for
  other platform-internal backfills.
* `AppData` values end up in exactly the same numeric-seconds representation Property Units already use for other `TIME`
  fields, so no reader needs to know a given `LONG` value was "originally a duration".

### Negative Consequences

* The migration is a hard cutover: an instance that has stored `DURATION` data and has not yet run the migration Starter
  before serving requests that call `AppVersionRepositoryPort.findAll()`/`toDomain()` on affected data would hit a
  `PropertyType.valueOf` failure on legacy `"DURATION"` documents. In practice this is safe for this project — the
  migration Starter is one of very few startup migrations that must run correctly on the platform's single real deployment,
  and CI/dev databases start empty (no `DURATION` documents exist to trigger the failure mode) — but it means this migration
  cannot be deferred or run out-of-band once this change is deployed.
* `LIST<DURATION>` items and computed `DURATION` properties lose the ability to ever carry a `PropertyUnit` after migrating
  to `LONG`, since the current domain model has no field to attach one to at those positions.
* The originally entered duration text (e.g. whether a User typed `1d 2h 30m 15s` or `02:30:15`) is not preserved, only the
  converted seconds value — consistent with how Property Units already handle unit-suffixed input generally.

## Pros and Cons of the Options

### One-time, breaking migration with no parallel operation

* Good, because it deletes all DURATION-specific code in one change instead of carrying it forward indefinitely.
* Good, because it matches the platform's existing one-time-migration pattern and hobby-scale/single-deployment context.
* Bad, because it requires the migration Starter to run correctly, in the right relative order, before this deploy's other
  startup migrations touch `AppVersion` data — acceptable here given the very small number of platform-internal migrations
  and the single real deployment, but would not scale to a multi-tenant rollout without more migration-ordering guarantees.

### Temporary dual-read support

* Good, because it removes the hard requirement that the migration Starter runs before any other code touches `AppVersion`
  data.
* Bad, because it doubles the number of branches (constraint checks, import mapping, UI) this ticket would otherwise delete,
  for a transition window that serves no real user on a single-deployment platform.
* Bad, because it delays, rather than avoids, eventually writing and running the exact same one-time migration.

### Manual, Developer-triggered migration

* Good, because it lets the Developer choose exactly when to convert each field.
* Bad, because it leaves `DURATION` and Property Units coexisting in the domain model and every consuming code path for an
  indefinite period, contradicting the goal of this ticket (`PropertyType.DURATION` must not exist in code afterward).
* Bad, because it adds UI/UX work for a migration that a startup Starter can already perform automatically and correctly.

## Links

* [ADR 0016: Property Units storage granularity](0016-property-units-storage-granularity.md)
* [`AppVersionRepositoryAdapter.kt`](../../adapter-out-mongodb/src/main/kotlin/de/chrgroth/james/platform/adapter/out/mongodb/AppVersionRepositoryAdapter.kt) – `migrateDurationProperties()`
* [`AppDataMigrationService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppDataMigrationService.kt) – `migrateDurationProperties()`
* [`DurationPropertyMigrationStarter.kt`](../../adapter-in-starter/src/main/kotlin/de/chrgroth/james/platform/adapter/in/starter/DurationPropertyMigrationStarter.kt)
* [arc42: Property Units](../arc42/arc42.md#property-units)
