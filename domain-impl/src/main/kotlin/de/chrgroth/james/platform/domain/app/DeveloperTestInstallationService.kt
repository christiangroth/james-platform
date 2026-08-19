package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DeveloperTestInstallationError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.`in`.app.DeveloperTestInstallationPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class DeveloperTestInstallationService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val outbox: OutboxPort,
) : DeveloperTestInstallationPort {

  override fun listTestInstallations(appId: String, developerId: String): Either<DomainError, List<InstalledApp>> {
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "List test installations failed: app not found: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    if (app.developerId != developerId) {
      logger.warn { "List test installations failed: app not owned by developer: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    return installedAppRepository.findAllByAppId(app.id).filter { it.isTest }.right()
  }

  override fun createTestInstallation(appId: String, versionId: String, developerId: String): Either<DomainError, InstalledApp> {
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Create test installation failed: app not found: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    if (app.developerId != developerId) {
      logger.warn { "Create test installation failed: app not owned by developer: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    val version = appVersionRepository.findById(AppVersionId(versionId))
    if (version == null || version.appId != app.id) {
      logger.warn { "Create test installation failed: version not found: $versionId for app: $appId" }
      return DeveloperTestInstallationError.VERSION_NOT_FOUND.left()
    }
    val installedApp = InstalledApp(
      id = InstalledAppId(UUID.randomUUID().toString()),
      userId = developerId,
      appId = app.id,
      // DRAFT versions have no versionNumber yet; this placeholder is never resolved against - installedVersionId is authoritative for test installations.
      installedVersionNumber = version.versionNumber ?: VersionNumber("draft-${version.id.value}"),
      installedAt = Instant.now(),
      isTest = true,
      installedVersionId = version.id,
    )
    installedAppRepository.save(installedApp)
    logger.info { "Test installation created: appId=$appId, versionId=$versionId for developer=$developerId" }
    return installedApp.right()
  }

  override fun deleteTestInstallation(appId: String, installedAppId: String, developerId: String): Either<DomainError, Unit> {
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Delete test installation failed: app not found: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    if (app.developerId != developerId) {
      logger.warn { "Delete test installation failed: app not owned by developer: $appId" }
      return DeveloperTestInstallationError.APP_NOT_FOUND.left()
    }
    val existing = installedAppRepository.findById(InstalledAppId(installedAppId))
    if (existing == null || existing.appId != app.id || !existing.isTest) {
      logger.warn { "Delete test installation failed: not found: $installedAppId for app: $appId" }
      return DeveloperTestInstallationError.INSTALLATION_NOT_FOUND.left()
    }
    outbox.enqueue(DomainOutboxEvent.UninstallApp(installedAppId = existing.id.value, userId = developerId))
    logger.info { "Test installation delete enqueued: installedAppId=$installedAppId for developer=$developerId" }
    return Unit.right()
  }

  companion object : KLogging()
}
