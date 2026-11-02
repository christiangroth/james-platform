package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.InstallationUpdateState
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

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
  val allVersions: List<AppVersion>,
)

data class InstalledAppInfo(
  val installedApp: InstalledApp,
  val installedAppId: String,
  val appName: String,
  val appActive: Boolean,
  val installedVersion: AppVersion,
  val latestVersion: AppVersion,
) {
  /** Whether this installation is up to date, being auto-upgraded, or needs a confirmed (breaking) upgrade. */
  val updateState: InstallationUpdateState get() = InstallationUpdateState.of(installedVersion.versionNumber, latestVersion.versionNumber)

  /** A non-breaking update to [latestVersion] is being applied automatically in the background. */
  val autoUpdatePending: Boolean get() = updateState == InstallationUpdateState.AUTO_UPDATE_PENDING

  /** [latestVersion] is a breaking (new major) version, which is never applied automatically. */
  val manualUpdateRequired: Boolean get() = updateState == InstallationUpdateState.MANUAL_UPDATE_REQUIRED
}

interface UserAppStorePort {
  fun listAllPublishedApps(): List<PublishedAppInfo>
  fun getPublishedApp(appId: String): Either<DomainError, PublishedAppDetail>
  fun getInstalledApps(userId: String): List<InstalledAppInfo>
  fun getInstalledApp(userId: String, installedAppId: String): Either<DomainError, InstalledAppInfo>
  fun installApp(userId: String, appId: String): Either<DomainError, InstalledApp>
  fun upgradeApp(userId: String, installedAppId: String): Either<DomainError, InstalledApp>
  fun uninstallApp(userId: String, installedAppId: String): Either<DomainError, Unit>

  /** Called by the outbox dispatcher, not directly by inbound adapters. */
  fun handle(event: DomainOutboxEvent.UninstallApp): Either<DomainError, Unit>
}
