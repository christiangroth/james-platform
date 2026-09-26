package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class MigrationStepId(val value: String)

/**
 * A declarative migration building block attached to an [EntityDefinition], executed in the defined order — before any
 * [EntityDefinition.migrationScript] — when an installation upgrades past the Version the step is authored in. All variants
 * operate on a Property that already existed (by the same [PropertyId]) in the last published Version, reusing [ValueConversion]
 * for the actual value transformation where a conversion is involved. Only top-level properties are supported; nested `OBJECT`
 * properties and `List` items are out of scope. See docs/adr/0023-migration-steps.md.
 */
sealed interface MigrationStep {
  val id: MigrationStepId

  /** The [propertyId] Property's type changed in place (e.g. `String` to `Long`); converts its already-stored value to the new type. */
  data class ConvertType(override val id: MigrationStepId, val propertyId: PropertyId) : MigrationStep

  /** [sourcePropertyId] (as it existed in the previous published Version) was replaced by the new [targetPropertyId]; copies the value over. */
  data class CopyValue(override val id: MigrationStepId, val sourcePropertyId: PropertyId, val targetPropertyId: PropertyId) : MigrationStep

  /**
   * The [propertyId] Property gained a [Property.unit] or had its `storageGranularity` changed in place; converts its already-stored
   * value from [sourceGranularity] (the granularity the existing raw values are expressed in — the property had no unit before, or a
   * different `storageGranularity`) to the new unit's `storageGranularity`, via [ValueConversion.convertGranularity].
   */
  data class ConvertUnit(override val id: MigrationStepId, val propertyId: PropertyId, val sourceGranularity: Granularity) : MigrationStep

  /**
   * The [propertyId] Property changed from nullable to non-nullable; fills a `null`/blank stored value with [value] if set, otherwise
   * with the Property's own configured default. [value] itself must satisfy the Property's constraints (validated when the step is
   * added/updated), and if `null`, the Property must have a default configured. There is deliberately no fallback to `null` — a value
   * this step cannot fill leaves the change breaking.
   */
  data class FillEmptyValue(override val id: MigrationStepId, val propertyId: PropertyId, val value: String?) : MigrationStep

  /**
   * The [propertyId] Property gained a more restrictive constraint; clamps an out-of-range stored value to the Property's current
   * numeric/date/time min or max constraint, or truncates an over-long `STRING` value to its `maxLength`. A value this cannot
   * meaningfully adjust (e.g. one violating a `Pattern` constraint) is left unchanged, failing re-validation — there is deliberately
   * no fallback to `null`.
   */
  data class AdjustToConstraints(override val id: MigrationStepId, val propertyId: PropertyId) : MigrationStep
}
