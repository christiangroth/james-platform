package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.App

interface AppManagementPort {
  fun listApps(): List<App>
  fun createApp(name: String, description: String?): Either<DomainError, App>
  fun getApp(appId: String): Either<DomainError, App>
  fun updateApp(appId: String, name: String, description: String?): Either<DomainError, App>
  fun deactivateApp(appId: String): Either<DomainError, App>
}
