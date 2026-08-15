package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.App

/** Result of deactivating an app: the updated app plus the number of active installations at the time of deactivation, shown to the developer for information only. */
data class AppDeactivationResult(
  val app: App,
  val activeInstallationCount: Int,
)

interface AppManagementPort {
  fun listApps(developerId: String): List<App>
  fun createApp(name: String, description: String?, developerId: String): Either<DomainError, App>
  fun getApp(appId: String, developerId: String): Either<DomainError, App>
  fun updateApp(appId: String, name: String, description: String?, developerId: String): Either<DomainError, App>
  fun deactivateApp(appId: String, developerId: String): Either<DomainError, AppDeactivationResult>
  fun activateApp(appId: String, developerId: String): Either<DomainError, App>
  fun deleteApp(appId: String, developerId: String): Either<DomainError, Unit>
}
