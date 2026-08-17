# Property Units: Fixed Numeric Storage Granularity, Immutable After Creation

* Status: accepted
* Deciders: Chris
* Date: 2026-08-16

Technical Story: [#556 Property Units (1/5): Domänenmodell & Persistierung](https://github.com/christiangroth/james-platform/issues/556), refs [#517 Property Units](https://github.com/christiangroth/james-platform/issues/517)

## Context and Problem Statement

`long`/`Double` properties are getting an optional **Property Unit** (`TIME` or `DISTANCE`), letting a
Developer attach a unit family to a numeric field and a User enter values as text (e.g. `15km 400m`)
instead of a bare number. The platform already has a comparable feature for time spans: the `duration`
data type, which stores a User's textual input verbatim (e.g. `"1d 2h 30m 15s"`) and only parses it into
a `java.time.Duration` on demand for constraint checks and comparisons.

Should Property Units follow that same "store raw text, parse on demand" pattern, or should they always
be converted and stored as a plain number in a fixed granularity?

## Decision Drivers

* `MinLong`/`MaxLong`/`StepLong`/`MinDouble`/`MaxDouble`/`StepDouble` constraints, sorting, and any future
  numeric aggregation (e.g. Reports) need a directly comparable, unit-less number — not a string that has
  to be re-parsed through unit-aware logic every time it is read.
* A `duration` property has exactly one implicit unit family (time) and no per-field choice of storage
  resolution; a Property Unit is explicitly meant to support several granularities per family (e.g.
  millimeters vs. meters vs. kilometers for `DISTANCE`) with a Developer-chosen storage resolution per
  field — raw-text storage would push that resolution choice into every read path instead of fixing it
  once at write time.
* Existing `AppData` values are plain strings (`Map<String, String?>`), read directly by
  `PropertyConstraintService`, sorting, and Reports; introducing a second "this string needs a unit-aware
  parse before use" convention alongside `duration`'s would double the number of implicit parsing rules
  call sites need to know about.

## Considered Options

* **Always store a plain number in a fixed `storageGranularity`** (chosen), converting textual input at
  write time via `UnitFormat`.
* **Store raw text, like `duration`**, and parse it into a number (in some granularity) on every read.

## Decision Outcome

Chosen option: **"Always store a plain number in a fixed `storageGranularity`"**. A `PropertyUnit`
(`family`, `storageGranularity`, `defaultGranularity`) is attached to a `Property` only for `LONG`/`DOUBLE`
types. `storageGranularity` is the field's fixed smallest representable unit; `AppDataService` parses a
User's textual input (`UnitFormat.parseUnitValue`, e.g. `"15km 400m"` → `15400` at `storageGranularity =
METERS`) and persists the resulting number in `storageGranularity`, not the raw text — unlike `duration`.
For a `LONG` field, the converted value must be an integer (e.g. `17,23 km` at `storageGranularity =
METERS` is accepted because it converts to the integral `17230`); a fractional result is rejected.

`storageGranularity` is fixed at field creation and **immutable afterward** — there is no migration
mechanism that would re-convert every existing `AppData` value of that field to a new granularity, so
changing it requires deleting and recreating the property (a breaking change, see [#557 Property Units
(2/5)](https://github.com/christiangroth/james-platform/issues/557)). `defaultGranularity` is only the
granularity pre-selected in the create/edit form and carries no storage meaning, so it may be changed
freely at any time without affecting existing data.

### Positive Consequences

* Every stored value is a plain, immediately comparable number — `MinLong`/`MaxLong`/`StepLong`/
  `MinDouble`/`MaxDouble`/`StepDouble` constraints, sorting, and existing-value uniqueness checks work
  unchanged, with no unit-aware parsing added to any read path.
* A Property Unit's storage resolution is fixed once, at the point the Developer is actually choosing it
  (field creation) — no read path has to guess or agree on which resolution a given value is in.

### Negative Consequences

* `storageGranularity` cannot be changed in place; correcting an overly coarse or fine choice always
  requires recreating the field and losing/migrating its existing data out-of-band, rather than a
  low-friction in-place unit switch.
* Unlike `duration`, the originally entered text (e.g. whether a User typed `15km 400m` or `15400m`) is
  not preserved — only the converted number in `storageGranularity` is stored.

## Pros and Cons of the Options

### Always store a plain number in a fixed `storageGranularity`

* Good, because numeric constraints, sorting, and comparisons need no unit-aware parsing.
* Good, because the storage resolution is decided once, at field-creation time, by the Developer
  configuring the field.
* Bad, because `storageGranularity` becomes immutable, making a resolution correction a breaking,
  recreate-the-field change.

### Store raw text, like `duration`

* Good, because it reuses an already-established pattern (`duration`) and preserves the originally
  entered text.
* Bad, because every numeric constraint check, sort, and comparison would need unit-aware parsing first,
  unlike today's direct `Long`/`Double` comparisons.
* Bad, because `duration` has no per-field storage-resolution choice to begin with, so there is no
  existing precedent for how a raw-text value would encode which of several possible granularities it was
  entered in.

## Links

* [`PropertyUnit.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/PropertyUnit.kt)
* [`UnitFormat.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/UnitFormat.kt)
* [`DurationFormat.kt`](../../domain-api/src/main/kotlin/de/chrgroth/james/platform/domain/model/app/DurationFormat.kt)
* [`AppDataService.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/app/AppDataService.kt)
* [`ScalarValueParsing.kt`](../../domain-impl/src/main/kotlin/de/chrgroth/james/platform/domain/imports/ScalarValueParsing.kt) – Data Import reuses `storageGranularity`/`factorToSmallestUnit` to convert a `FieldMapping.importGranularity` raw value at mapping time, mirroring `DurationConversionUnit`'s conversion.
* [arc42: Supported Data Types](../arc42/arc42.md#supported-data-types)
* [arc42: Data Import (ETL)](../arc42/arc42.md#data-import-etl)
