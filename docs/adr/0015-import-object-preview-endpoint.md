# Import Filter Preview: Per-Record Sample Endpoint Reusing FilterEvaluator

* Status: accepted
* Deciders: Chris
* Date: 2026-08-16

Technical Story: [#567 Import UX (2/4): Objekt-Vorschau im Filter-Schritt (Treffer/Ausschluss)](https://github.com/christiangroth/james-platform/issues/567),
[#568 Import UX (3/4): Live-Vorschau im Mapping-Schritt](https://github.com/christiangroth/james-platform/issues/568),
[#544 Importer User Experience verbessern](https://github.com/christiangroth/james-platform/issues/544)

## Context and Problem Statement

The Filter step of the Data Import wizard only ever showed an aggregate count ("x of y records
match"). To see what a rule actually matched or excluded, a User had to save the filter, walk
forward to Mapping and Dry-Run, and read individual source objects there - several steps removed
from where the rule was configured. The goal was an inline "view matches" preview directly under
the rule table, with a matched/excluded toggle and Prev/Next navigation, backed by a small enough
payload that it works for import jobs with many source records, and without duplicating the
filter-matching logic that already exists in `FilterEvaluator`.

## Decision Drivers

* Must reuse `FilterEvaluator.apply()` for "what matches" rather than re-implementing per-rule
  matching in a second code path - any divergence would let the preview show something different
  from what the filter actually does.
* No pagination/cursor infrastructure exists elsewhere for "browse records one at a time" -
  favor the simplest mechanism (a plain 0-based index) over introducing one.
* The same UI building block (Prev/Next, position indicator, JSON card) is meant to be reused for
  the Mapping step's live preview in a follow-up issue, with a different context overlay (source
  vs. mapped target) - so the data contract should be side-agnostic (one shape for both "matched"
  and "excluded") rather than two separate endpoints.
* Payload per request must stay small regardless of how many source records the import job has -
  ruling out returning the full matched/excluded lists in one response.

## Considered Options

1. **New `GET .../filter/sample?matched=&index=` endpoint**, returning exactly one record plus
   the total size of the requested side, computed via `FilterEvaluator.apply()` and a new
   `FilterEvaluator.excluded()` - chosen.
2. **Extend the existing `GET .../filter/values` endpoint** with `matched`/`index` parameters to
   also return sample records alongside distinct field values.
3. **Return the full matched and excluded record lists** (with counts) as part of the Filter
   page's initial server-rendered load, avoiding a follow-up AJAX call entirely.
4. **A dedicated per-record predicate** (e.g. `FilterEvaluator.recordMatches(record, rules)`)
   evaluated independently for each candidate record instead of reusing `apply()`'s pipeline
   fold.

## Decision Outcome

Chosen option 1. `ImportPort.resolveFilterSample(userId, importJobId, matched, index)` returns a
`FilterSample(total: Int, sourceDataJson: String?)`
([`Filter.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/Filter.kt)).
`ImportService.resolveFilterSample`
([`ImportService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/ImportService.kt))
resolves the requested side via `FilterEvaluator.apply()` for matched records, or the new
`FilterEvaluator.excluded()` for excluded ones - the latter reuses `apply()` internally and takes
its complement via an `IdentityHashMap`-backed set, so records with identical content are still
told apart correctly, without a second matching implementation
([`FilterEvaluator.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/FilterEvaluator.kt)).
`sourceDataJson` is `null` whenever `index` falls outside `0 until total` - this uniformly covers
both "index out of range" and "no records on this side at all" without a dedicated error code.

The REST endpoint (`UserImportResource.filterSample`) always answers with HTTP 200 and a
`FilterSampleResponse(total, record)` body, consistent with how every other JSON endpoint in this
codebase represents failure (`ok: false` / `null` payload) rather than HTTP status codes - "import
job not found" and "index out of range" both simply come back as `record: null`. The UI fetches
one record per Prev/Next click (and once when the accordion is first opened), so the payload size
never depends on the import job's total record count.

### Positive Consequences

* The preview can never disagree with the actual filter outcome, since both the aggregate count
  (`FilterView.matchingRecordCount`) and the per-record preview route through the same
  `FilterEvaluator.apply()`.
* The `matched`/`index` contract is side-agnostic and works unchanged for the planned Mapping-step
  reuse - only the surrounding context overlay differs.
* Preview payload size is O(1) regardless of import job size.

### Negative Consequences

* One HTTP round trip per Prev/Next click, with no client-side prefetching of neighboring
  records - acceptable given records are small JSON objects and the UI is used interactively.
* Every sample request re-evaluates the full filter pipeline over all source records rather than
  caching results between requests, same as the pre-existing `getFilterView` and
  `resolveFilterFieldValues`. Consistent with those, and acceptable at this platform's personal-
  use scale.

## Extension: Mapping Step Live Preview (#568)

The Mapping step reuses the same `matched`/`index`-style building block (Prev/Next, position
indicator, side-by-side card) the Decision Drivers above called out - but shows the mapped
*target* object next to the source record, not just the raw source record, and the payload must
reflect the *currently edited*, not yet saved, mapping form state.

* `DryRunExecutor.executeSingle(record, mapping, ...)` is a thin wrapper that calls the existing
  `DryRunExecutor.execute(listOf(record), mapping, ...)` and unwraps the single result
  ([`DryRunExecutor.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/DryRunExecutor.kt)) -
  no parallel mapping/validation implementation, consistent with how `resolveFilterSample` reuses
  `FilterEvaluator` rather than re-implementing matching.
* `ImportPort.resolveMappingSample(userId, importJobId, index, fieldMappings)` returns a
  `MappingSample(total: Int, dryRunObject: DryRunObject?)`
  ([`DryRun.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/DryRun.kt)).
  `fieldMappings` comes straight from the unsaved mapping form (the same request shape
  `updateMapping` already accepts), not from the job's persisted `Mapping` - so editing a rule and
  recalculating never requires saving first.
* The REST endpoint (`UserImportResource.mappingSample`, `POST .../mapping/sample?index=`) is a
  `POST` rather than `GET` because the current, unsaved field mappings have to travel in the
  request body - the `matched` boolean toggle from the filter step has no equivalent here, since
  there is only one "side" (the job's filtered record set) to preview against.
* **Trade-off, called out explicitly**: `execute()`'s in-batch `UniqueKey` fan-in tracking
  (`seenValues`) only ever sees the batch it's given. A batch of one record can therefore never
  detect a collision with *another* record of the same import job - only a collision with data
  already persisted (`existingAppData`) is caught. The full picture, across every record, is still
  what the Dry-Run page shows via the unmodified `execute()` batch call. This is accepted: the
  Mapping-step preview exists to give fast feedback on one record's mapping *shape* (missing
  fields, type/constraint violations, reference resolution), not to be a substitute for a full
  Dry-Run before accepting the import.
* Recalculation stays O(1) per request regardless of import job size, same as the Filter step's
  sample endpoint: only the one record at `index` is mapped and validated, never the whole
  filtered set - the AJAX call triggered by a mapping rule change (debounced client-side) cannot
  turn into a full dry-run by accident.

## Extension: Migration Steps Preview (#721)

The Version editor's Migration section reuses the same index-based `total`/sample building block for a different
pipeline: instead of previewing the Data Import Mapping step against unsaved form state, it previews a draft
Entity's already-*persisted* migration steps and script (see ADR [0023](0023-migration-steps.md)) against one
existing `AppData` object at a time - the interactive preview requested in
[#721](https://github.com/christiangroth/james-platform/issues/721), split out of the Migrationsbausteine work in
[#709](https://github.com/christiangroth/james-platform/issues/709).

* `MigrationPreviewSample(total, previewObject: MigrationPreviewObject?)`
  ([`MigrationPreview.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/MigrationPreview.kt))
  mirrors `MappingSample`. `AppVersionMigrationPort.resolveMigrationPreviewSample(appId, previousEntity, newEntity,
  index)` builds its candidate set the same way `dryRunMigration` does (every `AppData` row of the entity across
  every installation of the App), capped at a fixed sample size so the preview stays responsive regardless of how
  much data an installation holds - unlike `dryRunMigration`/`migrateInstallation`, which read every row.
* **Never short-circuits, unlike every other migration execution path.** `dryRunMigration`/`migrateInstallation`
  both abort on the first failing object, since a real dry-run/persisted migration is all-or-nothing. A preview of
  one already-selected object must instead show *everything* wrong with that one object - so
  `AppVersionMigrationService.previewMigrationSteps`/`previewMigrationObject` are non-short-circuiting siblings of
  `applyMigrationSteps`/`runAndValidate`: a step that cannot be applied is skipped (recorded as a
  `MigrationPreviewIssue.StepFailed`) rather than aborting the remaining steps, and re-validation still runs
  afterwards via the same `AppDataPort.validateEntityData` the real migration paths use, so a preview finding can
  never disagree with what an actual dry-run/publish would find.
* Since migration steps/script are edited via immediately-persisting endpoints (`addMigrationStep`,
  `updateEntityMigrationScript`, ...), unlike the Import Mapping form's client-side-until-saved state, the preview
  endpoint is a plain `GET .../migration-preview/sample?index=` with no request body - it always previews what is
  currently saved on the draft.

## Pros and Cons of the Options

### New dedicated sample endpoint (chosen)

* Good, because it reuses `FilterEvaluator.apply()` with no duplicated matching logic.
* Good, because the response shape is small and constant-size, independent of data set size.
* Good, because `matched`/`index` generalizes cleanly to the planned Mapping-step reuse.
* Bad, because it adds one more endpoint to `UserImportResource` rather than folding the
  capability into an existing one.

### Extend `/filter/values`

* Bad, because that endpoint's existing contract (a flat list of distinct field values for one
  `sourcePath`) is unrelated to "one full record at a position" - bolting both onto one endpoint
  would conflate two different concerns for callers and tests alike.

### Return full matched/excluded lists on page load

* Good, because it needs no follow-up AJAX call at all.
* Bad, because it defeats the point of keeping the preview payload small - a single Filter page
  load would have to embed every source record twice (matched and excluded).
* Bad, because it was not pursued.

### Separate per-record matching predicate

* Bad, because it duplicates `FilterEvaluator.apply()`'s rule-pipeline fold in a second code path
  that could silently drift from the real filter behavior.
* Bad, because it was not pursued - no such implementation exists.

## Links

* Refs [#567](https://github.com/christiangroth/james-platform/issues/567),
  [#568](https://github.com/christiangroth/james-platform/issues/568),
  [#544](https://github.com/christiangroth/james-platform/issues/544),
  [#721](https://github.com/christiangroth/james-platform/issues/721)
* [`FilterEvaluator.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/FilterEvaluator.kt)
* [`DryRunExecutor.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/DryRunExecutor.kt)
* [`ImportService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/ImportService.kt)
* [`UserImportResource.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/UserImportResource.kt)
* [`import-filter.html`](../../adapter-in-web/src/main/resources/templates/ui/user/import-filter.html)
* [`import-mapping.html`](../../adapter-in-web/src/main/resources/templates/ui/user/import-mapping.html)
* [`MigrationPreview.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/MigrationPreview.kt)
* [`AppVersionMigrationService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppVersionMigrationService.kt)
* [`DeveloperAppResource.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/DeveloperAppResource.kt)
* [`version-editor.html`](../../adapter-in-web/src/main/resources/templates/ui/developer/version-editor.html)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
* [arc42: Apps and Versions](../arc42/arc42.md#apps-and-versions)
