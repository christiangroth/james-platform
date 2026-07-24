package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType

/**
 * A single finding produced while validating a [Mapping] against the target [EntityDefinition] and the source
 * document's detected schema statistics. [NotStaticallyValidated] is informational only (it never blocks the
 * [ImportStatus.READY] transition), all other variants are blocking.
 */
sealed interface MappingIssue {
  val targetPropertyId: PropertyId

  /** The target property is mandatory (non-nullable) but has neither a mapped, always-present source field nor a fallback value. */
  data class MissingMandatoryField(override val targetPropertyId: PropertyId) : MappingIssue

  /** At least one value type observed in the source data for the mapped field cannot be mapped to the target property type, even with the configured conversion. */
  data class IncompatibleType(override val targetPropertyId: PropertyId, val sourceType: SchemaPropertyType, val targetType: PropertyType) : MappingIssue

  /** The source field's observed numeric range (min/max) violates a MinLong/MaxLong/MinDouble/MaxDouble constraint of the target property. */
  data class NumericRangeViolation(
    override val targetPropertyId: PropertyId,
    val constraintMin: Double?,
    val constraintMax: Double?,
    val observedMin: Double,
    val observedMax: Double,
  ) : MappingIssue

  /** The source field's observed string lengths violate a MinLength/MaxLength constraint of the target property. */
  data class StringLengthViolation(
    override val targetPropertyId: PropertyId,
    val constraintMin: Int?,
    val constraintMax: Int?,
    val observedMinLength: Int,
    val observedMaxLength: Int,
  ) : MappingIssue

  /** The target property has a Pattern constraint that cannot be statically checked against the detected schema; it is only validated during dry-run. */
  data class NotStaticallyValidated(override val targetPropertyId: PropertyId, val regex: String) : MappingIssue
}

data class MappingValidationResult(
  val issues: List<MappingIssue>,
) {
  val blockingIssues: List<MappingIssue> get() = issues.filterNot { it is MappingIssue.NotStaticallyValidated }
  val notStaticallyValidated: List<MappingIssue.NotStaticallyValidated> get() = issues.filterIsInstance<MappingIssue.NotStaticallyValidated>()
  val isReady: Boolean get() = blockingIssues.isEmpty()
}

/** Bundles everything the mapping UI needs to render: the import document, all entity definitions available as mapping targets, and the current mapping's validation result (null if no mapping has been configured yet). */
data class MappingView(
  val importDocument: ImportDocument,
  val entityDefinitions: List<EntityDefinition>,
  val validation: MappingValidationResult?,
)
