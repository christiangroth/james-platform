package de.chrgroth.james.platform.domain.model.app

/**
 * Suggested next version numbers for a draft. [breakingEntityIds] / [breakingPropertyIds] locate the breaking changes within the draft
 * (only populated when [hasBreakingChanges] is true): Entities whose change is breaking (e.g. a removed or incompatibly changed
 * Property) and the incompatibly changed Properties themselves. Removed Entities/Properties are not part of the draft and hence not listed.
 */
data class VersionBumpResult(
  val hasBreakingChanges: Boolean,
  val hasChanges: Boolean,
  val suggestedVersionOnBreaking: VersionNumber,
  val suggestedVersionOnFeature: VersionNumber,
  val suggestedVersionOnBugfix: VersionNumber,
  val breakingEntityIds: Set<EntityDefinitionId> = emptySet(),
  val breakingPropertyIds: Set<PropertyId> = emptySet(),
)
