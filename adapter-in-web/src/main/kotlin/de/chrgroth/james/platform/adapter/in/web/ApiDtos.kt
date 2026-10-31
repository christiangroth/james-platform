package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Granularity
import de.chrgroth.james.platform.domain.model.app.MigrationStep
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.VersionBumpResult
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Plain JSON DTOs of the `/api/v1` personal access token API. Domain models are never serialized directly: they use Kotlin value classes and
// sealed hierarchies, and the API contract must not change whenever the domain model does. All of them are registered for reflection in
// JsonResponseReflectionConfig (native mode).

data class ApiTokenInfoDto(
  val username: String,
  val tokenId: String,
  val scopes: List<String>,
  val appIds: List<String>,
)

data class ApiAppDto(
  val id: String,
  val name: String,
  val description: String?,
  val status: String,
  val createdAt: Instant,
  val updatedAt: Instant,
)

data class ApiVersionSummaryDto(
  val id: String,
  val versionNumber: String?,
  val status: String,
  val releaseNotes: String?,
  val createdAt: Instant,
)

data class ApiDeveloperAppDto(
  val app: ApiAppDto,
  val versions: List<ApiVersionSummaryDto>,
)

data class ApiVersionDto(
  val id: String,
  val appId: String,
  val versionNumber: String?,
  val status: String,
  val releaseNotes: String?,
  val createdAt: Instant,
  val entities: List<ApiEntityDto>,
  val reports: List<ApiReportDto>,
)

data class ApiEntityDto(
  val id: String,
  val name: String,
  val displayText: String?,
  val properties: List<ApiPropertyDto>,
  val sortBy: List<ApiSortCriteriaDto>,
  val computedProperties: List<ApiComputedPropertyDto>,
  val aggregations: List<ApiAggregationDto>,
  val migrationSteps: List<ApiMigrationStepDto>,
  val migrationScript: String?,
)

data class ApiPropertyDto(
  val id: String,
  val name: String,
  val type: String,
  val nullable: Boolean,
  val constraints: List<ApiConstraintDto>,
  val default: String?,
  val smartDefault: String?,
  val valueProposals: List<String>,
  val targetEntityId: String?,
  val listItemType: String?,
  val itemConstraints: List<ApiConstraintDto>,
  val nestedProperties: List<ApiPropertyDto>,
  val unit: ApiUnitDto?,
)

/** A property constraint as `type` (e.g. `MIN_LONG`, `PATTERN`, `UNIQUE_KEY`) plus its value as string (ISO format for dates/times, null for `UNIQUE_KEY`). */
data class ApiConstraintDto(
  val type: String,
  val value: String?,
)

data class ApiUnitDto(
  val family: String,
  val storageGranularity: String,
  val defaultGranularity: String,
)

data class ApiSortCriteriaDto(
  val propertyId: String,
  val direction: String,
)

data class ApiComputedPropertyDto(
  val id: String,
  val name: String,
  val type: String,
  val script: String?,
)

data class ApiAggregationDto(
  val id: String,
  val name: String,
  val function: String,
  val sourceProperty: String,
  val refPath: String?,
  val timeBucket: String?,
  val timeProperty: String?,
  val groupBy: String?,
  val periodStart: String?,
)

data class ApiMigrationStepDto(
  val id: String,
  val type: String,
  val propertyId: String?,
  val sourcePropertyId: String?,
  val targetPropertyId: String?,
  val sourceGranularity: String?,
  val value: String?,
)

data class ApiReportDto(
  val id: String,
  val name: String,
  val html: String,
  val script: String,
)

data class ApiVersionBumpDto(
  val hasChanges: Boolean,
  val hasBreakingChanges: Boolean,
  val suggestedVersionOnBreaking: String,
  val suggestedVersionOnFeature: String,
  val suggestedVersionOnBugfix: String,
  val breakingEntityIds: List<String>,
  val breakingPropertyIds: List<String>,
)

data class ApiInstalledAppDto(
  val installedAppId: String,
  val appId: String,
  val appName: String,
  val appActive: Boolean,
  val installedVersionNumber: String?,
  val latestVersionNumber: String?,
  val installedAt: Instant,
)

data class ApiInstalledAppDetailDto(
  val installedApp: ApiInstalledAppDto,
  val version: ApiVersionDto,
)

/**
 * One data object of an installed App. [values] maps property IDs to their stored values: a string for scalar properties (units in the
 * property's storage granularity), a list of strings for `LIST` properties and a map (property ID to value) for `OBJECT` properties.
 */
