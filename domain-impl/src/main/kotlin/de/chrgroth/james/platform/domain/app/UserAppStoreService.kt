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
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppDetail
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
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
  private val userRepository: UserRepositoryPort,
) : UserAppStorePort {

  private fun latestPublishedVersion(appId: AppId) =
    appVersionRepository.findAllByAppId(appId)
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .maxByOrNull { it.createdAt }

  override fun listAllPublishedApps(): List<PublishedAppInfo> {
    val apps = appRepository.findAll().filter { it.status == AppStatus.ACTIVE }
    return apps.mapNotNull { app ->
      val latestVersion = latestPublishedVersion(app.id)
      latestVersion?.let {
        PublishedAppInfo(
          appId = app.id.value,
          appName = app.name.value,
          developerName = userRepository.findById(UserId(app.developerId))?.username?.value ?: app.developerId,
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
    val allVersions = appVersionRepository.findAllByAppId(app.id)
      .filter { it.status == AppVersionStatus.PUBLISHED }
      .sortedByDescending { it.createdAt }
    val latestVersion = allVersions.firstOrNull() ?: run {
      logger.warn { "Get published app failed: no published version for app: $appId" }
      return UserAppStoreError.NO_PUBLISHED_VERSION.left()
    }
    return PublishedAppDetail(
      appId = app.id.value,
      appName = app.name.value,
      appDescription = app.description,
      developerName = userRepository.findById(UserId(app.developerId))?.username?.value ?: app.developerId,
      latestVersion = latestVersion,
      allVersions = allVersions,
    ).right()
  }

  override fun getInstalledApps(userId: String): List<InstalledAppInfo> {
    val installed = installedAppRepository.findAllByUserId(userId)
    return installed.mapNotNull { installedApp ->
      val app = appRepository.findById(installedApp.appId) ?: return@mapNotNull null
      val installedVersion = appVersionRepository.findByAppIdAndVersionNumber(installedApp.appId, installedApp.installedVersionNumber) ?: return@mapNotNull null
      val latestVersion = latestPublishedVersion(installedApp.appId) ?: installedVersion
      InstalledAppInfo(
        installedApp = installedApp,
        installedAppId = installedApp.id.value,
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
    val latestVersion = latestPublishedVersion(AppId(appId)) ?: run {
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
      installedVersionNumber = latestVersion.versionNumber!!,
      installedAt = Instant.now(),
    )
    installedAppRepository.save(installedApp)
    logger.info { "App installed: appId=$appId for user=$userId (version ${latestVersion.versionNumber!!.value})" }
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
    val latestVersion = latestPublishedVersion(existing.appId) ?: run {
      logger.warn { "Upgrade app failed: no published version for app: ${existing.appId.value}" }
      return UserAppStoreError.NO_PUBLISHED_VERSION.left()
    }
    if (existing.installedVersionNumber == latestVersion.versionNumber) {
      logger.warn { "Upgrade app failed: already up to date for installedAppId: $installedAppId" }
      return UserAppStoreError.ALREADY_UP_TO_DATE.left()
    }
    val upgraded = existing.copy(installedVersionNumber = latestVersion.versionNumber!!)
    installedAppRepository.save(upgraded)
    logger.info { "App upgraded: installedAppId=$installedAppId to version ${latestVersion.versionNumber!!.value}" }
    return upgraded.right()
  }

  companion object : KLogging()
}
