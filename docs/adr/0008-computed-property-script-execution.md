# Computed Property Scripts: Backend Kotlin Scripting with Timeout Guard

* Status: accepted
* Deciders: Chris
* Date: 2026-05-10

## Context and Problem Statement

A Developer may define a **computed property** on an Entity: a value derived from the entity's
other properties via a piece of code supplied by the Developer, evaluated on every data
read/write. The execution environment for that code was originally left open — backend Kotlin
Script and browser-side JavaScript were both considered, with sandboxing and error-handling
rules deferred (see historical note in `docs/arc42/arc42.md`, prior to this ADR).

Where and how should Developer-supplied computed-property scripts execute, given the Developer
role is trusted (accounts are Admin-created, no self-registration) but scripts must not be able
to hang or crash the request that triggers evaluation?

## Decision Drivers

* Computed properties must be able to reference other properties of the same entity object,
  including other computed properties (evaluated in definition order).
* A slow or infinite-looping script must not block or crash the enclosing HTTP request.
* Implementation and operational simplicity — this is a single-developer, personal-use
  platform, not a multi-tenant system with untrusted script authors.
* Consistency: the same execution model should later extend to Smart Defaults
  (`SmartDefaultService` already reuses the same script engine and timeout config).

## Considered Options

1. **Backend Kotlin Script (JSR-223 `kts` engine), timeout-guarded, no deeper sandbox**
2. **Backend Kotlin Script inside a fully isolated sandbox (separate process/JVM, restricted
   `SecurityManager`, resource limits)**
3. **Browser-side JavaScript, evaluated client-side before submit**

## Decision Outcome

Chosen option: **"Backend Kotlin Script, timeout-guarded, no deeper sandbox"**. Each computed
property's script runs via the JSR-223 `ScriptEngineManager`/Kotlin `kts` engine
(`ComputedPropertyService`), submitted to a virtual-thread executor and bounded by
`future.get(timeoutMs)` (`app.script.timeout-ms`, default 500ms). A timeout or any thrown
exception is caught, logged, recorded as a failed evaluation in `ScriptMetrics`, and yields
`null` for that property — it never fails the enclosing request. There is deliberately no
additional isolation (no restricted classloader, no `SecurityManager`, no separate process):
the Developer role is trusted, matching the existing invite-only access model (see ADR
[0007](0007-local-cookie-based-authentication.md)).

### Positive Consequences

* Full Kotlin language access for script authors — no artificial language subset to document
  or maintain.
* Single execution model reused by both Computed Properties and Smart Defaults.
* Simple, well-understood failure mode: timeout or error → `null` value, logged, metered — the
  request always completes.
* No additional infrastructure (no sandbox process, no container-per-script) to operate.

### Negative Consequences

* A malicious or buggy script has full JVM access within its timeout window — this is an
  accepted risk under the current trust model, **not** a general-purpose sandbox. If Developer
  accounts ever become less trusted (e.g. self-registration, multi-tenant), this decision must
  be revisited before that change ships.
* The same open question directly blocks Reports (`docs/arc42/arc42.md` § Reports), which are
  meant to execute Developer-supplied code too, but — unlike computed properties — Reports are
  specified to run in the browser, not backend-side, so this ADR's timeout-only model does not
  automatically transfer to them.

## Pros and Cons of the Options

### Backend Kotlin Script, timeout-guarded, no deeper sandbox

* Good, because it requires no additional infrastructure.
* Good, because full Kotlin is available, matching the rest of the codebase.
* Good, because failure handling (timeout/error → `null`) is simple and already implemented.
* Bad, because it provides no isolation beyond a time bound — acceptable only under the
  trusted-Developer assumption.

### Backend Kotlin Script inside a fully isolated sandbox

* Good, because it would tolerate a lower-trust Developer population.
* Bad, because it requires substantially more infrastructure (process/JVM isolation, resource
  limits) disproportionate to a single-developer personal project.
* Bad, because it was not pursued — no such infrastructure exists in the codebase today.

### Browser-side JavaScript

* Good, because browser sandboxing is provided by the JS engine itself, for free.
* Bad, because it can't easily share evaluation context with backend-computed values written
  during the same request (e.g. server-side defaults, MongoDB-stored computed results used by
  other backend logic).
* Bad, because it was not pursued — no such implementation exists in the codebase.

## Links

* [`ComputedPropertyService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/ComputedPropertyService.kt)
* [`SmartDefaultService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/SmartDefaultService.kt)
* [arc42: Entities and Properties — Computed properties](../arc42/arc42.md#entities-and-properties)
* [arc42: Reports (status: domain model only)](../arc42/arc42.md#reports)