data class ApiAppDataDto(
  val id: String,
  val entityTypeId: String,
  val values: Map<String, Any?>,
  val appVersion: String,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

object ApiDtoMapper {

  fun tokenInfo(username: String, tokenId: String, scopes: Set<ApiTokenScope>, appIds: Set<String>) =
    ApiTokenInfoDto(username, tokenId, scopes.map { it.name }.sorted(), appIds.sorted())

  fun app(app: App) = ApiAppDto(app.id.value, app.name.value, app.description, app.status.name, app.createdAt, app.updatedAt)

  fun versionSummary(version: AppVersion) =
    ApiVersionSummaryDto(version.id.value, version.versionNumber?.value, version.status.name, version.releaseNotes, version.createdAt)

  fun version(version: AppVersion) = ApiVersionDto(
    id = version.id.value,
    appId = version.appId.value,
    versionNumber = version.versionNumber?.value,
    status = version.status.name,
    releaseNotes = version.releaseNotes,
    createdAt = version.createdAt,
    entities = version.entityDefinitions.map { entity(it) },
    reports = version.reports.map { report(it) },
  )

  fun versionBump(result: VersionBumpResult) = ApiVersionBumpDto(
    hasChanges = result.hasChanges,
    hasBreakingChanges = result.hasBreakingChanges,
    suggestedVersionOnBreaking = result.suggestedVersionOnBreaking.value,
    suggestedVersionOnFeature = result.suggestedVersionOnFeature.value,
    suggestedVersionOnBugfix = result.suggestedVersionOnBugfix.value,
    breakingEntityIds = result.breakingEntityIds.map { it.value },
    breakingPropertyIds = result.breakingPropertyIds.map { it.value },
  )

  fun installedApp(info: InstalledAppInfo) = ApiInstalledAppDto(
    installedAppId = info.installedAppId,
    appId = info.installedApp.appId.value,
    appName = info.appName,
    appActive = info.appActive,
    installedVersionNumber = info.installedVersion.versionNumber?.value,
    latestVersionNumber = info.latestVersion.versionNumber?.value,
    installedAt = info.installedApp.installedAt,
  )

  fun appData(data: AppData, entity: EntityDefinition?) = ApiAppDataDto(
    id = data.id.value,
    entityTypeId = data.entityType.value,
    values = data.data.mapValues { (propertyId, raw) -> decodeValue(entity?.properties?.firstOrNull { it.id.value == propertyId }, raw) },
    appVersion = data.appVersion.value,
    objectVersion = data.objectVersion,
    createdAt = data.createdAt,
    lastChangedAt = data.lastChangedAt,
  )

  private fun decodeValue(property: Property?, raw: String?): Any? = when (property?.type) {
    PropertyType.LIST -> decodeListValue(raw)
    PropertyType.OBJECT -> decodeObjectValue(raw)
    else -> raw
  }

  private fun entity(entity: EntityDefinition) = ApiEntityDto(
    id = entity.id.value,
    name = entity.name,
    displayText = entity.displayText,
    properties = entity.properties.map { property(it) },
    sortBy = entity.sortBy.map { ApiSortCriteriaDto(it.propertyId, it.direction.name) },
    computedProperties = entity.computedProperties.map { computedProperty(it) },
    aggregations = entity.aggregations.map { aggregation(it) },
    migrationSteps = entity.migrationSteps.map { migrationStep(it) },
    migrationScript = entity.migrationScript,
  )

  private fun property(property: Property): ApiPropertyDto = ApiPropertyDto(
    id = property.id.value,
    name = property.name,
    type = property.type.name,
    nullable = property.nullable,
    constraints = property.constraints.map { constraint(it) },
    default = property.default,
    smartDefault = property.smartDefault,
    valueProposals = property.valueProposals,
    targetEntityId = property.targetEntityId?.value,
    listItemType = property.listItemType?.name,
    itemConstraints = property.itemConstraints.map { constraint(it) },
    nestedProperties = property.nestedProperties.map { property(it) },
    unit = property.unit?.let { ApiUnitDto(it.family.name, granularityName(it.storageGranularity), granularityName(it.defaultGranularity)) },
  )

  private fun computedProperty(computed: ComputedProperty) = ApiComputedPropertyDto(computed.id.value, computed.name, computed.type.name, computed.script)

  private fun aggregation(aggregation: AggregationDefinition) = ApiAggregationDto(
    id = aggregation.id.value,
    name = aggregation.name,
    function = aggregation.function.name,
    sourceProperty = aggregation.sourceProperty.value,
    refPath = aggregation.refPath?.value,
    timeBucket = aggregation.timeBucket?.name,
    timeProperty = aggregation.timeProperty?.value,
    groupBy = aggregation.groupBy?.value,
    periodStart = aggregation.periodStart?.toString(),
  )

  private fun migrationStep(step: MigrationStep): ApiMigrationStepDto = when (step) {
    is MigrationStep.ConvertType -> ApiMigrationStepDto(step.id.value, "CONVERT_TYPE", step.propertyId.value, null, null, null, null)
    is MigrationStep.CopyValue -> ApiMigrationStepDto(step.id.value, "COPY_VALUE", null, step.sourcePropertyId.value, step.targetPropertyId.value, null, null)
    is MigrationStep.ConvertUnit ->
      ApiMigrationStepDto(step.id.value, "CONVERT_UNIT", step.propertyId.value, null, null, granularityName(step.sourceGranularity), null)
    is MigrationStep.FillEmptyValue -> ApiMigrationStepDto(step.id.value, "FILL_EMPTY_VALUE", step.propertyId.value, null, null, null, step.value)
    is MigrationStep.AdjustToConstraints -> ApiMigrationStepDto(step.id.value, "ADJUST_TO_CONSTRAINTS", step.propertyId.value, null, null, null, null)
  }

  private fun report(report: Report) = ApiReportDto(report.id.value, report.name, report.html, report.script)

  private fun granularityName(granularity: Granularity): String = (granularity as Enum<*>).name

  @Suppress("CyclomaticComplexMethod")
  fun constraint(constraint: PropertyConstraint): ApiConstraintDto = when (constraint) {
    PropertyConstraint.UniqueKey -> ApiConstraintDto("UNIQUE_KEY", null)
    is PropertyConstraint.MinLong -> ApiConstraintDto("MIN_LONG", constraint.min.toString())
    is PropertyConstraint.MaxLong -> ApiConstraintDto("MAX_LONG", constraint.max.toString())
    is PropertyConstraint.StepLong -> ApiConstraintDto("STEP_LONG", constraint.step.toString())
    is PropertyConstraint.MinDouble -> ApiConstraintDto("MIN_DOUBLE", constraint.min.toString())
    is PropertyConstraint.MaxDouble -> ApiConstraintDto("MAX_DOUBLE", constraint.max.toString())
    is PropertyConstraint.StepDouble -> ApiConstraintDto("STEP_DOUBLE", constraint.step.toString())
    is PropertyConstraint.MinLength -> ApiConstraintDto("MIN_LENGTH", constraint.min.toString())
    is PropertyConstraint.MaxLength -> ApiConstraintDto("MAX_LENGTH", constraint.max.toString())
    is PropertyConstraint.Pattern -> ApiConstraintDto("PATTERN", constraint.regex)
    is PropertyConstraint.MinSize -> ApiConstraintDto("MIN_SIZE", constraint.min.toString())
    is PropertyConstraint.MaxSize -> ApiConstraintDto("MAX_SIZE", constraint.max.toString())
    is PropertyConstraint.MinDate -> ApiConstraintDto("MIN_DATE", constraint.min.toString())
    is PropertyConstraint.MaxDate -> ApiConstraintDto("MAX_DATE", constraint.max.toString())
    is PropertyConstraint.MinTime -> ApiConstraintDto("MIN_TIME", constraint.min.toString())
    is PropertyConstraint.MaxTime -> ApiConstraintDto("MAX_TIME", constraint.max.toString())
    is PropertyConstraint.MinDatetime -> ApiConstraintDto("MIN_DATETIME", constraint.min.toString())
    is PropertyConstraint.MaxDatetime -> ApiConstraintDto("MAX_DATETIME", constraint.max.toString())
  }

  /** Inverse of [constraint]; null if [dto] has an unknown type or a value that does not parse. */
  @Suppress("CyclomaticComplexMethod")
  fun parseConstraint(dto: ApiConstraintDto): PropertyConstraint? = runCatching {
    val value = dto.value?.trim().orEmpty()
    when (dto.type) {
      "UNIQUE_KEY" -> PropertyConstraint.UniqueKey
      "MIN_LONG" -> PropertyConstraint.MinLong(value.toLong())
      "MAX_LONG" -> PropertyConstraint.MaxLong(value.toLong())
      "STEP_LONG" -> PropertyConstraint.StepLong(value.toLong())
      "MIN_DOUBLE" -> PropertyConstraint.MinDouble(value.toDouble())
      "MAX_DOUBLE" -> PropertyConstraint.MaxDouble(value.toDouble())
      "STEP_DOUBLE" -> PropertyConstraint.StepDouble(value.toDouble())
      "MIN_LENGTH" -> PropertyConstraint.MinLength(value.toInt())
      "MAX_LENGTH" -> PropertyConstraint.MaxLength(value.toInt())
      "PATTERN" -> value.takeIf { it.isNotEmpty() }?.let { PropertyConstraint.Pattern(it) }
      "MIN_SIZE" -> PropertyConstraint.MinSize(value.toInt())
      "MAX_SIZE" -> PropertyConstraint.MaxSize(value.toInt())
      "MIN_DATE" -> PropertyConstraint.MinDate(LocalDate.parse(value))
      "MAX_DATE" -> PropertyConstraint.MaxDate(LocalDate.parse(value))
      "MIN_TIME" -> PropertyConstraint.MinTime(LocalTime.parse(value))
      "MAX_TIME" -> PropertyConstraint.MaxTime(LocalTime.parse(value))
      "MIN_DATETIME" -> PropertyConstraint.MinDatetime(LocalDateTime.parse(value))
      "MAX_DATETIME" -> PropertyConstraint.MaxDatetime(LocalDateTime.parse(value))
      else -> null
    }
  }.getOrNull()
}
