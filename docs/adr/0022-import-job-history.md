# Data Import: Merged Imports UI and Accepted-Job History

* Status: accepted
* Deciders: Chris
* Date: 2026-09-21

Technical Story: [issue #676](https://github.com/christiangroth/james-platform/issues/676)

## Context and Problem Statement

The Import UI was split across two pages: `/ui/user/imports` listed `ImportJob`s (one row per fetch,
regardless of which `ImportDefinition` it belonged to), and `/ui/user/imports/definitions` listed
`ImportDefinition`s (the reusable connection/mapping configuration, see ADR
[0021](0021-import-definition-job-split.md)) separately. Users found the split confusing: it was not
obvious what the difference between an "Import" and an "Import Definition" was, or how the two
related to each other - a definition's in-progress job and its own row lived on different pages with
no visual link between them.

Merging the two pages into one, grouping jobs under their definition, runs into a modeling gap:
`ImportService.acceptDryRun` (via the `AcceptDryRun` outbox handler, `ImportService.handle`) deletes
the `ImportJob` outright once its data is accepted - there is no persistent "finished" state. A
merged page that also wants to show a definition's run history (issue #676 asks for a "Historie"
modal listing past runs) has nothing to list once a job succeeds.

## Decision Drivers

* The two-page split forces users to mentally join two independent lists to understand one workflow
  (fetch a definition's data, then track its job through to completion).
* A one-time (unscheduled) definition's accepted job has no further use in the main list once
  accepted, but users still want to see *that* an import ran and when.
* A scheduled definition runs unattended repeatedly; showing every past run inline would flood the
  main list, but users still want to audit a scheduled definition's run history on demand.
* `ImportCleanupService` already deletes `ImportJob`s purely by `lastChangedAt` age, independent of
  status - reusing it for history retention avoids introducing a second cleanup mechanism.

## Considered Options

1. **Add a terminal `ACCEPTED` `ImportStatus`, keep the accepted `ImportJob` instead of deleting it,
   and merge the two pages into one definition-grouped view with a per-definition "Historie" modal**
   (shipped)
2. **Keep deleting the job on accept, and write a separate lightweight "history" record/collection
   purely for the Historie modal**
3. **Keep the two pages separate, and only add cross-links between a definition and its jobs**

## Decision Outcome

Chosen option: **"Add `ImportStatus.ACCEPTED`, keep the job, merge the pages"**. `ImportStatus` gains
a fifth value, `ACCEPTED`, alongside the existing in-progress values (`DOWNLOADED`, `DATA_IDENTIFIED`,
`READY`, `ACCEPTING`). `ImportService.handle` (the `AcceptDryRun` outbox handler) now saves the job
with `status = ACCEPTED` instead of deleting it, for both the plain-accept and `replaceExisting`
paths (recording `addedCount`, `replacedCount` and `discardedCount` on the accepted job, `null` for
jobs accepted before these existed); the "replace existing" clear-then-reimport branch was already
unaffected by this change since it clears `AppData`, not the `ImportJob`. `ImportCleanupService`'s age-based deletion is left
unchanged - it now doubles as the accepted-job history's retention window, exactly matching the
existing incomplete-job cleanup behavior the issue explicitly called out as reusable.

The web layer merges `UserImportDefinitionResource`'s page-rendering GET endpoints
(`/ui/user/imports/definitions` and its `/table` fragment) into `UserImportResource`'s
`/ui/user/imports` page; the definition resource's POST actions (`run`, `schedule`, `delete`) keep
their existing nested paths, since they are REST actions, not pages, and gains a new
`GET /ui/user/imports/definitions/{id}/history` fragment endpoint. The merged page lists every
`ImportDefinition`, grouping it with its still-in-progress `ImportJob`s (any status other than
`ACCEPTED` - these continue the existing fetch → detect → map → dry-run wizard unchanged) or, for a
scheduled definition with none in progress, the next computed run time. `ACCEPTED` jobs never appear
in this main list; they are only listed, newest first, in the definition's read-only "Historie"
modal, following the same htmx-less fetch-and-inject Bootstrap modal idiom already used by the
Schedule modal. Both lists (`ImportPort.listAllImportJobs`/`listAllImportDefinitions`) are already
loaded in full per user and joined client-side in the web adapter for the existing flat table - grouping
them by `importDefinitionId` is the same kind of join, so no new repository/port method was needed;
this stays adequate given the personal-use, small-data-volume scale documented for this codebase.

### Positive Consequences

* One page instead of two removes the "how do these relate" confusion the issue reported, without
  losing any existing capability (trigger, filter/mapping wizard, schedule, delete are all still
  reachable from the merged page).
* An accepted job's history is now genuinely inspectable per definition, for both one-time and
  scheduled imports, instead of disappearing the moment it succeeds.
* No new MongoDB collection, repository port, or cleanup mechanism - `ACCEPTED` is just another
  `ImportJob` status, and existing age-based cleanup already governs its lifetime.

### Negative Consequences

* `ImportJob` documents now live longer on average (kept until cleanup instead of deleted
  immediately on accept), slightly increasing steady-state `import_job` collection size - acceptable
  at the documented personal-use data volumes and bounded by the existing retention window.
* Call sites that treat "job exists" as "job is actionable" (e.g. the job overview page) now also
  need to consider `ACCEPTED` as a non-actionable terminal state; the merged page's grouping already
  does this by excluding `ACCEPTED` jobs from the in-progress list.

## Pros and Cons of the Options

### Add `ACCEPTED` status, keep the job, merge the pages

* Good, because it reuses the existing `ImportJob` aggregate and its existing age-based cleanup for
  history retention, instead of introducing new storage.
* Good, because the merged page directly addresses the reported confusion by construction (jobs are
  never shown independent of their definition).
* Bad, because `ImportJob`'s lifecycle now has a genuine terminal state to account for at every call
  site that reads `ImportStatus` (mitigated: Kotlin's exhaustive `when` over the enum makes every
  such site a compile-time checklist).

### Separate lightweight history record

* Good, because it would keep `ImportJob` itself exactly as ephemeral as before.
* Bad, because it duplicates most of what an accepted `ImportJob` already holds (date, target,
  status) into a second collection, and still needs its own retention/cleanup mechanism.

### Keep pages separate, only cross-link

* Good, because it is the smallest change.
* Bad, because it does not solve the reported problem - the two lists would still be independently
  navigable, just with extra links between them - and does nothing for the missing history.

## Links

* [`ImportJob.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportJob.kt)
* [`ImportService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/ImportService.kt)
* [`UserImportResource.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/UserImportResource.kt)
* [`UserImportDefinitionResource.kt`](../../adapter-in-web/src/main/kotlin/de/chrgroth/james/platform/adapter/in/web/UserImportDefinitionResource.kt)
* Refines [ADR-0021](0021-import-definition-job-split.md)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
