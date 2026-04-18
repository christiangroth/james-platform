package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppDetail
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class UserAppStoreService(
  private val appRepository: AppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val installedAppRepository: InstalledAppRepositoryPort,
) : UserAppStorePort {

  override fun listAllPublishedApps(): List<PublishedAppInfo> {
    val apps = appRepository.findAll().filter { it.status == AppStatus.ACTIVE }
    return apps.mapNotNull { app ->
      val latestVersion = appVersionRepository.findAllByAppId(app.id)
        .filter { it.status == AppVersionStatus.PUBLISHED }
        .maxByOrNull { it.createdAt }
      latestVersion?.let {
        PublishedAppInfo(
          appId = app.id.value,
          appName = app.name.value,
          developerName = app.developerId,
          latestVersion = it,
        )
      }
    }.sortedBy { it.appName }
  }

  override fun getPublishedApp(appId: String): Either<DomainError, PublishedAppDetail> {
    val app = appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Get published app failed: app not found: $appId" }
      return UserAppStoreError.APP_NOT_FOUND.left()
    }
    val latestVersion = appVersionRepository.findAllByAppId(app.id)
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
      ?: run {
        logger.warn { "Get published app failed: no published version for app: $appId" }
        return UserAppStoreError.NO_PUBLISHED_VERSION.left()
      }
    return PublishedAppDetail(
      appId = app.id.value,
      appName = app.name.value,
      appDescription = app.description,
      developerName = app.developerId,
      latestVersion = latestVersion,
    ).right()
  }

  override fun getInstalledApps(userId: String): List<InstalledAppInfo> {
    val installed = installedAppRepository.findAllByUserId(userId)
    return installed.mapNotNull { installedApp ->
      val app = appRepository.findById(installedApp.appId) ?: return@mapNotNull null
      val installedVersion = appVersionRepository.findById(installedApp.installedVersionId) ?: return@mapNotNull null
      val latestVersion = appVersionRepository.findAllByAppId(installedApp.appId)
        .filter { it.status == AppVersionStatus.PUBLISHED }
        .maxByOrNull { it.createdAt }
        ?: installedVersion
      InstalledAppInfo(
        installedApp = installedApp,
        appName = app.name.value,
        installedVersion = installedVersion,
        latestVersion = latestVersion,
      )
    }.sortedBy { it.appName }
  }

  override fun installApp(userId: String, appId: String): Either<DomainError, InstalledApp> {
    appRepository.findById(AppId(appId)) ?: run {
      logger.warn { "Install app failed: app not found: $appId" }
      return UserAppStoreError.APP_NOT_FOUND.left()
    }
    val latestVersion = appVersionRepository.findAllByAppId(AppId(appId))
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
      ?: run {
        logger.warn { "Install app failed: no published version for app: $appId" }
        return UserAppStoreError.NO_PUBLISHED_VERSION.left()
      }
    if (installedAppRepository.findByUserIdAndAppId(userId, AppId(appId)) != null) {
      logger.warn { "Install app failed: app already installed for user: $userId, app: $appId" }
      return UserAppStoreError.ALREADY_INSTALLED.left()
    }
    val installedApp = InstalledApp(
      id = InstalledAppId(UUID.randomUUID().toString()),
      userId = userId,
      appId = AppId(appId),
      installedVersionId = latestVersion.id,
      installedAt = Instant.now(),
    )
    installedAppRepository.save(installedApp)
    logger.info { "App installed: appId=$appId for user=$userId (version ${latestVersion.id.value})" }
    return installedApp.right()
  }

  override fun upgradeApp(userId: String, installedAppId: String): Either<DomainError, InstalledApp> {
    val existing = installedAppRepository.findById(InstalledAppId(installedAppId)) ?: run {
      logger.warn { "Upgrade app failed: installed app not found: $installedAppId" }
      return UserAppStoreError.INSTALLED_APP_NOT_FOUND.left()
    }
    if (existing.userId != userId) {
      logger.warn { "Upgrade app failed: installed app not found for user: $userId" }
      return UserAppStoreError.INSTALLED_APP_NOT_FOUND.left()
    }
    val latestVersion = appVersionRepository.findAllByAppId(existing.appId)
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }
      ?: run {
        logger.warn { "Upgrade app failed: no published version for app: ${existing.appId.value}" }
        return UserAppStoreError.NO_PUBLISHED_VERSION.left()
      }
    if (existing.installedVersionId == latestVersion.id) {
      logger.warn { "Upgrade app failed: already up to date for installedAppId: $installedAppId" }
      return UserAppStoreError.ALREADY_UP_TO_DATE.left()
    }
    val upgraded = existing.copy(installedVersionId = latestVersion.id)
    installedAppRepository.save(upgraded)
    logger.info { "App upgraded: installedAppId=$installedAppId to version ${latestVersion.id.value}" }
    return upgraded.right()
  }

  companion object : KLogging()
}
