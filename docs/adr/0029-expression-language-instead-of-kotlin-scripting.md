# Expression Language Instead of Kotlin Scripting

* Status: accepted, supersedes ADR [0008](0008-computed-property-script-execution.md) and ADR [0028](0028-report-kotlin-step.md)
* Deciders: Chris
* Date: 2026-11-03

Technical Story: issue #766 (package 2/2, builds on the expression language of #765)

## Context and Problem Statement

Developer-authored code ran as Kotlin scripts through the JSR-223 engine (`kotlin-scripting-jsr223`) in four places: computed properties and smart
defaults (ADR 0008), Entity migration scripts (ADR 0018/0023) and the report `kotlin` step (ADR 0028). Production runs as a GraalVM native image
(Mandrel), where the Kotlin scripting engine is not available - so every one of them was silently broken in production (no engine, `null` values,
failing migrations), while working in the JVM-based tests. Besides, scripts were expensive (compiled on every evaluation, cold starts close to the
500 ms timeout), had no sandbox beyond the timeout and needed the Developer to know the binding contract (`it`, `computed`, `now`, `config`).

Package 1/2 (#765) built a small, safe expression language for the report `calc` steps. How should the remaining scripting use cases be covered?

## Decision Drivers

* Must work in the native image - no scripting engine, no runtime code generation, no reflection.
* As little scripting as possible: what Developers write should be checkable on save, with clear (German) error messages.
* The real use cases are small calculations on one object (BMI, pace, sums, flags) and convenient starting values for data entry (today, the day
  after the last entry, the last used reference, workdays of a vacation), not general programs.
* Existing stored data (versions with Kotlin scripts) must keep loading.

## Considered Options

1. **Expression language for computed properties and smart defaults, remove migration scripts and the report `kotlin` step**
2. Keep Kotlin scripting and register the engine for the native image
3. Embed another scripting engine (JavaScript via GraalJS, Lua, ...)

## Decision Outcome

Chosen option: **"Expression language for computed properties and smart defaults, remove migration scripts and the report `kotlin` step"**,
because it is the only option that works in the native image without new infrastructure, and the declarative building blocks (expressions, migration
steps, report steps) cover all known use cases.

* **Computed properties** have an expression and a result type (LONG, DOUBLE, STRING, BOOLEAN, DATE, TIME, DATETIME), numeric results optionally a
  unit (a granularity such as `KILOMETERS`, the result is a raw value in that granularity). Expressions read the object's fields by property name
  (nested OBJECT fields as `objekt.feld`, references as the referenced object's id), the computed properties defined before them, `config.<feld>`,
  `today()` and `now()`. They are parsed and type checked on save (unknown fields, wrong operand/argument types, result type) and evaluated in
  definition order whenever an object is shown or read by a report; an expression without value (missing field, division by zero) yields no value.
* **Smart defaults** are expressions evaluated when the create form opens, in property order: they read static defaults, the smart defaults before
  them, `config.<feld>`, `today()`, `now()` and - only here - the entity's latest existing object: `last(feld)` (the field's value there) and
  `next_day(feld)` (the day after it). The latest object is found by the entity's time field (the first DATE/DATETIME property it is sorted by, else
  its first DATE/DATETIME property), then by creation time. Further data-entry functions (also available elsewhere): `add_days(datum, n)`,
  `end_of_month(datum)` and `workdays(von, bis[, feiertage])` (Mondays to Fridays, both ends inclusive, without the given dates - e.g. a configuration
  LIST of dates). REF properties can have a smart default too (e.g. `last(schuhe)`). Smart defaults that read other fields of the form are
  re-evaluated by the form when one of those fields changes, unless the User edited the field himself (e.g. Urlaub: `ende = start`,
  `tage = workdays(start, ende, config.feiertage)`).
* **Migration scripts are removed**; the declarative migration steps of ADR 0023 remain the only way to transform data on upgrade.
* **The report `kotlin` step is removed**; a definition using it fails validation with a dedicated "no longer supported" error. Reports use `calc`
  and the other declarative steps.
* **Removed with it:** the `kotlin-scripting-jsr223` dependency, the script timeout `app.script.timeout-ms`, the script execution metrics and their
  monitoring section.
* **Existing data:** stored Kotlin scripts of computed properties and smart defaults are read as *legacy scripts*: never evaluated (no value), shown
  in the version editor as "Kotlin (ungültig)" with the script, and dropped as soon as the Developer saves an expression (or clears it). Stored
  migration scripts are skipped on read.

### Positive Consequences

* Computed properties and smart defaults work in production (native image), are fast (parsed once, tree evaluation) and cannot hang or access the
  platform - no sandbox or timeout needed.
* Mistakes surface on save with a German message instead of as `null` at runtime.
* The convenience functions make typical data entry (date of the next entry, last used shoes, vacation days) a one-liner.

### Negative Consequences

* Less expressive than Kotlin: no loops or collections. Calculations needing them (e.g. a PV break-even projection) are no longer possible in
  reports; they need a dedicated declarative step if they turn out to be needed.
* Migrations that the declarative steps cannot express are no longer possible; such a change has to be published as a breaking change.
* Expressions refer to properties by name: renaming or deleting a property invalidates expressions using it (they yield no value until fixed, like a
  display text).

## Pros and Cons of the Options

### Expression language, remove migration scripts and the `kotlin` step

* Good, because it works in the native image and needs no extra infrastructure.
* Good, because expressions are validated on save and safe by construction.
* Bad, because it is less expressive than a general purpose language.

### Keep Kotlin scripting and register the engine for the native image

* Good, because nothing would change for Developers.
* Bad, because the Kotlin compiler and scripting host rely on runtime class loading and code generation that a native image cannot provide.
* Bad, because scripts stay slow, unchecked until run and unsandboxed.

### Embed another scripting engine

* Good, because it would offer a full language.
* Bad, because it adds a large dependency (and native image configuration) for a handful of small calculations.
* Bad, because Developers would write code again, which is what the platform wants to avoid.

## Links

* Supersedes [ADR 0008](0008-computed-property-script-execution.md) (computed property scripts) and [ADR 0028](0028-report-kotlin-step.md) (report
  `kotlin` step)
* Removes the migration scripts of [ADR 0018](0018-app-version-migration-execution-trigger.md) and [ADR 0023](0023-migration-steps.md)
* [arc42: Entities and Properties](../arc42/arc42.md#entities-and-properties), [arc42: Reports](../arc42/arc42.md#reports)
* [`Expression.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/expression/Expression.kt),
  [`EntityExpressions.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/expression/EntityExpressions.kt)
