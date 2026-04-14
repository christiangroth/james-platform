package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion

interface AppVersionManagementPort {
  fun listVersions(appId: String): Either<DomainError, List<AppVersion>>
  fun createVersion(appId: String, versionNumber: String, releaseNotes: String?): Either<DomainError, AppVersion>
  fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun publishVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun deprecateVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun deleteVersion(appId: String, versionId: String): Either<DomainError, Unit>
}
