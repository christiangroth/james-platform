# Migration Steps: Declarative Building Blocks for the Most Common Entity Changes

* Status: accepted
* Deciders: Chris
* Date: 2026-09-26

Technical Story: issue #707 (refs #705, builds on #706), extended by issue #708 (`ConvertUnit`, `FillEmptyValue`, `AdjustToConstraints`)

## Context and Problem Statement

Today the only way to transform existing `AppData` when an Entity changes is a Developer-authored Kotlin **migration script**
(`EntityDefinition.migrationScript`, see ADR [0018](0018-app-version-migration-execution-trigger.md)). Several kinds of change
account for most real-world edits, and all of them are unnecessarily painful with only a script available:

* **Type conversion** — a Property's type changes (e.g. `String` to `Long`). Today the stored values are not converted at all, so
  this is always breaking without a script, even though the conversion itself is simple and mechanical.
* **Value takeover** — a Property was deleted and a new one added in its place, e.g. to change a `long`/`Double` Property's
  immutable `storageGranularity` (see ADR [0016](0016-property-units-storage-granularity.md)), or because the Developer used
  "delete + add" instead of "edit" while restructuring. A plain rename needs no building block at all — `updateProperty` keeps the
  Property's id, and `AppData` is keyed by id — but a genuine delete-and-replace loses the value unless a script copies it over.
* **Unit conversion** (added in issue #708) — a Property gained a `unit` or had its `storageGranularity` changed in place (same
  Property id). The already-stored value is expressed in the old granularity (or in none at all) and must be converted, e.g. `s`
  to `ms`.
* **Filling empty values** (added in issue #708) — a Property changed from nullable to non-nullable. Existing `null`/blank values
  must be filled with a fixed value or the Property's own default before the change can be non-breaking.
* **Adjusting to constraints** (added in issue #708) — a Property gained a more restrictive constraint (a numeric/date/time
  min/max, or a `STRING` `maxLength`). Existing out-of-range values must be clamped or truncated before the change can be
  non-breaking.

All five cases can be expressed declaratively, reusing the type/unit conversion component extracted from the import mapping
pipeline into its own, reusable domain component in [Migrationsbausteine (1/4)](https://github.com/christiangroth/james-platform/issues/706)
(`ValueConversion`, `domain-api/.../model/app/ValueConversion.kt`). How should these declarative building blocks be modeled,
executed, and how should they affect breaking-change classification?

## Decision Drivers

* These changes account for the large majority of real-world Entity edits (see `AppVersionManagementService.isPropertyBreaking`);
  making them declarative should remove the need to hand-write a script for the majority of real changes, without trying to cover
  every possible change (see also [Migrationsbausteine](https://github.com/christiangroth/james-platform/issues/705)'s stated
  goal: make common changes easier, not make every change non-breaking).
* Must compose with the existing script mechanism rather than replace it — Expert Mode (a raw script) remains available for
  anything the building blocks don't cover.
* Must reuse `AppVersionMigrationService`'s existing all-or-nothing, per-installation execution and its breaking-change dry-run
  reclassification (ADR 0018) rather than introduce a second, parallel migration pipeline.
* Must reuse `ValueConversion` (ADR referenced above) for the actual value transformation, not reimplement type/unit conversion a
  third time (import mapping, migration steps).
* Scope must stay bounded: only top-level Properties, not nested `OBJECT` Properties or `List` items, keeping validation and the
  editor UI tractable for the initial cut.

## Considered Options

1. **A sealed `MigrationStep` list on `EntityDefinition`, executed before the script** — `ConvertType(propertyId)` and
   `CopyValue(sourcePropertyId, targetPropertyId)`, both applied via `ValueConversion` before any Expert Mode script sees the data.
2. **Fold type/unit conversion into the script's implicit behavior** (e.g. auto-convert before invoking the script, without a
   separate declarative model)
3. **A more general declarative transformation DSL** (arbitrary field mappings, similar to the import mapping pipeline)

## Decision Outcome

Chosen option: **"A sealed `MigrationStep` list on `EntityDefinition`, executed before the script"**.

* `MigrationStep` (`domain-api/.../model/app/MigrationStep.kt`) is a sealed interface with five variants: `ConvertType(id,
  propertyId)`, `CopyValue(id, sourcePropertyId, targetPropertyId)`, `ConvertUnit(id, propertyId, sourceGranularity)`,
  `FillEmptyValue(id, propertyId, value)` and `AdjustToConstraints(id, propertyId)`. `EntityDefinition.migrationSteps:
  List<MigrationStep>` sits alongside `migrationScript`, authored on the draft relative to the last published Version, exactly
  like the script.
* `AppVersionMigrationService` applies `migrationSteps` in their defined order, then the optional `migrationScript`, to each
  `AppData` object — both stages share the same all-or-nothing per-installation semantics from ADR 0018.
  * `ConvertType` converts a Property's own value in place, from its type/unit in the previous published Version's
    `EntityDefinition` to its type/unit in the new one. `CopyValue` copies a value from a Property that only exists in the
    previous Version into one that only exists in the new one. Both use `ValueConversion.convert`/`convertGranularity` for the
    actual transformation.
  * `ConvertUnit` converts a Property's own value in place from its declared `sourceGranularity` (the granularity the existing raw
    value is expressed in — there being no previous unit, or a different `storageGranularity`) to the new unit's
    `storageGranularity`, via `ValueConversion.convertGranularity`.
  * `FillEmptyValue` fills a `null`/blank stored value with its fixed `value` if set, otherwise with the Property's own configured
    default.
  * `AdjustToConstraints` clamps an out-of-range numeric/date/time value to the Property's current min/max constraint, or
    truncates an over-long `STRING` value to its `maxLength`.
  * A conversion failure (`ConvertType`/`CopyValue`/`ConvertUnit`) aborts that object's migration exactly like a failing script
    would (`AppVersionMigrationStepFailedError`). `FillEmptyValue`/`AdjustToConstraints` never fail at this stage by design —
    deliberately no fallback to `null` — a value they cannot fill/adjust is left as-is and instead fails the shared re-validation
    that already follows step execution, leaving the change breaking.
* `migrateInstallation` treats an Entity with `migrationSteps` the same as one with a `migrationScript` when collecting pending
  migrations across skipped Versions.
* `resolveBreakingChanges` (`AppVersionManagementService`) now requires "script **or** steps" (previously "script") for every
  breaking Entity before attempting the compensating dry-run; a successful dry-run still reclassifies the change as non-breaking,
  and a failing dry-run (including an unconvertible or unadjustable value) leaves it breaking, unchanged from ADR 0018.
* Validation happens at two points: when a step is added/updated (immediate feedback: source Property must exist in the last
  published Version, target must exist in the draft — the same Property for every kind except `CopyValue` — the type pair must be
  convertible via `ValueConversion.isConvertible`, and no two steps may target the same Property), and again at publish time,
  since a step can be invalidated later by deleting the Property it targets — an invalid step blocks publish with
  `InvalidMigrationStepError`, the same pattern as `InvalidAggregationDefinitionError` (ADR
  [0020](0020-aggregation-definitions.md)). `ConvertUnit` additionally requires the target Property to carry a unit whose family
  matches `sourceGranularity`'s; `FillEmptyValue` additionally requires its fixed `value` (if set) to satisfy the target
  Property's own constraints, or — if unset — the target Property to have a default configured (so the fixed value can never
  itself violate the constraints it is meant to satisfy).
