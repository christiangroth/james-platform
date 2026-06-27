package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult
import de.chrgroth.james.platform.domain.model.app.VersionDiff

interface AppVersionManagementPort {
  fun listVersions(appId: String): Either<DomainError, List<AppVersion>>
  fun createVersion(appId: String): Either<DomainError, AppVersion>
  fun getVersion(appId: String, versionId: String): Either<DomainError, AppVersion>
  fun publishVersion(appId: String, bumpType: String?, releaseNotes: String): Either<DomainError, AppVersion>
  fun deleteDraftVersion(appId: String, versionId: String): Either<DomainError, Unit>
  fun computeVersionBump(appId: String, draftVersionId: String): Either<DomainError, VersionBumpResult>
  fun addEntity(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun deleteEntity(appId: String, versionId: String, entityId: String): Either<DomainError, AppVersion>
  fun reorderEntities(appId: String, versionId: String, entityIds: List<String>): Either<DomainError, AppVersion>
  fun updateEntityDisplayText(appId: String, versionId: String, entityId: String, displayText: String?): Either<DomainError, AppVersion>
  fun updateEntitySortCriteria(appId: String, versionId: String, entityId: String, sortBy: List<SortCriteria>): Either<DomainError, AppVersion>
  fun addProperty(
    appId: String,
    versionId: String,
    entityId: String,
    name: String,
    type: String,
    nullable: Boolean,
    targetEntityId: String? = null,
    listItemType: String? = null,
  ): Either<DomainError, AppVersion>
  fun updateProperty(appId: String, versionId: String, entityId: String, propertyId: String, name: String, type: String, nullable: Boolean): Either<DomainError, AppVersion>
  fun setPropertyConstraints(appId: String, versionId: String, entityId: String, propertyId: String, constraints: Set<PropertyConstraint>): Either<DomainError, AppVersion>
  fun setPropertyDefault(appId: String, versionId: String, entityId: String, propertyId: String, default: String?): Either<DomainError, AppVersion>
  fun setPropertySmartDefault(appId: String, versionId: String, entityId: String, propertyId: String, smartDefault: String?): Either<DomainError, AppVersion>
  fun setPropertyValueProposals(appId: String, versionId: String, entityId: String, propertyId: String, valueProposals: List<String>): Either<DomainError, AppVersion>
  fun setPropertyTargetEntity(appId: String, versionId: String, entityId: String, propertyId: String, targetEntityId: String?): Either<DomainError, AppVersion>
  fun setPropertyListItemType(appId: String, versionId: String, entityId: String, propertyId: String, listItemType: String?): Either<DomainError, AppVersion>
  fun setPropertyItemConstraints(appId: String, versionId: String, entityId: String, propertyId: String, itemConstraints: Set<PropertyConstraint>): Either<DomainError, AppVersion>
  fun reorderProperties(appId: String, versionId: String, entityId: String, propertyIds: List<String>): Either<DomainError, AppVersion>
  fun deleteProperty(appId: String, versionId: String, entityId: String, propertyId: String): Either<DomainError, AppVersion>
  fun addComputedProperty(appId: String, versionId: String, entityId: String, name: String, type: String): Either<DomainError, AppVersion>
  fun updateComputedProperty(appId: String, versionId: String, entityId: String, computedPropertyId: String, name: String, type: String): Either<DomainError, AppVersion>
  fun setComputedPropertyScript(appId: String, versionId: String, entityId: String, computedPropertyId: String, script: String?): Either<DomainError, AppVersion>
  fun reorderComputedProperties(appId: String, versionId: String, entityId: String, computedPropertyIds: List<String>): Either<DomainError, AppVersion>
  fun deleteComputedProperty(appId: String, versionId: String, entityId: String, computedPropertyId: String): Either<DomainError, AppVersion>
  fun addReport(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun updateReport(appId: String, versionId: String, reportId: String, html: String, script: String): Either<DomainError, AppVersion>
  fun deleteReport(appId: String, versionId: String, reportId: String): Either<DomainError, AppVersion>
  fun getVersionDiff(appId: String, versionId: String): Either<DomainError, VersionDiff>
}
