# Persistent Outbox for Long-Running Domain Operations

* Status: accepted
* Deciders: Chris
* Date: 2026-08-17

Technical Story: [#619](https://github.com/christiangroth/james-platform/issues/619) (1/6 of series [#543](https://github.com/christiangroth/james-platform/issues/543))

## Context and Problem Statement

Several domain operations run entirely synchronously, in-request, today: Data Import (ETL) accept, App
uninstall/data deletion, App deletion, User deletion, and App Version Migration on publish/upgrade. Each
processes a potentially unbounded number of objects (import records, `AppData` documents per installation, or
installations per bulk auto-upgrade) in a single HTTP request. As data volume grows, these risk exceeding
request timeouts, and a crash mid-operation leaves no way to resume – the request either completed atomically
or the user has to notice and retry manually with no partial-progress tracking.

A persistent-outbox mechanism already existed in this project and solved exactly this class of reliability
problem, but was deliberately removed ([#215](https://github.com/christiangroth/james-platform/pull/215):
"the outbox pattern... is no longer needed") because, at the time, nothing in the codebase actually depended
on at-least-once delivery or rate-limit resilience – the external API calls it served (Slack notifications)
were fine as best-effort, direct calls. That "nothing needs it" premise no longer holds for the operations
listed above: growing data volumes make in-request timeouts a real, not hypothetical, risk. How should the
platform execute these long-running operations reliably and resumably, without walking back the simplification
[#215](https://github.com/christiangroth/james-platform/pull/215) achieved for the rest of the system?

The five operations above are the first, concrete instances of a general shape, not a closed list. The platform will keep growing additional long-running domain/business
operations (fachliche Operationen) and technical operations (e.g. maintenance/reindexing jobs) that face the identical in-request-timeout problem – bulk test-data generation
and Report test runs (see [docs/dev-tests.md](../dev-tests.md)) are already-identified future candidates. Deciding the outbox question again from scratch for every such
operation would be wasteful; this ADR is written to authorize the outbox as the platform's standing mechanism for long-running work in general, business or technical, with
the five operations of series #543 as its first adopters.

## Decision Drivers

* [#215](https://github.com/christiangroth/james-platform/pull/215) removed the outbox because it was unused
  complexity, not because the pattern itself was wrong for every future use case – the decision must be
  revisited narrowly for the operations that now actually need it, not treated as permanently closed
* ADR [0013](0013-precomputed-read-models-per-ui-page.md) explicitly relies on CDI events for read-model
  rebuilds and states that "any rebuild mechanism this decision picks must not reintroduce that complexity" –
  this decision must not quietly widen the outbox back into a general-purpose mechanism that swallows that
  ADR's CDI-event choice too
* ADR [0018](0018-app-version-migration-execution-trigger.md) chose synchronous in-request execution for App
  Version Migration specifically because "no job queue/worker...exists in this codebase and is explicitly out
  of scope for a single-developer/hobby project" – this decision directly changes that premise for one of
  ADR 0018's two call sites (see "Relationship to ADR 0018" below) and must say so explicitly rather than
  leaving a stale contradiction between the two documents
* This is an internal domain mechanism, not an external API integration – unlike the previous incarnation
  (which paused per-partition on HTTP 429 from Slack), no rate limiting or throttling is needed here
* Partitions must stay cleanly separated per operation: sharing one partition across unrelated operations
  reintroduces the head-of-line-blocking and cross-operation coupling risk the previous, removed incarnation
  avoided by partitioning per external service – a stuck or backlogged operation must not delay or starve an
  unrelated one competing for the same partition's worker capacity
* Must fit the existing hexagonal architecture: a port in `domain-api`, the persistent implementation in an
  `adapter-out-*` module, zero infrastructure leaking into `domain-api`/`domain-impl`
* Reuse over reinvention: the same external library removed in #215 already solves at-least-once delivery,
  retry with backoff, deduplication, and MongoDB persistence – rebuilding that from scratch would be the kind
  of technical over-engineering `role-architect.md` warns against

## Considered Options

1. **Reintroduce the `de.chrgroth.quarkus.outbox` library**
   ([christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox)), with one cleanly
   separated partition per long-running operation, for the operations identified in series #543 and as a
   general mechanism for future long-running domain/business or technical operations
2. **Keep operations fully synchronous in-request** (status quo) and address timeouts by raising HTTP timeout limits or splitting work across multiple requests
3. **Bespoke, project-local queue** – a hand-rolled MongoDB collection polled by a scheduler job, instead of the external library
4. **General-purpose event bus / message broker** (Kafka, RabbitMQ)

## Decision Outcome

Chosen option: **"Reintroduce `de.chrgroth.quarkus.outbox`, with one cleanly separated partition per operation"**. The same
library removed in [#215](https://github.com/christiangroth/james-platform/pull/215) is added back, split
across two adapter modules per the hexagonal in/out rule (mirroring the same split already proven in the
sister project [spotify-control](https://github.com/christiangroth/spotify-control)): `adapter-out-outbox`
wraps `ApplicationOutboxClient` to enqueue and query tasks (driven by the domain, so outbound); `adapter-in-outbox`
implements `ApplicationOutboxDispatcher`, which a library-managed worker calls to dispatch a claimed task back
into domain inbound ports (drives the domain, so inbound). The domain-facing contract
is `OutboxPort` in `domain-api/port/out/infra`, `DomainOutboxEvent`/`DomainOutboxPartition` in
`domain-api/outbox`. Every distinct long-running operation gets its **own** `DomainOutboxPartition` (e.g.
`DataImport`, `AppUninstall`, `AppDeletion`, `UserDeletion`, `AppVersionMigration`) – partitions are never
shared across unrelated operations, whether they are fachliche/business operations or technical ones. No rate
limiting/throttling is configured on any partition, because none of the operations in scope call an external,
rate-limited API; they only read and write this application's own MongoDB. Retry backoff, deduplication (via
each event's `deduplicationKey`), at-least-once delivery, and startup recovery of stale tasks are all provided
by the library unchanged, per partition.

**Scope: general-purpose for long-running work, narrow for anything else.** This ADR authorizes the outbox as
the platform's standing mechanism for any long-running domain/business or technical operation that would
otherwise run synchronously in-request and risk timing out or losing progress on crash – not only the five
operations identified in series [#543](https://github.com/christiangroth/james-platform/issues/543) (Data
Import (ETL) accept, App uninstall/data deletion, App deletion, User deletion, and App Version Migration on
publish/bulk auto-upgrade). Those five remain the first concrete adopters and the ones with event types defined
by the follow-up tickets in this series (2/6–6/6); a future long-running operation (e.g. bulk test-data
generation or Report test runs, see [docs/dev-tests.md](../dev-tests.md)) may add its own
`DomainOutboxEvent`/`DomainOutboxPartition` without requiring a new or amended ADR, as long as it follows the
partitioning rule below. This is still not a general-purpose event bus for arbitrary messaging or decoupling
between bounded contexts – it exists specifically for the request-timeout/crash-recovery problem stated above,
not as a substitute for CDI events (see "Relationship to ADR 0013" below) or as an integration mechanism with
external systems.

**Partition separation is mandatory.** Every distinct long-running operation gets its own
`DomainOutboxPartition`; operations must never share a partition, whether they are fachliche/business
operations (e.g. User deletion, App uninstall) or technical operations (e.g. a future index-rebuild or
cache-warming job). The library dispatches and retries per partition, so a partition is the unit of failure
isolation and backpressure: if one operation is backlogged, stuck retrying, or has a bug causing repeated
failures, only its own partition's queue grows – unrelated operations, sharing no partition, keep dispatching
at full speed. This supersedes the single shared `Domain` partition (key `"domain"`) from the initial version
of this decision; that design's justification (no operation in scope called a rate-limited external API) was
true but incomplete – queue depth and retry storms are a real isolation concern even without external rate
limits. Partition keys follow the operation name in kebab-case (e.g. `data-import`, `app-uninstall`,
`app-deletion`, `user-deletion`, `app-version-migration`); a new operation defines a new
`DomainOutboxPartition` rather than reusing an existing one, even if it happens to touch the same aggregate or
module as an existing one. Concrete event types are added by the follow-up tickets (2/6–6/6) that actually
route an operation through the outbox; this ticket introduces the port, the adapter, and the library wiring
with no concrete event types yet – the dispatcher has nothing to dispatch until a follow-up ticket adds one.

**Deferred, not part of this decision:** the previous incarnation also had an in-app outbox viewer/health page
(`OutboxViewerResource`, `health.html` partition stats). That observability layer is not reintroduced here – it
added no reliability value by itself and can be added later, by this ADR or a follow-up, once a concrete
operation is actually running through the outbox and there is something real to observe. Note: the previous
incarnation used the module name `adapter-in-outbox` for that viewer; this ADR reuses the same module name for
a different purpose – the `ApplicationOutboxDispatcher` implementation (see "Decision Outcome" above), which
drives the domain and is therefore in scope now, unlike the deferred viewer.

**Archiving is disabled** (`outbox.archive.enabled=false`): the library can copy completed/permanently-failed
tasks into a separate `outbox_archive` collection before deleting them from `outbox`. With no viewer/health
page reintroduced (see above) there is nothing in this project that reads that collection, so keeping it
populated has no observability value yet – completed and permanently failed tasks are deleted outright instead.
This can be revisited together with the deferred viewer once a concrete operation is running through the
outbox and there is a real audience for archived task history.

### Relationship to ADR 0013 (Precomputed Read Models per UI Page)

No change. Precomputed read-model rebuilds continue to use CDI events (or inline calls for single-source
writes) exactly as ADR 0013 describes. None of the five operations in scope here are read-model rebuilds, and
this ADR does not authorize using the outbox for that purpose.

### Relationship to ADR 0018 (App Version Migration: Synchronous, In-Request Execution on Upgrade)

Partially superseded, for one of its two call sites only. ADR 0018 covers two independent call sites:

* `UserAppStorePort.upgradeApp()` (single installation, User-triggered) – **unchanged**. One installation's
  migration is bounded work; synchronous, in-request execution stays exactly as ADR 0018 describes.
* `AppVersionManagementService.autoUpgradeInstallations()` (bulk, triggered synchronously from
  `publishVersion()`) – **superseded**. ADR 0018 chose synchronous execution here specifically because no job
  queue/worker existed; that premise is what this ADR changes. Ticket 6/6 of this series will move bulk
  auto-upgrade to enqueue one outbox event per installation instead of migrating all installations inline
  within the publish request. The per-installation migration logic itself
  (`AppVersionMigrationPort.migrateInstallation()`, its all-or-nothing validation, and its failure-isolation
  semantics) is reused unchanged inside the outbox dispatcher – only *where* it runs (in-request vs. via the
  outbox worker) changes.

### Positive Consequences

* At-least-once delivery and startup recovery mean a crash mid-operation no longer silently loses progress.
* Deduplication keys make it safe to enqueue the same logical operation twice (e.g. a retried request) without
  double-processing.
* Reuses infrastructure this project already had a working integration for – no new operational surface (same
  MongoDB, same library, same GitHub Packages credentials already configured for `quarkus-one-time-starters`).
* Per-operation partitioning contains failures and backlogs to the operation that caused them – a stuck or
  misbehaving operation cannot starve unrelated operations of dispatch capacity, without needing a rate-limit
  state machine (still not needed, since nothing in scope calls a rate-limited external API).
* The outbox is now a standing mechanism: future long-running domain/business or technical operations can adopt
  it by adding an event type and a dedicated partition, without re-litigating this ADR's core decision.

### Negative Consequences

* Reintroduces an external dependency and the operational surface that came with it (the `outbox` MongoDB
  collection; `outbox_archive` exists but stays empty since archiving is disabled, see above), which #215 had
  eliminated.
* Operations routed through the outbox become eventually consistent from the caller's perspective (the
  HTTP response returns once the event is enqueued, not once it is processed) – each follow-up ticket must
  define how its UI communicates "in progress" instead of the previous request/response result.
* This ADR alone introduces no observable behavior change (nothing enqueues yet) – its value is only realized
  once tickets 2/6–6/6 land; until then it is unused infrastructure sitting in the codebase.
* More partitions to track once the deferred observability layer (see above) is eventually added – each
  operation's partition needs to be individually visible, not just the outbox collection as a whole.

## Pros and Cons of the Options

### Reintroduce `de.chrgroth.quarkus.outbox`, one cleanly separated partition per operation

* Good, because it directly solves at-least-once delivery, retry, and deduplication without writing that logic
  from scratch.
* Good, because the library and its GitHub Packages credentials were already integrated once in this project;
  the reintroduction is low-risk, proven infrastructure, not a new unknown.
* Good, because per-operation partitioning without rate-limit/throttle state machines is simpler than the
  previous multi-partition, rate-limit-aware design, while still getting that design's failure-isolation
  benefit – this reintroduction is narrower than what #215 removed, not a like-for-like revert.
* Bad, because it reintroduces external dependency and operational surface that #215 had deliberately removed.

### Keep operations fully synchronous in-request

* Good, because it requires no new infrastructure at all.
* Bad, because it does not solve the actual problem – request timeouts still grow with data volume, and a
  mid-operation crash still leaves no recovery path.

### Bespoke, project-local queue

* Good, because no external dependency.
* Bad, because it would reimplement at-least-once delivery, atomic claim, retry/backoff, and deduplication —
  exactly the complexity the existing library already solved, entirely for the sake of avoiding a dependency
  this project already trusted enough to use once.

### General-purpose event bus / message broker

* Good, because it would scale far beyond this project's needs.
* Bad, because it is explicitly excluded by `role-architect.md` ("No message brokers (Kafka, RabbitMQ)") and
  wildly disproportionate to a single-developer/hobby-scale project with one MongoDB instance.

## Links

* Narrows [#215 Remove outbox entirely](https://github.com/christiangroth/james-platform/pull/215) – this ADR
  reintroduces the library #215 removed, scoped strictly to the operations in series #543, not a like-for-like
  revert
* Does not change [ADR-0013](0013-precomputed-read-models-per-ui-page.md) (Precomputed Read Models per UI Page)
* Partially supersedes [ADR-0018](0018-app-version-migration-execution-trigger.md) (App Version Migrations) for
  the bulk `autoUpgradeInstallations()` call site only
* [christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox) library
* Series: [#543](https://github.com/christiangroth/james-platform/issues/543); this ticket:
  [#619](https://github.com/christiangroth/james-platform/issues/619)
* Scope generalized to any long-running domain/business or technical operation, with mandatory per-operation
  partition separation, per review feedback on PR
  [#634](https://github.com/christiangroth/james-platform/pull/634)
