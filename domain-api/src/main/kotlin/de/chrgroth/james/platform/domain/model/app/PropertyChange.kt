package de.chrgroth.james.platform.domain.model.app

/**
 * A kind of change to a Property between the last published Version and the draft that makes the change breaking unless a
 * matching [MigrationStep] compensates for it. See [propertyChangeReasons] and docs/adr/0023-migration-steps.md.
 */
enum class PropertyChangeReason { TYPE_CHANGED, BECAME_REQUIRED, CONSTRAINT_TIGHTENED, UNIT_ADDED_OR_CHANGED, UNIT_REMOVED }

/**
 * The reasons [draft]'s change from [published] (the same Property, by id, as it existed in the last published Version) is
 * breaking - empty if the change does not affect breaking-change classification. The single source of truth for
 * `AppVersionManagementService.isPropertyBreaking`/the breaking-change badges and for migration-step suggestions alike - see
 * docs/adr/0023-migration-steps.md.
 */
fun propertyChangeReasons(published: Property, draft: Property): Set<PropertyChangeReason> {
  val reasons = mutableSetOf<PropertyChangeReason>()
  if (draft.type != published.type) reasons += PropertyChangeReason.TYPE_CHANGED
  if (published.nullable && !draft.nullable) reasons += PropertyChangeReason.BECAME_REQUIRED
  val addedConstraints = draft.constraints - published.constraints
  if (addedConstraints.any { it.isRestrictive() }) reasons += PropertyChangeReason.CONSTRAINT_TIGHTENED
  val publishedUnit = published.unit
  val draftUnit = draft.unit
  when {
    publishedUnit == null && draftUnit != null -> reasons += PropertyChangeReason.UNIT_ADDED_OR_CHANGED
    publishedUnit != null && draftUnit == null -> reasons += PropertyChangeReason.UNIT_REMOVED
    publishedUnit != null && draftUnit != null &&
      (publishedUnit.family != draftUnit.family || publishedUnit.storageGranularity != draftUnit.storageGranularity) ->
      reasons += PropertyChangeReason.UNIT_ADDED_OR_CHANGED
  }
  return reasons
}

/** Whether [this] constraint makes a Property strictly harder to satisfy than before it was added (used by [propertyChangeReasons]). */
private fun PropertyConstraint.isRestrictive(): Boolean = when (this) {
  is PropertyConstraint.UniqueKey -> false
  is PropertyConstraint.MinLong,
  is PropertyConstraint.MaxLong,
  is PropertyConstraint.StepLong,
  is PropertyConstraint.MinDouble,
  is PropertyConstraint.MaxDouble,
  is PropertyConstraint.StepDouble,
  is PropertyConstraint.MinLength,
  is PropertyConstraint.MaxLength,
  is PropertyConstraint.Pattern,
  is PropertyConstraint.MinSize,
  is PropertyConstraint.MaxSize,
  is PropertyConstraint.MinDate,
  is PropertyConstraint.MaxDate,
  is PropertyConstraint.MinTime,
  is PropertyConstraint.MaxTime,
  is PropertyConstraint.MinDatetime,
  is PropertyConstraint.MaxDatetime,
  -> true
}

/**
 * The single Property this [MigrationStep] targets in the draft - the same as its `propertyId` for every kind except
 * [MigrationStep.CopyValue], whose target is [MigrationStep.CopyValue.targetPropertyId].
 */
val MigrationStep.coveredPropertyId: PropertyId
  get() = when (this) {
    is MigrationStep.ConvertType -> propertyId
    is MigrationStep.CopyValue -> targetPropertyId
    is MigrationStep.ConvertUnit -> propertyId
    is MigrationStep.FillEmptyValue -> propertyId
    is MigrationStep.AdjustToConstraints -> propertyId
  }
