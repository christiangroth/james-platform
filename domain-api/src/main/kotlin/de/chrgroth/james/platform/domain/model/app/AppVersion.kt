package de.chrgroth.james.platform.domain.model.app

import java.time.Instant

@JvmInline
value class AppVersionId(val value: String)

@JvmInline
value class VersionNumber(val value: String)

data class AppVersion(
  val id: AppVersionId,
  val appId: AppId,
  val versionNumber: VersionNumber,
  val releaseNotes: String?,
  val entityDefinitions: List<EntityDefinition>,
  val reports: List<Report>,
  val status: AppVersionStatus,
  val createdAt: Instant,
)

enum class AppVersionStatus {
  DRAFT,
  PUBLISHED,
}
