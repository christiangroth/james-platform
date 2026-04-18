package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.InstalledApp

data class PublishedAppInfo(
  val appId: String,
  val appName: String,
  val developerName: String,
  val latestVersion: AppVersion,
)

data class PublishedAppDetail(
  val appId: String,
  val appName: String,
  val appDescription: String?,
  val developerName: String,
  val latestVersion: AppVersion,
)

data class InstalledAppInfo(
  val installedApp: InstalledApp,
  val appName: String,
  val installedVersion: AppVersion,
  val latestVersion: AppVersion,
)

interface UserAppStorePort {
  fun listAllPublishedApps(): List<PublishedAppInfo>
  fun getPublishedApp(appId: String): Either<DomainError, PublishedAppDetail>
  fun getInstalledApps(userId: String): List<InstalledAppInfo>
  fun installApp(userId: String, appId: String): Either<DomainError, InstalledApp>
  fun upgradeApp(userId: String, installedAppId: String): Either<DomainError, InstalledApp>
}
