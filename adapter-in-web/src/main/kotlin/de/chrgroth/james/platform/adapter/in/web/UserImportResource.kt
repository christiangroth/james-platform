package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserImportFilterMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.ImportFetchFailedError
import de.chrgroth.james.platform.domain.error.ImportInvalidUrlError
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.applicableSchemaTypes
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingSample
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.model.imports.resolveImportUrl
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

data class DataPathRow(
  val path: String,
  val displayPath: String,
  val size: Int,
)

data class JsonStructureRow(
  val path: String,
  val displayName: String,
  val typeLabel: String,
  val depthPadding: Int,
  val mandatory: Boolean,
  val isSelectedPath: Boolean,
)

data class ImportJobRow(
  val id: String,
  val installedAppId: String,
  val installedAppName: String,
  val targetEntityName: String,
  val connectionName: String,
  val urlPostfix: String,
  val statusLabel: String,
  val accepting: Boolean,
  val awaitingDataPathSelection: Boolean,
  val filterable: Boolean,
  val mappable: Boolean,
  val readyForDryRun: Boolean,
  val detectedDataPaths: List<DataPathRow>,
  val selectedDataPath: String?,
  val selectedDataPathDisplay: String?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

data class EntityOptionRow(
  val id: String,
  val name: String,
)

data class AppOptionRow(
  val id: String,
  val name: String,
  val active: Boolean,
  val entityOptions: List<EntityOptionRow>,
)

data class ConnectionOptionRow(
  val id: String,
  val name: String,
  val baseUrl: String,
)

data class SchemaFieldOptionRow(
  val path: String,
  val label: String,
  val dominantType: String,
)

data class SchemaPanelRow(
  val path: String,
  val typeLabel: String,
  val mandatory: Boolean,
  val valueRangeLabel: String,
  val stringLengthLabel: String,
)

data class FilterRuleRow(
  val mode: String,
  val sourcePath: String,
  val operator: String,
  val value: String,
  val requiresValue: Boolean,
  val includeAbsent: Boolean,
)

data class FilterModeOptionRow(
  val value: String,
  val label: String,
)

/**
 * [total] is the size of the requested matched/excluded side, [record] is null when the requested index falls
 * outside it - see [de.chrgroth.james.platform.domain.model.imports.FilterSample].
 */
data class FilterSampleResponse(
  val total: Int,
  val record: JsonNode?,
)

data class FilterOperatorOptionRow(
  val value: String,
  val label: String,
  val requiresValue: Boolean,
  val applicableTypes: String,
)

data class PropertyOptionRow(
  val id: String,
  val name: String,
)

data class ConversionOptionRow(
  val value: String,
  val label: String,
)

data class ReferenceLookupCriterionRow(
  val targetPropertyId: String,
  val sourcePath: String,
)

data class MappingPropertyRow(
  val id: String,
  val name: String,
  val typeLabel: String,
  val mandatory: Boolean,
  val constraintHint: String,
  val hasPattern: Boolean,
  val isReference: Boolean,
  val useReferenceLookup: Boolean,
  val referencedEntityPropertyOptions: List<PropertyOptionRow>,
  val referenceLookupCriteria: List<ReferenceLookupCriterionRow>,
  val sourcePath: String,
  val conversion: String,
  val hasUnit: Boolean,
  val unitFamily: String,
  val importGranularity: String,
  val fallbackValue: String,
  val issueMessages: List<String>,
)

data class DryRunIssueRow(
  val message: String,
  val staticallyChecked: Boolean,
  val expected: Boolean,
)

data class DryRunPropertyRow(
  val name: String,
  val typeLabel: String,
  val value: String,
  val hasIssue: Boolean,
  val issues: List<DryRunIssueRow>,
)

data class DryRunObjectRow(
  val index: Int,
  val sourceDataJson: String,
  val properties: List<DryRunPropertyRow>,
)

data class DryRunSkippedReasonRow(
  val propertyName: String,
  val message: String,
  val count: Int,
)

data class ReferenceLookupCriterionRequest @JsonCreator constructor(
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String?,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
)

data class ReferenceLookupRequest @JsonCreator constructor(
  @param:JsonProperty("criteria") val criteria: List<ReferenceLookupCriterionRequest>?,
)

data class FieldMappingRequest @JsonCreator constructor(
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
  @param:JsonProperty("conversion") val conversion: String?,
  @param:JsonProperty("importGranularity") val importGranularity: String?,
  @param:JsonProperty("fallbackValue") val fallbackValue: String?,
  @param:JsonProperty("referenceLookup") val referenceLookup: ReferenceLookupRequest? = null,
)

data class MappingSaveRequest @JsonCreator constructor(
  @param:JsonProperty("fieldMappings") val fieldMappings: List<FieldMappingRequest>,
)

/** [sample] is null when the requested index falls outside the job's filtered record set - see [MappingSample]. */
data class MappingSampleResponse(
  val total: Int,
  val sample: DryRunObjectRow?,
)

data class FilterRuleRequest @JsonCreator constructor(
  @param:JsonProperty("mode") val mode: String?,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
  @param:JsonProperty("operator") val operator: String?,
  @param:JsonProperty("value") val value: String?,
  @param:JsonProperty("includeAbsent") val includeAbsent: Boolean = false,
)

data class FilterSaveRequest @JsonCreator constructor(
  @param:JsonProperty("rules") val rules: List<FilterRuleRequest>,
)

@Path("/ui/user/imports")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportResource {

  @Inject
  @Location("ui/user/imports.html")
  private lateinit var importsTemplate: Template

  @Inject
  @Location("ui/user/import-job.html")
  private lateinit var importJobTemplate: Template

  @Inject
  @Location("ui/user/import-filter.html")
  private lateinit var filterTemplate: Template

  @Inject
  @Location("ui/user/import-mapping.html")
  private lateinit var mappingTemplate: Template

  @Inject
  @Location("ui/user/import-dry-run.html")
  private lateinit var dryRunTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @Inject
  private lateinit var importPort: ImportPort

  @Inject
  private lateinit var importConnectionPort: ImportConnectionPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @Inject
  private lateinit var userImportFilterMsg: UserImportFilterMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun imports(): Response = httpResponseMetrics.timed("page.user-import.list") {
    val userId = securityIdentity.principal.name
    val apps = userAppStore.getInstalledApps(userId)
    val connections = importConnectionPort.listConnections(userId).getOrNull().orEmpty()
    Response.ok(
      importsTemplate
        .data("jobs", loadAllRows(userId, apps))
        .data("appOptions", apps.map { it.toOptionRow() })
        .data("connectionOptions", connections.map { ConnectionOptionRow(it.id.value, it.name, it.baseUrl) })
        .data("hasConnections", connections.isNotEmpty()),
    ).build()
  }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun importsTable(): Any = httpResponseMetrics.timed("fragment.user-import.imports-table") {
    val userId = securityIdentity.principal.name
    importsTemplate.getFragment("imports_table")
      .data("jobs", loadAllRows(userId, userAppStore.getInstalledApps(userId)))
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerImport(
    @FormParam("installedAppId") installedAppId: String?,
    @FormParam("connectionId") connectionId: String?,
    @FormParam("targetEntityDefinitionId") targetEntityDefinitionId: String?,
    @FormParam("urlPostfix") urlPostfix: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.trigger") {
    val userId = securityIdentity.principal.name
    if (installedAppId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userInstalledAppNotFoundError())).build()
    }
    if (connectionId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportConnectionRequiredError())).build()
    }
    if (targetEntityDefinitionId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportEntityRequiredError())).build()
    }
    importPort.triggerImport(userId, installedAppId, connectionId, targetEntityDefinitionId, urlPostfix).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code), errorDetails = importErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportCreatedMessage())).build() },
    )
  }

  @POST
  @Path("/test-connection")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun testConnection(
    @FormParam("connectionId") connectionId: String?,
    @FormParam("urlPostfix") urlPostfix: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.test-connection") {
    val userId = securityIdentity.principal.name
    if (connectionId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportConnectionRequiredError())).build()
    }
    importPort.testConnectionUrl(userId, connectionId, urlPostfix).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code), errorDetails = importErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportTestConnectionSuccessMessage())).build() },
    )
  }

  /** Always opens the furthest step the job has reached, so navigating to an import (e.g. from the imports list) never lands on an earlier, already-completed step. */
  @GET
  @Path("/{importJobId}")
  @Produces(MediaType.TEXT_HTML)
  fun importJob(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("page.user-import.job") {
    val userId = securityIdentity.principal.name
    val view = importPort.getMappingView(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val step = when {
      view.importDefinition.mapping != null -> "dry-run"
      view.importJob.status == ImportStatus.DATA_IDENTIFIED -> "mapping"
      else -> "overview"
    }
    Response.seeOther(URI.create("/ui/user/imports/$importJobId/$step")).build()
  }

  @GET
  @Path("/{importJobId}/overview")
  @Produces(MediaType.TEXT_HTML)
  fun importJobOverview(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("page.user-import.job-overview") {
    val userId = securityIdentity.principal.name
    val view = importPort.getMappingView(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val info = userAppStore.getInstalledApp(userId, view.importJob.installedAppId.value).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityCount = info.installedVersion.entityDefinitions.size
    val connection = importConnectionPort.listConnections(userId).getOrNull().orEmpty().firstOrNull { it.id == view.importDefinition.connectionId }
    Response.ok(
      importJobTemplate
        .data(
          "job",
          view.importJob.toRow(
            mapOf(view.importDefinition.id.value to view.importDefinition),
            mapOf(view.targetEntityDefinition.id.value to view.targetEntityDefinition.name),
            mapOf(info.installedAppId to info.appName),
            emptyMap(),
          ),
        )
        .data("targetEntityName", view.targetEntityDefinition.name)
        .data("targetEntityUrl", entityListUrl(info.installedAppId, view.targetEntityDefinition.id.value, entityCount))
        .data("pageHeading", pageHeading(userId, view.importDefinition.connectionId, info.appName, view.targetEntityDefinition.name))
        .data("sourceUrl", connection?.let { resolveImportUrl(it.baseUrl, view.importDefinition.urlPostfix) }.orEmpty())
        .data("structureRows", buildJsonStructureRows(view.importJob, view.importDefinition.selectedDataPath))
        .data("schemaPanelRows", buildSchemaPanelRows(view.importJob.detectedSchema))
        .data("appActive", info.appActive),
    ).build()
  }

  /** The entity's data list: the app detail page itself when it is the only entity type, otherwise its dedicated entity page. */
  private fun entityListUrl(installedAppId: String, entityTypeId: String, entityCount: Int): String =
    if (entityCount <= 1) "/ui/user/apps/$installedAppId" else "/ui/user/apps/$installedAppId/entities/$entityTypeId"

  /** Constant heading shown on the Quelle/Mapping/Dry-Run pages of an import job: "$connection: $app $entity". */
  private fun pageHeading(userId: String, connectionId: ImportConnectionId, installedAppName: String, targetEntityName: String): String {
    val connectionName = importConnectionPort.listConnections(userId).getOrNull().orEmpty().firstOrNull { it.id == connectionId }?.name.orEmpty()
    return userMsg.userImportPageHeading(connectionName, installedAppName, targetEntityName)
  }

  @POST
  @Path("/{importJobId}/select-path")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun selectDataPath(
    @PathParam("importJobId") importJobId: String,
    @FormParam("dataPath") dataPath: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.select-path") {
    val userId = securityIdentity.principal.name
    if (dataPath.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportBlankDataPathError())).build()
    }
    importPort.selectDataPath(userId, importJobId, normalizeDataPathInput(dataPath)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDataPathSelectedMessage())).build() },
    )
  }

  @GET
  @Path("/{importJobId}/filter")
  @Produces(MediaType.TEXT_HTML)
  fun filter(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("page.user-import.filter") {
    val userId = securityIdentity.principal.name
    val view = importPort.getFilterView(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val info = userAppStore.getInstalledApp(userId, view.importJob.installedAppId.value).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val targetEntityName = installedTargetEntityName(info, view.importDefinition.targetEntityDefinitionId.value)

    Response.ok(
      filterTemplate
        .data("importJobId", importJobId)
        .data("targetEntityName", targetEntityName)
        .data("pageHeading", pageHeading(userId, view.importDefinition.connectionId, info.appName, targetEntityName))
        .data("filterRuleRows", view.importDefinition.filterRules.map { it.toRow() })
        .data("schemaFieldOptions", view.importJob.detectedSchema.map { SchemaFieldOptionRow(it.path, schemaFieldLabel(it), dominantSchemaType(it)?.name.orEmpty()) })
        .data("schemaPanelRows", buildSchemaPanelRows(view.importJob.detectedSchema))
        .data("modeOptions", FilterMode.entries.map { FilterModeOptionRow(it.name, filterModeLabel(it)) })
        .data("operatorOptions", FilterOperator.entries.map { FilterOperatorOptionRow(it.name, filterOperatorLabel(it), it.requiresValue, it.applicableSchemaTypes().joinToString(",") { type -> type.name }) })
        .data("totalRecordCount", view.totalRecordCount)
        .data("matchingRecordCount", view.matchingRecordCount)
        .data("awaitingDataPathSelection", view.importJob.status == ImportStatus.DOWNLOADED)
        .data("filterable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("mappable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("readyForDryRun", view.importDefinition.mapping != null)
        .data("appActive", info.appActive),
    ).build()
  }

  @POST
  @Path("/{importJobId}/filter")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun saveFilter(
    @PathParam("importJobId") importJobId: String,
    request: FilterSaveRequest,
  ): Response = httpResponseMetrics.timed("rest.user-import.filter-save") {
    val userId = securityIdentity.principal.name
    val filterRules = request.rules.mapNotNull { it.toDomainOrNull() }

    importPort.updateFilter(userId, importJobId, filterRules).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { view -> Response.ok(DeveloperApiResult(true, userMsg.userImportFilterSavedMessage(view.matchingRecordCount, view.totalRecordCount))).build() },
    )
  }

  @GET
  @Path("/{importJobId}/filter/values")
  @Produces(MediaType.APPLICATION_JSON)
  fun filterFieldValues(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("sourcePath") sourcePath: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.filter-values") {
    val userId = securityIdentity.principal.name
    if (sourcePath.isNullOrBlank()) {
      return@timed Response.ok(emptyList<String>()).build()
    }
    importPort.resolveFilterFieldValues(userId, importJobId, sourcePath).fold(
      ifLeft = { Response.ok(emptyList<String>()).build() },
      ifRight = { values -> Response.ok(values).build() },
    )
  }

  @GET
  @Path("/{importJobId}/filter/sample")
  @Produces(MediaType.APPLICATION_JSON)
  fun filterSample(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("matched") matched: Boolean?,
    @QueryParam("index") index: Int?,
  ): Response = httpResponseMetrics.timed("rest.user-import.filter-sample") {
    val userId = securityIdentity.principal.name
    importPort.resolveFilterSample(userId, importJobId, matched ?: true, index?.takeIf { it >= 0 } ?: 0).fold(
      ifLeft = { Response.ok(FilterSampleResponse(0, null)).build() },
      ifRight = { sample -> Response.ok(FilterSampleResponse(sample.total, sample.sourceDataJson?.let { objectMapper.readTree(it) })).build() },
    )
  }

  private fun installedTargetEntityName(info: InstalledAppInfo, entityDefinitionId: String): String =
    info.installedVersion.entityDefinitions.find { it.id.value == entityDefinitionId }?.name.orEmpty()

  private fun FilterRuleRequest.toDomainOrNull(): FilterRule? {
    val ruleMode = mode?.let { runCatching { FilterMode.valueOf(it) }.getOrNull() } ?: return null
    val ruleSourcePath = sourcePath?.takeIf { it.isNotBlank() } ?: return null
    val ruleOperator = operator?.let { runCatching { FilterOperator.valueOf(it) }.getOrNull() } ?: return null
    val ruleValue = value?.takeIf { it.isNotBlank() }
    if (ruleOperator.requiresValue && ruleValue == null) return null
    return FilterRule(ruleMode, ruleSourcePath, ruleOperator, ruleValue, includeAbsent)
  }

  private fun FilterRule.toRow() = FilterRuleRow(
    mode = mode.name,
    sourcePath = sourcePath,
    operator = operator.name,
    value = value.orEmpty(),
    requiresValue = operator.requiresValue,
    includeAbsent = includeAbsent,
  )

  private val FilterOperator.requiresValue: Boolean
    get() = this != FilterOperator.IS_NULL && this != FilterOperator.IS_NOT_NULL

  private fun filterModeLabel(mode: FilterMode): String = when (mode) {
    FilterMode.INCLUDE -> userImportFilterMsg.userImportFilterModeInclude()
    FilterMode.EXCLUDE -> userImportFilterMsg.userImportFilterModeExclude()
  }

  private fun filterOperatorLabel(operator: FilterOperator): String = when (operator) {
    FilterOperator.IS_NULL -> userImportFilterMsg.userImportFilterOperatorIsNull()
    FilterOperator.IS_NOT_NULL -> userImportFilterMsg.userImportFilterOperatorIsNotNull()
    FilterOperator.EQUALS -> userImportFilterMsg.userImportFilterOperatorEquals()
    FilterOperator.NOT_EQUALS -> userImportFilterMsg.userImportFilterOperatorNotEquals()
    FilterOperator.CONTAINS -> userImportFilterMsg.userImportFilterOperatorContains()
    FilterOperator.MATCHES -> userImportFilterMsg.userImportFilterOperatorMatches()
    FilterOperator.NOT_MATCHES -> userImportFilterMsg.userImportFilterOperatorNotMatches()
    FilterOperator.GREATER_THAN -> userImportFilterMsg.userImportFilterOperatorGreaterThan()
    FilterOperator.GREATER_THAN_OR_EQUAL -> userImportFilterMsg.userImportFilterOperatorGreaterThanOrEqual()
    FilterOperator.LESS_THAN -> userImportFilterMsg.userImportFilterOperatorLessThan()
    FilterOperator.LESS_THAN_OR_EQUAL -> userImportFilterMsg.userImportFilterOperatorLessThanOrEqual()
  }

  @GET
  @Path("/{importJobId}/mapping")
  @Produces(MediaType.TEXT_HTML)
  fun mapping(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("page.user-import.mapping") {
    val userId = securityIdentity.principal.name
    val view = importPort.getMappingView(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val info = userAppStore.getInstalledApp(userId, view.importJob.installedAppId.value).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )

    Response.ok(
      mappingTemplate
        .data("importJobId", importJobId)
        .data("isReady", view.importJob.status == ImportStatus.READY)
        .data("targetEntityName", view.targetEntityDefinition.name)
        .data("pageHeading", pageHeading(userId, view.importDefinition.connectionId, info.appName, view.targetEntityDefinition.name))
        .data("propertyRows", buildPropertyRows(view.targetEntityDefinition, view.importDefinition.mapping, view))
        .data("schemaFieldOptions", view.importJob.detectedSchema.map { SchemaFieldOptionRow(it.path, schemaFieldLabel(it), dominantSchemaType(it)?.name.orEmpty()) })
        .data("schemaPanelRows", buildSchemaPanelRows(view.importJob.detectedSchema))
        .data("conversionOptions", FieldMappingConversion.entries.map { ConversionOptionRow(it.name, conversionLabel(it)) })
        .data("awaitingDataPathSelection", view.importJob.status == ImportStatus.DOWNLOADED)
        .data("filterable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("mappable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("readyForDryRun", view.importDefinition.mapping != null)
        .data("appActive", info.appActive),
    ).build()
  }

  @POST
  @Path("/{importJobId}/mapping")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun saveMapping(
    @PathParam("importJobId") importJobId: String,
    request: MappingSaveRequest,
  ): Response = httpResponseMetrics.timed("rest.user-import.mapping-save") {
    val userId = securityIdentity.principal.name
    importPort.updateMapping(userId, importJobId, request.fieldMappings.toFieldMappings()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { view ->
        val message = if (view.validation?.isReady == true) userMsg.userImportMappingStatusReadyMessage() else userMsg.userImportMappingStatusIncompleteMessage()
        Response.ok(DeveloperApiResult(true, message)).build()
      },
    )
  }

  @POST
  @Path("/{importJobId}/mapping/sample")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun mappingSample(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("index") index: Int?,
    request: MappingSaveRequest,
  ): Response = httpResponseMetrics.timed("rest.user-import.mapping-sample") {
    val userId = securityIdentity.principal.name
    val view = importPort.getMappingView(userId, importJobId).fold(
      ifLeft = { return@timed Response.ok(MappingSampleResponse(0, null)).build() },
      ifRight = { it },
    )
    importPort.resolveMappingSample(userId, importJobId, index?.takeIf { it >= 0 } ?: 0, request.fieldMappings.toFieldMappings()).fold(
      ifLeft = { Response.ok(MappingSampleResponse(0, null)).build() },
      ifRight = { sample -> Response.ok(MappingSampleResponse(sample.total, sample.dryRunObject?.toRow(view.targetEntityDefinition))).build() },
    )
  }

  /**
   * Parses request fields into domain [FieldMapping]s, shared by saving the mapping and the live preview - a field
   * with no source path, fallback value, or reference lookup is dropped rather than kept as an empty mapping.
   */
  private fun List<FieldMappingRequest>.toFieldMappings(): List<FieldMapping> = mapNotNull { field ->
    if (field.targetPropertyId.isBlank()) return@mapNotNull null
    val sourcePath = field.sourcePath?.takeIf { it.isNotBlank() }
    val fallbackValue = field.fallbackValue?.takeIf { it.isNotBlank() }
    val referenceLookup = field.referenceLookup?.criteria.orEmpty()
      .mapNotNull { criterion ->
        val criterionTargetPropertyId = criterion.targetPropertyId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val criterionSourcePath = criterion.sourcePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ReferenceLookupCriterion(PropertyId(criterionTargetPropertyId), criterionSourcePath)
      }
      .takeIf { it.isNotEmpty() }
      ?.let { ReferenceLookup(it) }
    if (sourcePath == null && fallbackValue == null && referenceLookup == null) return@mapNotNull null
    FieldMapping(
      targetPropertyId = PropertyId(field.targetPropertyId),
      sourcePath = sourcePath,
      conversion = runCatching { FieldMappingConversion.valueOf(field.conversion ?: FieldMappingConversion.NONE.name) }.getOrDefault(FieldMappingConversion.NONE),
      importGranularity = field.importGranularity?.takeIf { it.isNotBlank() },
      fallbackValue = fallbackValue,
      referenceLookup = referenceLookup,
    )
  }

  @GET
  @Path("/{importJobId}/dry-run")
  @Produces(MediaType.TEXT_HTML)
  fun dryRun(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("page.user-import.dry-run") {
    val userId = securityIdentity.principal.name
    val view = importPort.getMappingView(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val info = userAppStore.getInstalledApp(userId, view.importJob.installedAppId.value).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val report = importPort.dryRun(userId, importJobId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/imports/$importJobId/mapping")).build() },
      ifRight = { it },
    )

    Response.ok(
      dryRunTemplate
        .data("importJobId", importJobId)
        .data("targetEntityName", view.targetEntityDefinition.name)
        .data("pageHeading", pageHeading(userId, view.importDefinition.connectionId, info.appName, view.targetEntityDefinition.name))
        .data("totalCount", report.totalCount)
        .data("validCount", report.validCount)
        .data("skippedCount", report.skippedCount)
        .data("invalidCount", report.invalidCount)
        .data("validObjectsColumns", view.targetEntityDefinition.properties.map { it.name })
        .data("validObjects", report.validObjects.map { it.toRow(view.targetEntityDefinition) })
        .data("invalidObjects", report.invalidObjects.map { it.toRow(view.targetEntityDefinition) })
        .data("skippedReasons", buildSkippedReasonRows(report.skippedObjects, view.targetEntityDefinition))
        .data("awaitingDataPathSelection", view.importJob.status == ImportStatus.DOWNLOADED)
        .data("filterable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("mappable", view.importJob.status == ImportStatus.DATA_IDENTIFIED || view.importJob.status == ImportStatus.READY)
        .data("readyForDryRun", view.importDefinition.mapping != null)
        .data("appActive", info.appActive),
    ).build()
  }

  @POST
  @Path("/{importJobId}/dry-run/accept")
  @Produces(MediaType.APPLICATION_JSON)
  fun acceptDryRun(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("mode") mode: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.dry-run-accept") {
    val userId = securityIdentity.principal.name
    val replaceExisting = mode == "REPLACE"
    importPort.acceptDryRun(userId, importJobId, replaceExisting).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDryRunQueuedMessage(), redirectUrl = "/ui/user/imports")).build() },
    )
  }

  @POST
  @Path("/{importJobId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteImport(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("rest.user-import.delete") {
    val userId = securityIdentity.principal.name
    importPort.deleteImportJob(userId, importJobId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDeletedMessage())).build() },
    )
  }

  private fun loadAllRows(userId: String, apps: List<InstalledAppInfo>): List<ImportJobRow> {
    val appNamesById = apps.associate { it.installedAppId to it.appName }
    val entityNamesById = apps.flatMap { it.installedVersion.entityDefinitions }.associate { it.id.value to it.name }
    val connectionNamesById = importConnectionPort.listConnections(userId).getOrNull().orEmpty().associate { it.id.value to it.name }
    val definitionsById = importPort.listAllImportDefinitions(userId).associateBy { it.id.value }
    return importPort.listAllImportJobs(userId).map { it.toRow(definitionsById, entityNamesById, appNamesById, connectionNamesById) }
  }

  private fun InstalledAppInfo.toOptionRow() = AppOptionRow(
    id = installedAppId,
    name = appName,
    active = appActive,
    entityOptions = installedVersion.entityDefinitions.map { EntityOptionRow(it.id.value, it.name) },
  )

  private fun ImportJob.toRow(
    definitionsById: Map<String, ImportDefinition>,
    entityNamesById: Map<String, String>,
    appNamesById: Map<String, String>,
    connectionNamesById: Map<String, String>,
  ): ImportJobRow {
    val definition = definitionsById[importDefinitionId.value]
    return ImportJobRow(
      id = id.value,
      installedAppId = installedAppId.value,
      installedAppName = appNamesById[installedAppId.value].orEmpty(),
      targetEntityName = definition?.let { entityNamesById[it.targetEntityDefinitionId.value] }.orEmpty(),
      connectionName = definition?.let { connectionNamesById[it.connectionId.value] }.orEmpty(),
      urlPostfix = definition?.urlPostfix.orEmpty(),
      statusLabel = statusLabel(status),
      accepting = status == ImportStatus.ACCEPTING,
      awaitingDataPathSelection = status == ImportStatus.DOWNLOADED,
      filterable = status == ImportStatus.DATA_IDENTIFIED || status == ImportStatus.READY,
      mappable = status == ImportStatus.DATA_IDENTIFIED || status == ImportStatus.READY,
      readyForDryRun = definition?.mapping != null,
      detectedDataPaths = detectedDataPaths.map { it.toRow() },
      selectedDataPath = definition?.selectedDataPath,
      selectedDataPathDisplay = definition?.selectedDataPath?.let { formatDataPath(it) },
      createdAt = createdAt,
      lastChangedAt = lastChangedAt,
    )
  }

  private fun DataPath.toRow() = DataPathRow(
    path = path,
    displayPath = formatDataPath(path),
    size = size,
  )

  /**
   * Walks the whole downloaded JSON document (not just the objects at the selected data path) so users can see
   * why other candidate paths weren't picked. Fields inside the selected array get their mandatory flag and
   * aggregated type from [ImportJob.detectedSchema] (computed across all array elements); everything else is
   * typed from a single sample, since no other part of the document has multiple elements to aggregate over.
   */
  private fun buildJsonStructureRows(job: ImportJob, selectedDataPath: String?): List<JsonStructureRow> {
    val root = runCatching { objectMapper.readTree(job.payload) }.getOrNull() ?: return emptyList()
    val mandatoryByPath = selectedDataPath
      ?.let { selected -> job.detectedSchema.associate { "$selected.${it.path}" to it.mandatory } }
      .orEmpty()
    val rows = mutableListOf<JsonStructureRow>()
    appendJsonStructureRows(root, path = "", depth = 0, selectedDataPath = selectedDataPath, mandatoryByPath = mandatoryByPath, rows = rows)
    return rows
  }

  private fun appendJsonStructureRows(
    node: JsonNode,
    path: String,
    depth: Int,
    selectedDataPath: String?,
    mandatoryByPath: Map<String, Boolean>,
    rows: MutableList<JsonStructureRow>,
  ) {
    if (!node.isObject) return
    node.properties().forEach { (name, child) ->
      val childPath = if (path.isEmpty()) name else "$path.$name"
      val typeLabel = if (child.isArray) "${userMsg.userImportSchemaTypeArray()} (${userMsg.userImportDataPathSizeLabel(child.size())})" else jsonStructureTypeLabel(child)
      rows += JsonStructureRow(
        path = childPath,
        displayName = name,
        typeLabel = typeLabel,
        depthPadding = depth * 20,
        mandatory = mandatoryByPath[childPath] == true,
        isSelectedPath = childPath == selectedDataPath,
      )
      when {
        child.isObject -> appendJsonStructureRows(child, childPath, depth + 1, selectedDataPath, mandatoryByPath, rows)
        child.isArray && child.size() > 0 && child[0].isObject -> appendJsonStructureRows(child[0], childPath, depth + 1, selectedDataPath, mandatoryByPath, rows)
      }
    }
  }

  private fun jsonStructureTypeLabel(node: com.fasterxml.jackson.databind.JsonNode): String = when {
    node.isTextual -> userMsg.userImportSchemaTypeString()
    node.isIntegralNumber -> userMsg.userImportSchemaTypeLong()
    node.isFloatingPointNumber -> userMsg.userImportSchemaTypeDouble()
    node.isBoolean -> userMsg.userImportSchemaTypeBoolean()
    node.isObject -> userMsg.userImportSchemaTypeObject()
    else -> userMsg.userImportSchemaTypeNull()
  }

  /** Displays a dot-separated internal data path (e.g. `data.items`) in the leading-slash form users expect (`/data/items`). */
  private fun formatDataPath(path: String): String = "/" + path.replace(".", "/")

  /** Accepts both the leading-slash display form and the raw dot-separated internal form for manually entered data paths. */
  private fun normalizeDataPathInput(raw: String): String = raw.trim().removePrefix("/").replace("/", ".")

  private fun DryRunObject.toRow(entityDefinition: EntityDefinition): DryRunObjectRow {
    val issuesByProperty = issues.groupBy { it.targetPropertyId }
    val properties = entityDefinition.properties.map { property ->
      val propertyIssues = issuesByProperty[property.id].orEmpty()
      DryRunPropertyRow(
        name = property.name,
        typeLabel = PropertyLabelTemplateExtensions.propertyTypeLabel(property.type),
        value = targetData[property.id].orEmpty(),
        hasIssue = propertyIssues.isNotEmpty(),
        issues = propertyIssues.map { DryRunIssueRow(dryRunIssueMessage(it), it.staticallyChecked, it.expected) },
      )
    }
    return DryRunObjectRow(index + 1, prettyPrintedSourceData(sourceDataJson), properties)
  }

  /** Groups a fan-in mapping's skipped objects by (property, reason) instead of listing every individual object, which would otherwise flood the report with noise. */
  private fun buildSkippedReasonRows(skippedObjects: List<DryRunObject>, entityDefinition: EntityDefinition): List<DryRunSkippedReasonRow> {
    val propertyNamesById = entityDefinition.properties.associate { it.id to it.name }
    return skippedObjects
      .flatMap { it.issues }
      .groupBy { it.targetPropertyId to dryRunIssueMessage(it) }
      .map { (propertyIdAndMessage, issues) -> DryRunSkippedReasonRow(propertyNamesById[propertyIdAndMessage.first].orEmpty(), propertyIdAndMessage.second, issues.size) }
      .sortedByDescending { it.count }
  }

  private fun dryRunIssueMessage(issue: DryRunIssue): String = when (issue) {
    is DryRunIssue.MissingMandatoryValue -> userMsg.userImportMappingIssueMissingMandatory()
    is DryRunIssue.ConstraintViolated -> PropertyLabelTemplateExtensions.constraintViolationMessage(issue.violation)
  }

  private fun prettyPrintedSourceData(json: String): String = runCatching { objectMapper.readTree(json).toPrettyString() }.getOrDefault(json)

  private fun buildPropertyRows(entityDefinition: EntityDefinition, mapping: Mapping?, view: MappingView): List<MappingPropertyRow> {
    val fieldMappingsByProperty = mapping?.fieldMappings?.associateBy { it.targetPropertyId }.orEmpty()
    val issuesByProperty = view.validation?.issues?.groupBy { it.targetPropertyId }.orEmpty()
    val entityDefinitionsById = view.entityDefinitions.associateBy { it.id }
    return entityDefinition.properties.map { property ->
      val fieldMapping = fieldMappingsByProperty[property.id]
      val issues = issuesByProperty[property.id].orEmpty()
      val referencedEntity = property.targetEntityId?.let { entityDefinitionsById[it] }
      MappingPropertyRow(
        id = property.id.value,
        name = property.name,
        typeLabel = PropertyLabelTemplateExtensions.propertyTypeLabel(property.type),
        mandatory = !property.nullable,
        constraintHint = PropertyLabelTemplateExtensions.constraintHint(property),
        hasPattern = property.constraints.any { it is PropertyConstraint.Pattern },
        isReference = property.type == PropertyType.REF,
        useReferenceLookup = fieldMapping?.referenceLookup != null,
        referencedEntityPropertyOptions = referencedEntity?.properties
          ?.filterNot { it.type == PropertyType.LIST || it.type == PropertyType.OBJECT }
          ?.map { PropertyOptionRow(it.id.value, it.name) }
          .orEmpty(),
        referenceLookupCriteria = fieldMapping?.referenceLookup?.criteria.orEmpty()
          .map { ReferenceLookupCriterionRow(it.targetPropertyId.value, it.sourcePath) },
        sourcePath = fieldMapping?.sourcePath.orEmpty(),
        conversion = (fieldMapping?.conversion ?: FieldMappingConversion.NONE).name,
        hasUnit = property.unit != null,
        unitFamily = property.unit?.family?.name.orEmpty(),
        importGranularity = fieldMapping?.importGranularity.orEmpty(),
        fallbackValue = fieldMapping?.fallbackValue.orEmpty(),
        issueMessages = issues.filterNot { it is MappingIssue.NotStaticallyValidated }.map { issueMessage(it) },
      )
    }
  }

  private fun issueMessage(issue: MappingIssue): String = when (issue) {
    is MappingIssue.MissingMandatoryField -> userMsg.userImportMappingIssueMissingMandatory()
    is MappingIssue.IncompatibleType -> userMsg.userImportMappingIssueIncompatibleType(schemaTypeLabel(issue.sourceType), PropertyLabelTemplateExtensions.propertyTypeLabel(issue.targetType))
    is MappingIssue.NumericRangeViolation -> userMsg.userImportMappingIssueNumericRange(formatNumber(issue.observedMin), formatNumber(issue.observedMax))
    is MappingIssue.StringLengthViolation -> userMsg.userImportMappingIssueStringLength(issue.observedMinLength, issue.observedMaxLength)
    is MappingIssue.NotStaticallyValidated -> userMsg.userImportMappingIssueNotStaticallyValidated(issue.regex)
    is MappingIssue.ReferenceLookupMissingCriteria -> userMsg.userImportMappingIssueReferenceLookupMissingCriteria()
    is MappingIssue.ReferenceLookupInvalidCriterion -> userMsg.userImportMappingIssueReferenceLookupInvalidCriterion()
    is MappingIssue.ReferenceLookupIncompatibleType ->
      userMsg.userImportMappingIssueIncompatibleType(schemaTypeLabel(issue.sourceType), PropertyLabelTemplateExtensions.propertyTypeLabel(issue.targetType))
    is MappingIssue.FallbackValueViolatesConstraint -> userMsg.userImportMappingIssueFallbackValueViolatesConstraint(
      PropertyLabelTemplateExtensions.constraintViolationMessage(issue.violation),
    )
    is MappingIssue.MissingImportGranularity -> userMsg.userImportMappingIssueMissingImportGranularity()
  }

  private fun formatNumber(value: Double): String = if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

  private fun schemaFieldLabel(property: SchemaProperty): String {
    val dominantType = dominantSchemaType(property)
    val typeLabel = dominantType?.let { schemaTypeLabel(it) }
    return if (typeLabel != null) "${property.path} ($typeLabel)" else property.path
  }

  private fun dominantSchemaType(property: SchemaProperty): SchemaPropertyType? =
    property.typeCounts.filterKeys { it != SchemaPropertyType.NULL }.maxByOrNull { it.value }?.key

  /** Builds the rows for the schema panel offcanvas, shared identically across the Quelle, Filter and Mapping steps. */
  private fun buildSchemaPanelRows(schema: List<SchemaProperty>): List<SchemaPanelRow> = schema.map { property ->
    SchemaPanelRow(
      path = property.path,
      typeLabel = dominantSchemaType(property)?.let { schemaTypeLabel(it) }.orEmpty(),
      mandatory = property.mandatory,
      valueRangeLabel = property.numericRange?.let { userMsg.userImportSchemaValueRangeLabel(formatNumber(it.min), formatNumber(it.max)) }.orEmpty(),
      stringLengthLabel = property.stringLengthCounts.keys.takeIf { it.isNotEmpty() }?.let { userMsg.userImportSchemaStringLengthLabel(it.min(), it.max()) }.orEmpty(),
    )
  }

  private fun schemaTypeLabel(type: SchemaPropertyType): String = when (type) {
    SchemaPropertyType.STRING -> userMsg.userImportSchemaTypeString()
    SchemaPropertyType.DATE -> userMsg.userImportSchemaTypeDate()
    SchemaPropertyType.DATETIME -> userMsg.userImportSchemaTypeDatetime()
    SchemaPropertyType.LONG -> userMsg.userImportSchemaTypeLong()
    SchemaPropertyType.DOUBLE -> userMsg.userImportSchemaTypeDouble()
    SchemaPropertyType.BOOLEAN -> userMsg.userImportSchemaTypeBoolean()
    SchemaPropertyType.OBJECT -> userMsg.userImportSchemaTypeObject()
    SchemaPropertyType.ARRAY -> userMsg.userImportSchemaTypeArray()
    SchemaPropertyType.NULL -> userMsg.userImportSchemaTypeNull()
  }

  private fun conversionLabel(conversion: FieldMappingConversion): String = when (conversion) {
    FieldMappingConversion.NONE -> userMsg.userImportMappingConversionNone()
    FieldMappingConversion.STRING_TO_LONG -> userMsg.userImportMappingConversionStringToLong()
    FieldMappingConversion.STRING_TO_DOUBLE -> userMsg.userImportMappingConversionStringToDouble()
    FieldMappingConversion.STRING_TO_BOOLEAN -> userMsg.userImportMappingConversionStringToBoolean()
    FieldMappingConversion.LONG_TO_DOUBLE -> userMsg.userImportMappingConversionLongToDouble()
    FieldMappingConversion.LONG_TO_STRING -> userMsg.userImportMappingConversionLongToString()
    FieldMappingConversion.DOUBLE_TO_STRING -> userMsg.userImportMappingConversionDoubleToString()
    FieldMappingConversion.BOOLEAN_TO_STRING -> userMsg.userImportMappingConversionBooleanToString()
    FieldMappingConversion.STRING_TO_DATE -> userMsg.userImportMappingConversionStringToDate()
    FieldMappingConversion.STRING_TO_DATETIME -> userMsg.userImportMappingConversionStringToDatetime()
    FieldMappingConversion.DATETIME_TO_DATE -> userMsg.userImportMappingConversionDatetimeToDate()
  }

  private fun statusLabel(status: ImportStatus): String = when (status) {
    ImportStatus.DOWNLOADED -> userMsg.userImportStatusDownloaded()
    ImportStatus.DATA_IDENTIFIED -> userMsg.userImportStatusDataIdentified()
    ImportStatus.READY -> userMsg.userImportStatusReady()
    ImportStatus.ACCEPTING -> userMsg.userImportStatusAccepting()
  }

  private fun importErrorMessage(code: String): String = when (code) {
    ImportError.INSTALLED_APP_NOT_FOUND.code -> userMsg.userInstalledAppNotFoundError()
    ImportError.INVALID_URL.code -> userMsg.userImportInvalidUrlError()
    ImportError.FETCH_FAILED.code -> userMsg.userImportFetchFailedError()
    ImportError.INVALID_JSON_RESPONSE.code -> userMsg.userImportInvalidJsonError()
    ImportError.NOT_A_JSON_OBJECT.code -> userMsg.userImportNotJsonObjectError()
    ImportError.RESPONSE_TOO_LARGE.code -> userMsg.userImportResponseTooLargeError()
    ImportError.IMPORT_JOB_NOT_FOUND.code -> userMsg.userImportJobNotFoundError()
    ImportError.IMPORT_JOB_NOT_DOWNLOADED.code -> userMsg.userImportJobNotDownloadedError()
    ImportError.BLANK_DATA_PATH.code -> userMsg.userImportBlankDataPathError()
    ImportError.INVALID_DATA_PATH.code -> userMsg.userImportInvalidDataPathError()
    ImportError.IMPORT_JOB_NOT_MAPPABLE.code -> userMsg.userImportJobNotMappableError()
    ImportError.ENTITY_DEFINITION_NOT_FOUND.code -> userMsg.userImportEntityDefinitionNotFoundError()
    ImportError.MAPPING_PROPERTY_NOT_FOUND.code -> userMsg.userImportMappingPropertyNotFoundError()
    ImportError.IMPORT_JOB_NOT_READY.code -> userMsg.userImportJobNotReadyError()
    ImportError.CONNECTION_NOT_FOUND.code -> userMsg.userImportConnectionNotFoundError()
    ImportError.IMPORT_JOB_NOT_FILTERABLE.code -> userMsg.userImportJobNotFilterableError()
    else -> msg.commonUnexpectedError()
  }

  private fun importErrorDetails(error: DomainError): List<String>? = when (error) {
    is ImportInvalidUrlError -> listOf(error.detail)
    is ImportFetchFailedError -> listOf(error.detail)
    else -> null
  }

  companion object {
    private val objectMapper = ObjectMapper()
  }
}
