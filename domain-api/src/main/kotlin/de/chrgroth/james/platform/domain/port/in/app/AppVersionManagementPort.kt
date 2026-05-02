package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult

interface AppVersionManagementPort {
  fun listVersions(appId: String): Either<DomainError, List<AppVersion>>
  fun createVersion(appId: String): Either<DomainError, AppVersion>
  fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun publishVersion(appId: String, bumpType: String?, releaseNotes: String): Either<DomainError, AppVersion>
  fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult>
  fun addEntity(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun deleteEntity(appId: String, versionId: String, entityId: String): Either<DomainError, AppVersion>
  fun addProperty(appId: String, versionId: String, entityId: String, name: String, type: String, nullable: Boolean): Either<DomainError, AppVersion>
  fun updateProperty(appId: String, versionId: String, entityId: String, propertyId: String, name: String, type: String, nullable: Boolean): Either<DomainError, AppVersion>
  fun setPropertyConstraints(appId: String, versionId: String, entityId: String, propertyId: String, constraints: Set<PropertyConstraint>): Either<DomainError, AppVersion>
  fun deleteProperty(appId: String, versionId: String, entityId: String, propertyId: String): Either<DomainError, AppVersion>
  fun addReport(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun updateReport(appId: String, versionId: String, reportId: String, html: String, script: String): Either<DomainError, AppVersion>
  fun deleteReport(appId: String, versionId: String, reportId: String): Either<DomainError, AppVersion>
}
