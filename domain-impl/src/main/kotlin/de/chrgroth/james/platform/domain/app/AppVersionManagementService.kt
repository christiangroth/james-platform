package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppVersionManagementService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
) : AppVersionManagementPort {

  override fun listVersions(appId: String): Either<DomainError, List<AppVersion>> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "List versions failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    return appVersionRepository.findAllByAppId(AppId(appId)).right()
  }

  override fun createVersion(appId: String, versionNumber: String, releaseNotes: String?): Either<DomainError, AppVersion> {
    if (versionNumber.isBlank()) {
      logger.warn { "Create version failed: blank version number" }
      return AppVersionError.BLANK_INPUT.left()
    }
    if (!SEMANTIC_VERSION_REGEX.matches(versionNumber)) {
      logger.warn { "Create version failed: invalid version number format: $versionNumber" }
      return AppVersionError.INVALID_VERSION_NUMBER_FORMAT.left()
    }
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Create version failed: app not found: $appId" }
      return AppVersionError.APP_NOT_FOUND.left()
    }
    if (appVersionRepository.findByAppIdAndVersionNumber(AppId(appId), VersionNumber(versionNumber)) != null) {
      logger.warn { "Create version failed: version number already exists: $versionNumber in app $appId" }
      return AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.left()
    }
    val version = AppVersion(
      id = AppVersionId(UUID.randomUUID().toString()),
      appId = AppId(appId),
      versionNumber = VersionNumber(versionNumber),
      releaseNotes = releaseNotes?.takeIf { it.isNotBlank() },
      status = AppVersionStatus.DRAFT,
      publishedAt = null,
      createdAt = Instant.now(),
    )
    appVersionRepository.save(version)
    logger.info { "App version created: $versionNumber for app $appId (${version.id.value})" }
    return version.right()
  }

  override fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Get version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Get version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    return version.right()
  }

  override fun publishVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Publish version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Publish version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.status != AppVersionStatus.DRAFT) {
      logger.warn { "Publish version failed: version $versionId is not in DRAFT status" }
      return AppVersionError.VERSION_NOT_IN_DRAFT.left()
    }
    val publishedVersion = version.copy(
      status = AppVersionStatus.PUBLISHED,
      publishedAt = Instant.now(),
    )
    appVersionRepository.save(publishedVersion)
    logger.info { "App version published: ${version.versionNumber.value} (${version.id.value})" }
    return publishedVersion.right()
  }

  override fun deprecateVersion(appId: String, versionId: String): Either<DomainError, AppVersion> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Deprecate version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Deprecate version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.status != AppVersionStatus.PUBLISHED) {
      logger.warn { "Deprecate version failed: version $versionId is not in PUBLISHED status" }
      return AppVersionError.VERSION_NOT_PUBLISHED.left()
    }
    val deprecatedVersion = version.copy(status = AppVersionStatus.DEPRECATED)
    appVersionRepository.save(deprecatedVersion)
    logger.info { "App version deprecated: ${version.versionNumber.value} (${version.id.value})" }
    return deprecatedVersion.right()
  }

  override fun deleteVersion(appId: String, versionId: String): Either<DomainError, Unit> {
    val version = appVersionRepository.findById(AppVersionId(versionId)) ?: run {
      logger.warn { "Delete version failed: not found: $versionId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.appId != AppId(appId)) {
      logger.warn { "Delete version failed: version $versionId does not belong to app $appId" }
      return AppVersionError.VERSION_NOT_FOUND.left()
    }
    if (version.status != AppVersionStatus.DRAFT) {
      logger.warn { "Delete version failed: version $versionId is not in DRAFT status" }
      return AppVersionError.CANNOT_DELETE_NON_DRAFT_VERSION.left()
    }
    appVersionRepository.delete(AppVersionId(versionId))
    logger.info { "App version deleted: ${version.versionNumber.value} (${version.id.value})" }
    return Unit.right()
  }

  companion object : KLogging() {
    private val SEMANTIC_VERSION_REGEX = Regex("""^\d+\.\d+\.\d+$""")
  }
}
