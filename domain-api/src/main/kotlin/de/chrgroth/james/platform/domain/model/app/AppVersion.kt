package de.chrgroth.james.platform.domain.model.app

import java.time.Instant

@JvmInline
value class AppVersionId(val value: String)

@JvmInline
value class VersionNumber(val value: String) {
  /** The semver major part, or null if [value] is not a version number. */
  val major: Int? get() = value.substringBefore('.').toIntOrNull()
}

/**
 * How an installation relates to its app's latest published version. Publishing a non-breaking version (same major) auto-upgrades
 * every installation of that major in the background, so a version gap within one major is an update in progress; a breaking version
 * (new major) is never auto-upgraded and needs the user's confirmation.
 */
enum class InstallationUpdateState {
  UP_TO_DATE,
  AUTO_UPDATE_PENDING,
  MANUAL_UPDATE_REQUIRED,
  ;

  companion object {
    fun of(installed: VersionNumber?, latest: VersionNumber?): InstallationUpdateState = when {
      installed == null || latest == null || installed == latest -> UP_TO_DATE
      installed.major != null && installed.major == latest.major -> AUTO_UPDATE_PENDING
      else -> MANUAL_UPDATE_REQUIRED
    }
  }
}

data class AppVersion(
  val id: AppVersionId,
  val appId: AppId,
  val versionNumber: VersionNumber?,
  val releaseNotes: String?,
  val entityDefinitions: List<EntityDefinition>,
  val reports: List<Report>,
  val status: AppVersionStatus,
  val createdAt: Instant,
) {
  /** The configuration Entity (singleton, ADR 0027), if this version defines one. */
  val configurationEntity: EntityDefinition? get() = entityDefinitions.find { it.configuration }

  /** The Entities holding regular data, i.e. all but the [configurationEntity]. */
  val dataEntities: List<EntityDefinition> get() = entityDefinitions.filter { !it.configuration }
}

enum class AppVersionStatus {
  DRAFT,
  PUBLISHED,
}
