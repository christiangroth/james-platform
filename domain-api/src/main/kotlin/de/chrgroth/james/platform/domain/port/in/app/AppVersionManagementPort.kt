package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult

interface AppVersionManagementPort {
  fun listVersions(appId: String): Either<DomainError, List<AppVersion>>
  fun createVersion(appId: String, versionNumber: String): Either<DomainError, AppVersion>
  fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun publishVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult>
}
