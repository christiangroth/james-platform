package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData

interface AppDataPort {
  fun createAppData(
    userId: String,
    installedAppId: String,
    entityTypeId: String,
    data: Map<String, String>,
  ): Either<DomainError, AppData>

  fun listAppData(userId: String, installedAppId: String): Either<DomainError, List<AppData>>

  fun getAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, AppData>

  fun updateAppData(
    userId: String,
    installedAppId: String,
    dataId: String,
    data: Map<String, String>,
  ): Either<DomainError, AppData>

  fun deleteAppData(userId: String, installedAppId: String, dataId: String): Either<DomainError, Unit>
}
