package de.chrgroth.james.platform.domain.model.app

data class VersionDiff(
  val version: AppVersion,
  val previousVersion: AppVersion,
  val entityDiffs: List<SectionDiff>,
  val reportDiffs: List<SectionDiff>,
)

data class SectionDiff(
  val name: String,
  val status: DiffStatus,
  val lines: List<DiffLine>,
)

data class DiffLine(
  val text: String,
  val status: DiffLineStatus,
)

enum class DiffStatus { ADDED, REMOVED, MODIFIED, UNCHANGED }
enum class DiffLineStatus { ADDED, REMOVED, UNCHANGED }
