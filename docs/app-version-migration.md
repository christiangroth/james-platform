# App Version Migrations — Concept

Status: draft (concept only, not yet implemented)

## 1. Problem Statement

A Developer evolves an App over time by publishing new **Versions** (see [arc42.md](arc42/arc42.md), section "Apps and Versions"). Each Version can add, remove, or change
Entities and Properties. Users install and keep using **App Versions**; their data ("AppData" objects) is expected to keep working across upgrades.

Today, publishing a new Version only changes the *schema* (`AppVersion.entityDefinitions`). Nothing transforms the *data* that already exists for installations of the
previous Version. This raises two related needs (see the issue):

1. **Breaking-change handling** — e.g. a new `NOT NULL` property on an existing Entity has no value on already-existing objects. A Developer should be able to supply a
   migration that fills in a sensible value for existing data, so upgrading doesn't corrupt or orphan it — and, ideally, so the change no longer *needs* to be classified as
   breaking (avoiding an otherwise-mandatory Major version bump).
2. **General migrations** — a Developer should also be able to run a data transformation that has nothing to do with the current Version's schema diff (e.g. normalizing
   values, deduplicating, fixing data produced by a past bug), triggered independently of whether the Version itself changed any Entity.

## 2. Current State

### 2.1 What exists today

- `AppVersion` (`domain-api/.../model/app/AppVersion.kt`) carries `entityDefinitions` (with `Property.default` / `Property.smartDefault`, but those only apply to *new* objects
  created through the UI — they are never applied retroactively to existing `AppData`).
- `AppVersionManagementService.publishVersion()` (`domain-impl/.../app/AppVersionManagementService.kt`) computes whether a draft has **breaking changes**
  (`hasBreakingChanges()`): a removed/renamed Entity or Property, a changed Property type, a Property becoming non-nullable, or a newly added restrictive constraint
  (`isRestrictiveConstraint()`). If breaking, a Major bump is enforced; the Developer has no way to influence this.
- On publish, `autoUpgradeInstallations()` bumps every installation's `installedVersionNumber` **only if the change is non-breaking**. No data is touched. If the change is
  breaking, installations stay on the old Version until the User calls `UserAppStorePort.upgradeApp()` — which *also* only bumps `installedVersionNumber` and never touches
  `AppData` (`UserAppStoreService.kt:145-166`).
- `AppData` (`domain-api/.../model/app/AppData.kt`) already has an `appVersion: VersionNumber` field, but it is only ever set once, at creation
  (`AppDataService.createAppData()`), from the installation's *current* version. `AppDataService.updateAppData()` never updates it — so today it does not even reliably mean
  "created with this version", let alone "last edited with". There is no field at all for "last validated with version".
- Constraint validation (`PropertyConstraintPort.checkValue()`) only ever runs against data the User is actively submitting through `createAppData`/`updateAppData`. Existing
  objects that were valid under an old schema are never re-checked against a new schema.
- `AppDataMigrationPort` / `AppDataMigrationService` (`domain-impl/.../app/AppDataMigrationService.kt`) plus the `Starter`s in `adapter-in-starter` are a **different,
  unrelated** mechanism: one-time, platform-internal, run automatically on every application startup (via `christiangroth/quarkus-one-time-starters`), for fixing the
  *platform's own* bugs/schema drift (e.g. `backfillEntityDisplayText`). They are not Developer-facing and not scoped to a single App or Version. **This concept must use a
  clearly different name** (e.g. `AppVersionMigrationPort`) to avoid confusion with that existing port.
- `ComputedPropertyService` (`domain-impl/.../app/ComputedPropertyService.kt`) already runs Developer-authored Kotlin scripts (JSR-223 `.kts` engine) per Entity, with a
  configurable timeout (`app.script.timeout-ms`, default 500 ms), execution on a dedicated virtual-thread executor so timeouts can actually be interrupted, and metrics via
  `ScriptMetrics`/`ScriptType`. This is a very close analogue for what a migration script needs and should be reused rather than re-invented.

### 2.2 Gaps to close first (prerequisites)

The issue explicitly asks to check whether the prerequisites are in place. They are **not**, today:

