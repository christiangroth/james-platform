package de.chrgroth.james.platform.domain.model.app

data class VersionBumpResult(
  val hasBreakingChanges: Boolean,
  val hasChanges: Boolean,
  val suggestedVersionOnBreaking: VersionNumber,
  val suggestedVersionOnFeature: VersionNumber,
  val suggestedVersionOnBugfix: VersionNumber,
)
