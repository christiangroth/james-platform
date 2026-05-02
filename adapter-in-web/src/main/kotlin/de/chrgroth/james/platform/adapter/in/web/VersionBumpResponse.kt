package de.chrgroth.james.platform.adapter.`in`.web

data class VersionBumpResponse(
  val hasBreakingChanges: Boolean,
  val hasChanges: Boolean,
  val suggestedVersionOnBreaking: String,
  val suggestedVersionOnFeature: String,
  val suggestedVersionOnBugfix: String,
)
