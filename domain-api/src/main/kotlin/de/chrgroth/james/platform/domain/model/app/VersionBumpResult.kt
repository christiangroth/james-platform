package de.chrgroth.james.platform.domain.model.app

data class VersionBumpResult(
  val hasBreakingChanges: Boolean,
  val suggestedVersionOnBreaking: VersionNumber,
  val suggestedVersionOnFeature: VersionNumber,
  val suggestedVersionOnBugfix: VersionNumber,
)
