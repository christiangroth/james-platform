package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class MigrationStepId(val value: String)

/**
 * A declarative migration building block attached to an [EntityDefinition], executed in the defined order — before any
 * [EntityDefinition.migrationScript] — when an installation upgrades past the Version the step is authored in. Both kinds
 * reuse [ValueConversion] to convert the stored value if the source and target [Property.type]/[Property.unit] differ. Only
 * top-level properties are supported; nested `OBJECT` properties and `List` items are out of scope. See
 * docs/adr/0023-migration-steps.md.
 */
sealed interface MigrationStep {
  val id: MigrationStepId

  /** The [propertyId] Property's type changed in place (e.g. `String` to `Long`); converts its already-stored value to the new type. */
  data class ConvertType(override val id: MigrationStepId, val propertyId: PropertyId) : MigrationStep

  /** [sourcePropertyId] (as it existed in the previous published Version) was replaced by the new [targetPropertyId]; copies the value over. */
  data class CopyValue(override val id: MigrationStepId, val sourcePropertyId: PropertyId, val targetPropertyId: PropertyId) : MigrationStep
}
