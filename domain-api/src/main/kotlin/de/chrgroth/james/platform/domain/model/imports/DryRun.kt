package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.PropertyId

/**
 * A single finding produced while validating one dry-run object's property value. [staticallyChecked] tells whether
 * this finding's constraint category was already covered by [MappingValidator] during mapping (min/max value,
 * min/max length, missing mandatory field), or whether it is validated here for the first time (in particular
 * Pattern/regex constraints, which [MappingIssue.NotStaticallyValidated] already flagged as deferred to the dry-run).
 */
sealed interface DryRunIssue {
  val targetPropertyId: PropertyId
  val staticallyChecked: Boolean

  /** The target property is mandatory (non-nullable) but this particular record resolved to no value. */
  data class MissingMandatoryValue(override val targetPropertyId: PropertyId) : DryRunIssue {
    override val staticallyChecked: Boolean = true
  }

  data class ConstraintViolated(
    override val targetPropertyId: PropertyId,
    val violation: PropertyConstraintViolation,
    override val staticallyChecked: Boolean,
  ) : DryRunIssue
}

/** One source record's target object as it would be created by the mapping, with the constraint violations found while validating it. */
data class DryRunObject(
  val index: Int,
  val sourceDataJson: String,
  val targetData: Map<PropertyId, String?>,
  val issues: List<DryRunIssue>,
) {
  val isValid: Boolean get() = issues.isEmpty()
}

data class DryRunReport(
  val importDocumentId: ImportDocumentId,
  val objects: List<DryRunObject>,
) {
  val totalCount: Int get() = objects.size
  val validCount: Int get() = objects.count { it.isValid }
  val invalidCount: Int get() = totalCount - validCount
  val invalidObjects: List<DryRunObject> get() = objects.filterNot { it.isValid }
}

data class DryRunAcceptResult(
  val savedCount: Int,
  val discardedCount: Int,
)
