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

  override fun createVersion(appId: String, versionNumber: String): Either<DomainError, AppVersion> {
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
    val existingVersions = appVersionRepository.findAllByAppId(AppId(appId))
    if (existingVersions.any { it.status == AppVersionStatus.DRAFT }) {
      logger.warn { "Create version failed: draft version already exists for app $appId" }
      return AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.left()
    }
    if (existingVersions.any { it.versionNumber == VersionNumber(versionNumber) }) {
      logger.warn { "Create version failed: version number already exists: $versionNumber in app $appId" }
      return AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.left()
    }
    val latestPublished = existingVersions
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
    val newVersion = if (latestPublished != null) {
      latestPublished.copy(
        id = AppVersionId(UUID.randomUUID().toString()),
        versionNumber = VersionNumber(versionNumber),
        releaseNotes = null,
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    } else {
      AppVersion(
        id = AppVersionId(UUID.randomUUID().toString()),
        appId = AppId(appId),
        versionNumber = VersionNumber(versionNumber),
        releaseNotes = null,
        entityDefinitions = emptyList(),
        reports = emptyList(),
        status = AppVersionStatus.DRAFT,
        createdAt = Instant.now(),
      )
    }
    appVersionRepository.save(newVersion)
    logger.info { "App version created: $versionNumber for app $appId (${newVersion.id.value})" }
    return newVersion.right()
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
    val publishedVersion = version.copy(status = AppVersionStatus.PUBLISHED)
    appVersionRepository.save(publishedVersion)
    logger.info { "App version published: ${version.versionNumber.value} (${version.id.value})" }
    return publishedVersion.right()
  }

  companion object : KLogging() {
    private val SEMANTIC_VERSION_REGEX = Regex("""^\d+\.\d+\.\d+$""")
  }
}
