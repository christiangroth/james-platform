# Data Import: Separate Reusable Connection from Per-Run Job

* Status: accepted
* Deciders: Chris
* Date: 2026-08-14

Technical Story: [issue #514](https://github.com/christiangroth/james-platform/issues/514)

## Context and Problem Statement

The original Data Import (ETL) design (ADR [0011](0011-import-single-mapping-scope.md)) stored a
source URL and an encrypted Bearer token directly on the `ImportDocument` created for one fetch,
and implicitly scoped every import to the App installation whose route
(`/ui/user/imports/{installedAppId}`) it was triggered from. Re-importing from the same
source (e.g. a nightly refresh) meant re-entering the URL and token every time, and credentials
were only ever reachable through the specific job that first captured them. How should credential
reuse and the job's target be modeled so that credentials survive independently of any one fetch?

Once a job exists, its id (`ImportJobId`) is already globally unique, so its own
lifecycle routes (`select-path`, `mapping`, `dry-run`, `delete`) key purely off that id
(`/ui/user/imports/{importJobId}/...`) rather than also carrying `installedAppId` — only the
job-listing/trigger routes, which precede any job id, stay scoped under
`/ui/user/imports/{installedAppId}`.

## Decision Drivers

* Users regularly re-import from the same source; re-typing a URL and Bearer token for every run
  is repetitive and error-prone, and does not let the platform (or the User) distinguish a "new
  source" from "the same source, run again".
* The `DATA_IMPORT` role must remain the sole gate for both configuring sources and running
  imports against them — no new role is introduced by this split.
* A fetched job's raw payload is inherently short-lived (already deleted once accepted, and
  cleaned up automatically when stale); credentials are not and should not share that lifecycle.

## Considered Options

1. **Split into a reusable `ImportConnection` (name, URL, optional Bearer token) and a per-run
   `ImportJob` that references a connection and fixes its target App/Entity at creation** (shipped)
2. **Keep a single entity, but add a "save as template" action that copies URL/token into a new
   job's form**
3. **Keep a single entity, add an explicit "clone" action on a past job**

## Decision Outcome

Chosen option: **"Split into `ImportConnection` and `ImportJob`"**. An `ImportConnection` holds a
name, URL, and optional encrypted Bearer token; it is created, tested (a real fetch against its
URL/token without persisting anything), and deleted independently of any job, through its own
`DATA_IMPORT`-gated CRUD UI. An `ImportJob` (the renamed former `ImportDocument`) references a
connection by id instead of embedding its own URL/token, and has its target App installation and
target Entity fixed at creation time — the target Entity, previously chosen during the mapping
step, moved up-front since it is now part of what a job's creator explicitly configures alongside
the connection. Everything downstream of fetch (data-path detection, schema detection, mapping,
dry-run, accept, stale-job cleanup) is unchanged; connections are never deleted automatically.

### Positive Consequences

* Re-importing from a known source no longer requires re-entering a URL or token — the User picks
  an existing connection.
* Credentials have a lifecycle independent of any one fetch: deleting a stale job never loses
  access to the source, and a connection can be tested without triggering a full import.
* The target Entity being fixed at job creation removes the entity-selector step from the mapping
  page, slightly simplifying that flow.

### Negative Consequences

* Two entities (and two MongoDB collections, `import_connection` and `import_job`) instead of
  one, with an additional CRUD surface (list/create/edit/delete/test) to maintain.
* Deleting a connection that a not-yet-accepted job still references leaves that job unable to be
  re-fetched (though its already-downloaded payload remains usable for mapping/dry-run/accept).

## Pros and Cons of the Options

### Split into `ImportConnection` and `ImportJob`

* Good, because credentials are reusable and independently manageable (list, test, delete) without
  touching any job.
* Good, because it matches how the User already thinks about the problem: "a source I import
  from" versus "one import run".
* Bad, because it is the largest change of the three options considered (new domain entity, port,
  adapter, and web resource).

### "Save as template" action on a job

* Good, because it requires no new top-level entity or CRUD UI.
* Bad, because a "template" is really a connection wearing a job's clothes — it either needs its
  own storage anyway (re-deriving this ADR's outcome) or keeps re-copying a stale snapshot of
  credentials into new jobs.

### "Clone" action on a past job

* Good, because it is the smallest change — no new entity.
* Bad, because a cloned job still embeds a fresh copy of the credentials rather than referencing
  one reusable, independently deletable record; deleting/rotating a credential still means editing
  N jobs instead of one connection.
* Bad, because a job's short cleanup-driven lifecycle would still apply to credentials, even
  though credentials should outlive any one fetch.

## Links

* [`ImportConnection.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportConnection.kt)
* [`ImportJob.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/imports/ImportJob.kt)
* Refines [ADR-0011](0011-import-single-mapping-scope.md)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