* Only top-level Properties are supported; nested `OBJECT` Properties and `List` items are out of scope for this iteration.

### Positive Consequences

* The most common real-world Entity changes (retype a field, replace a field, add/change a unit, tighten nullability or a
  constraint) need no script at all, and no longer force a mandatory Major version bump as long as the existing data actually
  converts/fills/adjusts.
* Reuses `ValueConversion` a second time (after the import mapping pipeline), validating that it is a genuinely shared,
  presentation-agnostic component rather than import-specific code that happened to be extracted.
* No new execution model, error-handling convention, or breaking-change mechanism — building blocks slot into the exact same
  pipeline (`AppVersionMigrationService`, `resolveBreakingChanges`, the outbox-driven auto-upgrade from ADR 0019) that already
  exists for scripts.
* Expert Mode is unchanged and still available for anything building blocks don't cover — building blocks and scripts compose
  (steps run first, feeding the script) rather than being mutually exclusive.

### Negative Consequences

* Two migration mechanisms (building blocks and script) now exist side by side on the same Entity, authored and executed
  together — a Developer must understand the "steps run first, then script" ordering to reason about the combined effect.
* A `MigrationStep` can become invalid after the fact (its target Property gets deleted from the draft), requiring a second
  validation pass at publish time in addition to add/update-time validation — the same structural issue
  `InvalidAggregationDefinitionError` already has for aggregations.
* The scope restriction to top-level Properties means changes inside nested `OBJECT` Properties or `List` items still require a
  script; this is an accepted, explicit limitation rather than a design goal.

## Pros and Cons of the Options

### A sealed `MigrationStep` list, executed before the script

* Good, because it covers the most common changes without any scripting knowledge required.
* Good, because it composes with, rather than replaces, the existing script mechanism.
* Good, because it reuses `ValueConversion` and the existing migration/breaking-change pipeline unchanged.
* Bad, because it adds a second migration concept a Developer must learn alongside the script.

### Fold conversion into implicit script behavior

* Good, because it would need no new domain model or editor UI.
* Bad, because a script is still required for every case, defeating the goal of making common changes scriptless.
* Bad, because implicit auto-conversion before an arbitrary script is harder to reason about than an explicit, orderable step list.

### A general declarative transformation DSL

* Good, because it could eventually cover more cases than just type conversion and value takeover.
* Bad, because it duplicates the import mapping pipeline's own declarative model for a much smaller problem, and significantly
  raises the scope (editor UI, validation, persistence) for building blocks that were meant to stay minimal.
* Bad, because it works against the project's incremental-scope constraints (single-developer/hobby project, see
  `docs/coding-guidelines/role-architect.md`) by front-loading generality nothing has asked for yet.

## Links

* [`MigrationStep.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/MigrationStep.kt)
* [`AppVersionMigrationService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppVersionMigrationService.kt)
* [`AppVersionManagementService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppVersionManagementService.kt)
* [`ValueConversion.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/ValueConversion.kt)
* [`PropertyUnit.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/PropertyUnit.kt)
* Reuses the execution and breaking-change model from [ADR 0018](0018-app-version-migration-execution-trigger.md)
* Reuses the immutable storage granularity concept from [ADR 0016](0016-property-units-storage-granularity.md)
* Follows the publish-time invalidation pattern from [ADR 0020](0020-aggregation-definitions.md)
  (`InvalidAggregationDefinitionError`)
