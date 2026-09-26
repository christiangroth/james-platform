package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult
import de.chrgroth.james.platform.domain.model.app.VersionDiff
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

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
  fun addMigrationStep(appId: String, versionId: String, entityId: String, input: MigrationStepInput): Either<DomainError, AppVersion>
  fun updateMigrationStep(appId: String, versionId: String, entityId: String, stepId: String, input: MigrationStepInput): Either<DomainError, AppVersion>
  fun deleteMigrationStep(appId: String, versionId: String, entityId: String, stepId: String): Either<DomainError, AppVersion>
  fun reorderMigrationSteps(appId: String, versionId: String, entityId: String, stepIds: List<String>): Either<DomainError, AppVersion>
  fun updateEntityMigrationScript(appId: String, versionId: String, entityId: String, migrationScript: String?): Either<DomainError, AppVersion>
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
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun updateProperty(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    name: String,
    type: String,
    nullable: Boolean,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyConstraints(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    constraints: Set<PropertyConstraint>,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyDefault(appId: String, versionId: String, entityId: String, propertyId: String, default: String?, path: List<String> = emptyList()): Either<DomainError, AppVersion>
  fun setPropertySmartDefault(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    smartDefault: String?,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyValueProposals(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    valueProposals: List<String>,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyTargetEntity(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    targetEntityId: String?,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyListItemType(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    listItemType: String?,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyItemConstraints(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    itemConstraints: Set<PropertyConstraint>,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun setPropertyUnit(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String,
    family: String?,
    storageGranularity: String?,
    defaultGranularity: String?,
    path: List<String> = emptyList(),
  ): Either<DomainError, AppVersion>
  fun reorderProperties(appId: String, versionId: String, entityId: String, propertyIds: List<String>, path: List<String> = emptyList()): Either<DomainError, AppVersion>
  fun deleteProperty(appId: String, versionId: String, entityId: String, propertyId: String, path: List<String> = emptyList()): Either<DomainError, AppVersion>
  fun addComputedProperty(appId: String, versionId: String, entityId: String, name: String, type: String): Either<DomainError, AppVersion>
  fun updateComputedProperty(appId: String, versionId: String, entityId: String, computedPropertyId: String, name: String, type: String): Either<DomainError, AppVersion>
  fun setComputedPropertyScript(appId: String, versionId: String, entityId: String, computedPropertyId: String, script: String?): Either<DomainError, AppVersion>
  fun reorderComputedProperties(appId: String, versionId: String, entityId: String, computedPropertyIds: List<String>): Either<DomainError, AppVersion>
  fun deleteComputedProperty(appId: String, versionId: String, entityId: String, computedPropertyId: String): Either<DomainError, AppVersion>
  fun addAggregation(appId: String, versionId: String, entityId: String, input: AggregationInput): Either<DomainError, AppVersion>
  fun updateAggregation(appId: String, versionId: String, entityId: String, aggregationId: String, input: AggregationInput): Either<DomainError, AppVersion>
  fun deleteAggregation(appId: String, versionId: String, entityId: String, aggregationId: String): Either<DomainError, AppVersion>
  fun addReport(appId: String, versionId: String, name: String): Either<DomainError, AppVersion>
  fun updateReport(appId: String, versionId: String, reportId: String, html: String, script: String): Either<DomainError, AppVersion>
  fun deleteReport(appId: String, versionId: String, reportId: String): Either<DomainError, AppVersion>
  fun getVersionDiff(appId: String, versionId: String): Either<DomainError, VersionDiff>

  /** Called by the outbox dispatcher, not directly by inbound adapters. */
  fun handle(event: DomainOutboxEvent.AutoUpgradeInstallation): Either<DomainError, Unit>
}

/**
 * Raw editor input for an `AggregationDefinition` (see docs/adr/0020-aggregation-definitions.md). [function] and [timeBucket] are enum
 * names, the property references are property IDs of the owning Entity; blank optional values mean "not set". Parsing and validation
 * happen in the domain, using the same rules the Version publish applies.
 */
data class AggregationInput(
  val name: String,
  val function: String,
  val sourceProperty: String,
  val refPath: String? = null,
  val timeBucket: String? = null,
  val timeProperty: String? = null,
  val groupBy: String? = null,
  val periodStartDay: String? = null,
  val periodStartMonth: String? = null,
)

/**
 * Raw editor input for a `MigrationStep` (see MigrationStep, docs/adr/0023-migration-steps.md). [type] is `CONVERT_TYPE` or `COPY_VALUE`;
 * [propertyId] is used for `CONVERT_TYPE`, [sourcePropertyId]/[targetPropertyId] for `COPY_VALUE`. Property references are property IDs
 * of the owning Entity (top-level only).
 */
data class MigrationStepInput(
  val type: String,
  val propertyId: String? = null,
  val sourcePropertyId: String? = null,
  val targetPropertyId: String? = null,
)