| # | Gap | Why it matters for migrations |
|---|-----|--------------------------------|
| 1 | `AppData` has one `appVersion` field, set at creation and never updated | Can't tell whether an object was ever touched (edited or migrated) under a later Version — this is the concrete prerequisite the issue calls out. |
| 2 | No "last validated with version" field | A migration engine needs to know, per object, whether it already satisfies the current Version's schema, to avoid re-running migrations/validation on every read. |
| 3 | No concept of a migration attached to an `AppVersion` | Nothing to author, store, or execute. |
| 4 | `hasBreakingChanges()` / `publishVersion()` have no notion of "a migration compensates for this change" | Required for "avoid a Major bump via migration". |
| 5 | Upgrading an installation (auto or manual) never touches `AppData` | Migrations need a defined execution point. |

Section 4 proposes closing gaps 1–2 as a data-model change, and gaps 3–5 as the migration concept itself.

## 3. Goals

- A Developer can attach one or more migrations to a Version that transform existing `AppData` of that App when an installation upgrades to (or through) that Version.
- A migration that neutralizes what would otherwise be a breaking schema change (e.g. backfilling a new `NOT NULL` property) can allow the Version to be published as
  Feature/Bugfix instead of a forced Major bump — *if* it demonstrably brings all existing data into a valid state.
- A Developer can also attach a migration to a Version that has no Entity/Report changes at all ("general migration"), e.g. to fix up data affected by a past bug.
- Every `AppData` object exposes, at all times, which App Version it was created with, last edited with, and last validated with.
- Migrations run automatically as part of the existing upgrade flow (auto-upgrade for non-breaking Versions, explicit `upgradeApp` for breaking ones) — no separate UI concept
  for "run migration" is required beyond what already exists for upgrading.
- Consistent with the project's constraints (single-developer hobby project, no message brokers/queues, see `docs/coding-guidelines/role-architect.md`): migrations execute
  synchronously as part of the upgrade call, are forward-only (no down-migrations, matching Flyway/Liquibase-style tools), and reuse the existing sandboxed script engine.

## 4. Non-Goals (for this iteration)

- Down-migrations / rollback of a migration.
- Cross-App migrations (a migration only ever operates on the AppData of its own App/Entity).
- A dedicated migration-authoring UI is out of scope for this concept doc; it only defines the domain model and execution semantics. UI wiring follows the existing
  patterns used for computed properties / reports.
- Migrating Report code — Reports are not versioned data, they are re-evaluated live against the current AppData.
- Distributed/queued execution, retries with backoff, or progress UI for large datasets — the platform is single-developer/hobby-scale; a migration runs synchronously in the
  request that triggers the upgrade.

## 5. Concept

### 5.1 Data model: per-object version tracking

Replace the single `AppData.appVersion` field with three explicit fields:

```kotlin
data class AppData(
  val id: AppDataId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val createdWithVersion: VersionNumber,       // was: appVersion — set once at creation, immutable
  val lastEditedWithVersion: VersionNumber,    // updated whenever a User edits the object via createAppData/updateAppData
  val lastValidatedWithVersion: VersionNumber, // updated whenever the object is (re-)validated against an EntityDefinition — including by a migration
  val entityType: EntityDefinitionId,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val data: Map<String, String?>,
)
```

