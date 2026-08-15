# App Lifecycle: Non-Blocking Deactivation, Blocking Hard Delete

* Status: accepted
* Deciders: Chris
* Date: 2026-08-15

Technical Story: [#516 App-Lifecycle plan](https://github.com/christiangroth/james-platform/issues/516),
[#540 Deactivate app](https://github.com/christiangroth/james-platform/issues/540),
[#541 Delete app](https://github.com/christiangroth/james-platform/issues/541),
[#542 Document app lifecycle](https://github.com/christiangroth/james-platform/issues/542)

## Context and Problem Statement

Before this, a Developer had no way to retire an App: no way to hide it from the store while
keeping existing Users unaffected, and no way to remove it entirely once it was no longer
needed. The main open question was what should happen to a Developer's request to deactivate
or delete an App when Users still have it installed.

## Decision Drivers

* An action a Developer takes on their own App must not silently break data or functionality
  that a User is actively relying on in their own Installation.
* Irreversible actions (deletion) need an explicit, simple guard rather than ad-hoc checks
  scattered across call sites.
* A cascading force-delete that also removes active Installations/`AppData` would silently
  destroy User data unless the affected Users are notified first — and no notification
  infrastructure (email, in-app alerts, etc.) exists in the platform yet. Building that
  infrastructure was out of scope for this lifecycle work.

## Considered Options

1. **Two-stage lifecycle** (`ACTIVE` ↔ `INACTIVE`, freely reversible and non-blocking) plus a
   separate, blocking Hard Delete that requires zero Installations — chosen.
2. **Single-stage lifecycle**: skip deactivation, only offer Hard Delete, blocked while
   Installations exist.
3. **Force Delete**: allow Hard Delete to cascade to active Installations and their `AppData`,
   deleting a User's data along with the App.

## Decision Outcome

Chosen option 1, implemented as `AppStatus.ACTIVE` / `AppStatus.INACTIVE` on the `App` aggregate
([`App.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/App.kt)),
plus a separate Hard Delete.

**Deactivation** (`AppManagementService.deactivateApp` / `activateApp`) is purely informational
and freely reversible in both directions:

* An `INACTIVE` App is hidden from `UserAppStorePort.listAllPublishedApps()`, so it can no
  longer be *discovered* or newly installed from the store.
* Existing Installations are completely unaffected: they keep working, keep their data, and
  can still be used, upgraded, and imported into exactly as before.
* Affected Users see a purely informational badge/banner (dashboard, app detail, import
  screens) telling them the App was deactivated by its Developer — no functionality is
  blocked or degraded.
* The Developer sees the current count of active Installations when deactivating, as input for
  their own decision-making, but that count never blocks the action.
* Reactivation (`ACTIVE` again) reverses all of the above and re-lists the App in the store.

**Hard Delete** (`AppManagementService.deleteApp`) is blocking and irreversible:

* Blocked with `AppError.HAS_ACTIVE_INSTALLATIONS` (`APP-005`) while
  `InstalledAppRepositoryPort.findAllByAppId()` returns any Installation — independent of the
  App's `ACTIVE`/`INACTIVE` status. A Developer must wait until every User has uninstalled
  before an App can be deleted.
* Once no Installations remain, deletion cascades to the App's `AppVersion`s and then the App
  itself.

**Force Delete is deliberately not implemented.** A variant that cascades Hard Delete onto
active Installations (and their `AppData`) was considered and rejected for now, because it
would delete User data without warning. Introducing it responsibly requires user-notification
infrastructure (so affected Users can be told their data is about to be removed) that the
platform does not currently have. This remains a possible future extension, gated on that
notification infrastructure being built first — not on this ADR.

### Positive Consequences

* A Developer can retire an App from the store, or reverse that decision, without any risk to
  existing Users — deactivation has no failure mode to reason about.
* Hard Delete can never silently destroy a User's data: the guard is a single, simple
  precondition (no Installations) rather than per-call-site checks.
* The domain model stays minimal: one two-value enum plus one blocking precondition, no
  additional lifecycle states.

### Negative Consequences

* A Developer cannot fully remove an App while even one User still has it installed — they can
  only wait, or ask that User to uninstall; there is no administrative override.
* Retiring an App that many Users still have installed effectively stalls at "hidden from the
  store," possibly indefinitely, since there is no path to force completion.

## Pros and Cons of the Options

### Two-stage lifecycle + blocking Hard Delete (chosen)

* Good, because deactivation is a safe, reversible, no-risk action a Developer can take at any
  time to stop new installations.
* Good, because Hard Delete's guard is a single simple precondition, easy to reason about and
  test.
* Bad, because full removal of a still-installed App is not possible without administrative
  override or a force-delete path.

### Single-stage lifecycle (Hard Delete only)

* Good, because it is a smaller domain model — no separate `AppStatus` concept.
* Bad, because it removes the reversible "stop new installs, keep existing Users unaffected"
  step a Developer needs before committing to a permanent, blocking deletion.

### Force Delete cascading to Installations/AppData

* Good, because it would let a Developer fully remove an App at will, regardless of existing
  Installations.
* Bad, because it destroys User data (`AppData`) without warning unless paired with
  notification infrastructure, which does not exist yet.
* Bad, because it was not pursued — no such implementation exists; remains a future option only.

## Links

* Refs [#516](https://github.com/christiangroth/james-platform/issues/516),
  [#540](https://github.com/christiangroth/james-platform/issues/540),
  [#541](https://github.com/christiangroth/james-platform/issues/541)
* [`App.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/App.kt)
* [`AppManagementService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppManagementService.kt)
* [`UserAppStoreService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/UserAppStoreService.kt)
* [arc42: Glossary](../arc42/arc42.md#glossary)
