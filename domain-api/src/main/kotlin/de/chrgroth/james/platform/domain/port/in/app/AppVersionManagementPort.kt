package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult

interface AppVersionManagementPort {
  fun listVersions(appId: String): Either<DomainError, List<AppVersion>>
  fun createVersion(appId: String): Either<DomainError, AppVersion>
  fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun publishVersion(appId: String, versionId: String, versionNumber: String): Either<DomainError, AppVersion>
  fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult>
  fun addEntity(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun deleteEntity(appId: String, versionId: String, entityId: String): Either<DomainError, AppVersion>
  fun addProperty(appId: String, versionId: String, entityId: String, name: String, type: String, nullable: Boolean): Either<DomainError, AppVersion>
  fun deleteProperty(appId: String, versionId: String, entityId: String, propertyId: String): Either<DomainError, AppVersion>
  fun addReport(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun deleteReport(appId: String, versionId: String, reportId: String): Either<DomainError, AppVersion>
  fun addReportPage(appId: String, versionId: String, reportId: String, html: String, script: String): Either<DomainError, AppVersion>
  fun updateReportPage(appId: String, versionId: String, reportId: String, pageIndex: Int, html: String, script: String): Either<DomainError, AppVersion>
  fun deleteReportPage(appId: String, versionId: String, reportId: String, pageIndex: Int): Either<DomainError, AppVersion>
}