- `createdWithVersion` is what `appVersion` already almost is today — it just needs to stay untouched after creation (already true) and be renamed for clarity.
- `lastEditedWithVersion` is a genuinely new behavior: `AppDataService.updateAppData()` must start stamping it with the installation's *current* `installedVersionNumber`
  at the time of the edit (it currently doesn't touch `appVersion` at all — this is the concrete bug the issue points at).
- `lastValidatedWithVersion` is set to the installation's current version whenever the object passes constraint validation against that version's `EntityDefinition` —
  this happens naturally on every create/update, and additionally after a successful migration run (5.3). It lets the migration engine cheaply skip objects that are already
  known-valid for the target version (`lastValidatedWithVersion == targetVersion` ⇒ skip).

This is a breaking change to `AppDataDocument` (`adapter-out-mongodb`) and needs its own platform-level migration Starter (using the *existing* `AppDataMigrationPort`
mechanism from section 2.1) to backfill `lastEditedWithVersion`/`lastValidatedWithVersion` from the current `appVersion` value for all existing documents.

### 5.2 Migration definition

A migration is authored per **Entity**, alongside its Properties and computed properties, since schema changes (the most common migration trigger) are themselves scoped to
an Entity:

```kotlin
data class EntityDefinition(
  val id: EntityDefinitionId,
  val name: String,
  val displayText: String? = null,
  val properties: List<Property> = emptyList(),
  val sortBy: List<SortCriteria> = emptyList(),
  val computedProperties: List<ComputedProperty> = emptyList(),
  val migrationScript: String? = null, // new: Kotlin script, see 5.3
)
```

A Version's migration is simply "the union of all non-null `migrationScript`s across its `entityDefinitions`, compared against the previous published Version". Keeping it
per-Entity (rather than one script for the whole Version):

- mirrors how schema changes are already scoped (per Entity, not per Version),
- keeps each script small and focused on one Entity's data shape,
- lets the Developer author it right next to the Property changes it compensates for, in the same version-editing UI used for computed properties.

A migration script is optional — most Versions will have none, matching today's default behavior.

### 5.3 Execution model — reuse the computed-property script sandbox

Introduce `AppVersionMigrationService` (new; **not** the existing `AppDataMigrationService`) implementing a new `AppVersionMigrationPort`, structured exactly like
`ComputedPropertyService`:

- Same JSR-223 Kotlin (`.kts`) `ScriptEngineManager`/engine setup, same `app.script.timeout-ms` config, same virtual-thread executor, same `ScriptMetrics` recording (new
  `ScriptType.MIGRATION` entry).
- Script contract: given the object's current `data: Map<String, String?>` (bindings: `it`), the previous `EntityDefinition`, and the new `EntityDefinition`, return the
  transformed `data: Map<String, String?>`. Same wrapping/binding approach as `buildWrappedScript()`.
- After the script returns, the transformed data is run through the **existing** `PropertyConstraintPort.checkValue()` for every property, exactly like `createAppData`/
  `updateAppData` already do. This guarantees a migration can never leave data in a state that violates the new schema — if it does, the migration is treated as failed
  for that object (logged, object left untouched, upgrade for that installation aborted — see 5.4).

Execution point: when an installation's `installedVersionNumber` changes from Version A to Version B (whether via `autoUpgradeInstallations()` for a non-breaking publish,
or via `UserAppStorePort.upgradeApp()`), before the new `installedVersionNumber` is persisted:

1. Collect all Entities' `migrationScript`s across every published Version strictly between A (exclusive) and B (inclusive), in publish order — mirrors how Flyway/Liquibase
   apply all pending migrations in sequence, not just the delta from the immediately-previous version.
2. For each installation's `AppData` rows whose `entityType` has a pending migration and whose `lastValidatedWithVersion` is older than that migration's Version, run the
   script, validate the result, and — on success — save the object with updated `data`, `lastValidatedWithVersion = <that Version>`, `objectVersion += 1`.
3. If any object fails migration or post-migration validation, the whole upgrade for that installation is aborted (no partial state): `installedVersionNumber` is not
   changed, the User/Developer sees an error naming the failing object/Entity, and the installation stays on Version A until the Developer fixes the script or the data.
4. `autoUpgradeInstallations()` (bulk, non-breaking case) runs this per installation; a failure for one installation must not block the others — each is independent.

This keeps the mechanism synchronous and simple (matching the "single-developer hobby project" constraint — no background job queue), while still being safe: nothing is
half-migrated, and a bad script cannot corrupt data because every result is re-validated with the exact same constraint checks used for normal user edits.

### 5.4 Avoiding a Major bump via migration

Extend `computeVersionBump()`/`publishVersion()`:

1. Compute `hasBreakingChanges()` as today (schema-only diff).
2. If breaking **and** the draft Version declares migration scripts for every Entity whose change is what made it breaking, **dry-run** those migrations against *every*
   existing `AppData` row across *all* installations of the App (not just one), reusing 5.3's script + validation logic without persisting anything.
