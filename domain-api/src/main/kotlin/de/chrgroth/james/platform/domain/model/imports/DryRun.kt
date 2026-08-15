package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId

/**
 * A single finding produced while validating one dry-run object's property value. [staticallyChecked] tells whether
 * this finding's constraint category was already covered by [MappingValidator] during mapping (min/max value,
 * min/max length, missing mandatory field), or whether it is validated here for the first time (in particular
 * Pattern/regex constraints, which [MappingIssue.NotStaticallyValidated] already flagged as deferred to the dry-run).
 * [expected] marks a finding that is a by-design consequence of a "fan-in" mapping (many source records collapsing
 * into few target objects via a `UniqueKey` property) rather than a defect: a record without a value for such a
 * property, or one whose value duplicates a value already used earlier, is meant to be skipped, not fixed.
 */
sealed interface DryRunIssue {
  val targetPropertyId: PropertyId
  val staticallyChecked: Boolean
  val expected: Boolean

  /** The target property is mandatory (non-nullable) but this particular record resolved to no value. */
  data class MissingMandatoryValue(override val targetPropertyId: PropertyId, override val expected: Boolean = false) : DryRunIssue {
    override val staticallyChecked: Boolean = true
  }

  data class ConstraintViolated(
    override val targetPropertyId: PropertyId,
    val violation: PropertyConstraintViolation,
    override val staticallyChecked: Boolean,
    override val expected: Boolean = false,
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

  /** Has issues, but every one of them is [DryRunIssue.expected] — this record is discarded on accept by design (fan-in), not because it needs fixing. */
  val isSkipped: Boolean get() = issues.isNotEmpty() && issues.all { it.expected }

  /** Has at least one issue that is not [DryRunIssue.expected] — this record needs to be fixed before it can be imported. */
  val isInvalid: Boolean get() = issues.any { !it.expected }
}

data class DryRunReport(
  val importJobId: ImportJobId,
  val objects: List<DryRunObject>,
) {
  val totalCount: Int get() = objects.size
  val validCount: Int get() = objects.count { it.isValid }
  val skippedCount: Int get() = objects.count { it.isSkipped }
  val invalidCount: Int get() = objects.count { it.isInvalid }
  val invalidObjects: List<DryRunObject> get() = objects.filter { it.isInvalid }
  val skippedObjects: List<DryRunObject> get() = objects.filter { it.isSkipped }
}

data class DryRunAcceptResult(
  val installedAppId: InstalledAppId,
  val savedCount: Int,
  val discardedCount: Int,
)
