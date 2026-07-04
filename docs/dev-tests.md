# Developer Test Data & Testing Concept

## Purpose and Scope

This document addresses [issue #321](https://github.com/christiangroth/james-platform/issues/321): as Developers build increasingly complex Apps (more Entities, more Properties,
more Computed Properties, more Reports), they need a way to test those Apps without hand-typing data through the generic UI every time.

This is **not** about the platform's own test suite (`domain-impl`/`application-quarkus` JUnit tests, see [role-test-engineer.md](coding-guidelines/role-test-engineer.md)). It is
about giving a **Developer** (the role that defines Apps, Entities, and Reports — see [arc42.md](arc42/arc42.md)) a way to generate and manage test data for the Apps they build, so
that:

- Entity/Property definitions can be exercised with realistic data before releasing a Version.
- Computed Properties and Smart Defaults can be checked against a range of inputs, including edge cases.
- Reports can eventually be run against known data sets and their output verified, rather than only being eyeballed manually.

## Options Considered (from the issue)

1. **Automatic test data generator** – derive test data from a Property's type and constraints (`PropertyType`, `PropertyConstraint` in
   `domain-api/.../model/app/EntityDefinition.kt`). Libraries exist for this, but the constraint model here is custom, so generation logic must be custom too.
2. **Manual test data maintained by the Developer** – the Developer curates specific objects (including deliberately tricky edge cases a generator would not think of).
3. **Anonymized real user data** – Users with a real App installation opt in to share their (anonymized) data for testing purposes.

These are not mutually exclusive. The proposal below sequences them: option 1 and 2 are complementary and form the initial scope; option 3 is deferred to a later, separate
decision because of its privacy implications.

## Proposed Approach

### Test Installations

Test data needs a home that is clearly separated from real User data. The existing `Installation` concept (a User's personal instance of an App Version, see the arc42 glossary)
is reused, but flagged as a **test installation**:

- A Developer can create a test installation for any Version of an App they own, without needing a real User account for it.
- Test installations are excluded from normal User-facing listings, sharing, and Report data sources unless explicitly targeted by a test run.
- Test installations follow the same Entity/Property constraint validation as real installations (via `PropertyConstraintPort`) — test data must be valid data, otherwise the test
  proves nothing about the real system.

### Phase 1 – Automatic Test Data Generator

Add a `TestDataGeneratorPort` (inbound port, `domain-api`) that, given an `EntityDefinition` and a desired object count, produces a list of value maps satisfying every
constraint already modelled on `Property`:

- **Type-driven generation** – one generator strategy per `PropertyType` (`LONG`, `DOUBLE`, `BOOLEAN`, `STRING`, `DATE`, `TIME`, `DATETIME`, `DURATION`, `REF`, `LIST`, `OBJECT`).
- **Constraint-aware** – respects `MinLong`/`MaxLong`/`StepLong`, `MinLength`/`MaxLength`/`Pattern`, `MinDate`/`MaxDate`, `MinSize`/`MaxSize`, etc. (the full set already defined as
  `PropertyConstraint` subtypes), plus `nullable` and `UniqueKey`.
- **Nested types** – `LIST` properties generate `MinSize`..`MaxSize` items honoring `listItemType`/`itemConstraints`; `OBJECT` properties recurse into `nestedProperties`.
- **`REF` properties** – point at existing objects of the `targetEntityId` entity within the same test installation; if none exist yet, referenced entities are generated first
  (topologically, which is safe since cyclic `ref` graphs are already rejected at schema-definition time).
- **Computed Properties and Smart Defaults are not generated** – they are derived values, computed the same way they would be for real data (`ComputedPropertyPort`,
  `SmartDefaultPort`), so generated test objects exercise that logic rather than bypassing it.
- **Safety net** – every generated object is run through `PropertyConstraintPort.checkValue` before being stored, so a bug in a generator strategy fails loudly instead of quietly
  producing invalid test data.
- **Reproducibility** – the generator accepts a seed so a Developer (or a future automated Report test) can regenerate the exact same data set.

Developer UI: an action on an Entity ("Generate test data") that asks for object count and an optional seed, then creates the objects in a chosen test installation.

### Phase 2 – Manual Test Data Sets

Generated data covers the "typical" and constraint-boundary cases mechanically, but a Developer will still want to hand-craft specific scenarios (a known-tricky combination of
values, a regression case). For this, a test installation simply supports the same manual create/edit flows Users already have (generic CRUD UI from Entity metadata) — no new
mechanism is required beyond making test installations reachable from the Developer's App view. Generated and manually-entered objects can coexist in the same test installation.

### Phase 3 – Testing Reports (deferred until Report execution is finalized)

The issue explicitly calls out Reports as the next thing to test. Report execution/sandboxing is still an open item in the architecture
(see [arc42.md – Architecture Constraints](arc42/arc42.md), "Reports must be sandboxed" and "concept to be finalised"). Once that execution model exists, a Report test should be
able to:

- Point a Report at a chosen test installation instead of a real one.
- Run the Report's script/HTML the same way it would run for a User.
- Compare output against an expected snapshot or specific assertions the Developer defines.

This phase is intentionally left at the concept level here and should be revisited once Report sandboxing is decided (candidate for its own ADR).

### Deferred – Anonymized Real User Data (option 3)

Letting Users opt in to share anonymized production data for Developer testing raises questions this document does not resolve: consent model, what "anonymized" means for
arbitrary Developer-defined Entities, retention, and revocation. This should not be attempted before Phases 1–2 are in place and proven useful, and needs its own ADR when picked
up.

## Non-Goals

- This is not a general-purpose data-fixture library for the platform's own Kotlin test suite; that is already covered by the existing
  [Test Engineer guidelines](coding-guidelines/role-test-engineer.md).
- No implementation is included in this document — it defines the shape of Phase 1/2 so implementation can be planned as separate, scoped work.

## Open Questions

- Should test installations count against any future per-Developer storage limits?
- How many generated objects is "enough" by default, and should `REF` fan-out have a configurable depth limit to avoid runaway generation?
- Should `Pattern` (regex) constraints support a bounded random-string-from-regex generator, or require the Developer to provide example strings for such properties?