3. If the dry-run succeeds for all existing data, the change is reclassified as non-breaking for versioning purposes: `VersionBumpResult.hasBreakingChanges = false`, and
   the Developer may publish as Feature/Bugfix. The real (persisting) migration then runs per-installation at actual upgrade time (5.3), driven by the now-non-breaking
   publish flow (`autoUpgradeInstallations`).
4. If the dry-run fails for any object, the change remains breaking and a Major bump stays mandatory, exactly as today — the Developer gets clear feedback on which
   object/Entity failed so they can fix the script.

This directly answers "Ggf kann durch eine Migration sogar ein Major Version bump vermieden werden" — the migration must *prove* (by successfully transforming and
validating all existing data at publish time) that it neutralizes the breaking change, rather than being trusted blindly.

### 5.5 General migrations, independent of schema changes

Because a `migrationScript` is just an optional field on `EntityDefinition`, nothing prevents a Developer from adding one to a Version whose `entityDefinitions` and
`reports` are otherwise **unchanged** from the previous Version. Two consequences to handle explicitly:

- `hasAnyChanges()` (which currently gates `publishVersion` with `AppVersionError.NO_CHANGES` when nothing changed) must also consider a changed/added `migrationScript` as
  a change — otherwise a Developer couldn't publish a Version whose only purpose is running a general migration.
- Such a Version is never "breaking" by definition (no Property/Entity actually changed), so it always follows the existing non-breaking auto-upgrade path (5.3 still runs
  the migration for every installation as part of that upgrade).

This covers "Migrationen unabhängig von den Änderungen in der neuen Version ausführen" — a Version bump (Bugfix, typically) becomes the natural, already-existing vehicle for
running a one-off data fix, without inventing a second "run migration now" mechanism outside of versioning.

### 5.6 Error handling

Following the project's `Either<DomainError, T>` convention (`docs/adr/0006-error-handling-concept.md`): migration execution failures during publish (dry-run) and during
upgrade are new `DomainError` variants (e.g. `AppVersionMigrationError.SCRIPT_FAILED`, `AppVersionMigrationError.VALIDATION_FAILED_AFTER_MIGRATION`), carrying enough
information (Entity name, object id, underlying violation) for the web adapter to render a useful message — the same pattern already used for
`AppDataConstraintViolationError`.

## 6. Open Questions

- Should a failing migration for one installation (in the bulk auto-upgrade case) be surfaced to the Developer (who published the Version) or silently retried later? Given
  the "no queue" constraint, the simplest option is: log a warning and leave that one installation on the old Version, visible to its owning User as "update available" the
  same way a manual (breaking) upgrade already behaves. This needs a decision before implementation.
- Dry-running a migration against *all* installations' data at publish time (5.4) could be expensive for a large number of installations/objects. Given this is a
  single-developer/personal-use platform with a small number of Users, this is assumed acceptable, but should be revisited if that assumption changes.
- Whether `lastValidatedWithVersion` should also be bumped just by *viewing* an object (read-time lazy validation) in addition to write-time — this concept only proposes
  write-time and migration-time updates, since read-heavy revalidation would add cost without a clear benefit yet.

Once the design questions above are settled, the concrete execution-trigger decision (synchronous, in-request migration as part of `upgradeApp`/`publishVersion`, versus a
Starter-like background mechanism) is architecturally significant enough to warrant a follow-up ADR, similar to `docs/adr/0006-error-handling-concept.md`.

## 7. Glossary Additions

Extends the Glossary in [arc42.md](arc42/arc42.md):

| Term | Definition |
|------|------------|
| App Version Migration | A Developer-authored Kotlin script attached to an `EntityDefinition` within an `AppVersion`, transforming existing `AppData` when an installation upgrades past that Version. |
| Migration dry-run | Executing a migration against all existing data without persisting results, used at publish time to check whether it neutralizes an otherwise-breaking change. |
| `createdWithVersion` / `lastEditedWithVersion` / `lastValidatedWithVersion` | Per-`AppData` fields recording which `VersionNumber` last created, edited, or successfully validated the object. |
