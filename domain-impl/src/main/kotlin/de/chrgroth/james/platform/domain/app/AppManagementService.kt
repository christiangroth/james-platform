package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppName
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class AppManagementService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
) : AppManagementPort {

  override fun listApps(): List<App> = appRepository.findAll()

  override fun createApp(name: String, description: String?): Either<DomainError, App> {
    if (name.isBlank()) {
      logger.warn { "Create app failed: blank name" }
      return AppError.BLANK_INPUT.left()
    }
    if (appRepository.findByName(AppName(name)) != null) {
      logger.warn { "Create app failed: name already exists: $name" }
      return AppError.APP_NAME_ALREADY_EXISTS.left()
    }
    val now = Instant.now()
    val app = App(
      id = AppId(UUID.randomUUID().toString()),
      name = AppName(name),
      description = description?.takeIf { it.isNotBlank() },
      status = AppStatus.ACTIVE,
      createdAt = now,
      updatedAt = now,
    )
    appRepository.save(app)
    logger.info { "App created: $name (${app.id.value})" }
    return app.right()
  }

  override fun getApp(appId: String): Either<DomainError, App> {
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Get app failed: not found: $appId" }
      return AppError.APP_NOT_FOUND.left()
    }
    return app.right()
  }

  override fun updateApp(appId: String, name: String, description: String?): Either<DomainError, App> {
    if (name.isBlank()) {
      logger.warn { "Update app failed: blank name" }
      return AppError.BLANK_INPUT.left()
    }
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Update app failed: not found: $appId" }
      return AppError.APP_NOT_FOUND.left()
    }
    val existingWithName = appRepository.findByName(AppName(name))
    if (existingWithName != null && existingWithName.id != app.id) {
      logger.warn { "Update app failed: name already exists: $name" }
      return AppError.APP_NAME_ALREADY_EXISTS.left()
    }
    val updatedApp = app.copy(
      name = AppName(name),
      description = description?.takeIf { it.isNotBlank() },
      updatedAt = Instant.now(),
    )
    appRepository.save(updatedApp)
    logger.info { "App updated: $name (${app.id.value})" }
    return updatedApp.right()
  }

  override fun deleteApp(appId: String): Either<DomainError, Unit> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Delete app failed: not found: $appId" }
      return AppError.APP_NOT_FOUND.left()
    }
    val publishedVersions = appVersionRepository.findAllByAppId(AppId(appId))
      .filter { it.status == AppVersionStatus.PUBLISHED }
    if (publishedVersions.isNotEmpty()) {
      logger.warn { "Delete app failed: has published versions: $appId" }
      return AppError.HAS_PUBLISHED_VERSIONS.left()
    }
    appVersionRepository.deleteAllByAppId(AppId(appId))
    appRepository.delete(AppId(appId))
    logger.info { "App deleted: $appId" }
    return Unit.right()
  }

  companion object : KLogging()
}
