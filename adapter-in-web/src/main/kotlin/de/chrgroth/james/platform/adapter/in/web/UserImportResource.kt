package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
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
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookup
import de.chrgroth.james.platform.domain.model.imports.ReferenceLookupCriterion
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

data class DataPathRow(
  val path: String,
  val size: Int,
)

data class ImportJobRow(
  val id: String,
  val targetEntityName: String,
  val statusLabel: String,
  val awaitingDataPathSelection: Boolean,
  val mappable: Boolean,
  val readyForDryRun: Boolean,
  val detectedDataPaths: List<DataPathRow>,
  val selectedDataPath: String?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

data class EntityOptionRow(
  val id: String,
  val name: String,
)

data class ConnectionOptionRow(
  val id: String,
  val name: String,
)

data class SchemaFieldOptionRow(
  val path: String,
  val label: String,
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
  val fallbackValue: String,
  val issueMessages: List<String>,
)

data class DryRunIssueRow(
  val message: String,
  val staticallyChecked: Boolean,
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
  @param:JsonProperty("fallbackValue") val fallbackValue: String?,
  @param:JsonProperty("referenceLookup") val referenceLookup: ReferenceLookupRequest? = null,
)

data class MappingSaveRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String,
  @param:JsonProperty("fieldMappings") val fieldMappings: List<FieldMappingRequest>,
)

@Path("/ui/user/imports")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportResource {

  @Inject
  @Location("ui/user/app-imports.html")
  private lateinit var importsTemplate: Template

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
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Path("/{installedAppId}")
  @Produces(MediaType.TEXT_HTML)
  fun imports(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("page.user-import.list") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityDefinitions = info.installedVersion.entityDefinitions
    val connections = importConnectionPort.listConnections(userId).getOrNull().orEmpty()
    Response.ok(
      importsTemplate
        .data("info", info)
        .data("jobs", loadRows(userId, installedAppId, entityDefinitions))
        .data("entityOptions", entityDefinitions.map { EntityOptionRow(it.id.value, it.name) })
        .data("connectionOptions", connections.map { ConnectionOptionRow(it.id.value, it.name) })
        .data("hasConnections", connections.isNotEmpty()),
    ).build()
  }

  @GET
  @Path("/{installedAppId}/table")
  @Produces(MediaType.TEXT_HTML)
  fun importsTable(@PathParam("installedAppId") installedAppId: String): Any = httpResponseMetrics.timed("fragment.user-import.imports-table") {
    val userId = securityIdentity.principal.name
    val entityDefinitions = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion?.entityDefinitions.orEmpty()
    importsTemplate.getFragment("imports_table")
      .data("jobs", loadRows(userId, installedAppId, entityDefinitions))
  }

  @POST
  @Path("/{installedAppId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerImport(
    @PathParam("installedAppId") installedAppId: String,
    @FormParam("connectionId") connectionId: String?,
    @FormParam("targetEntityDefinitionId") targetEntityDefinitionId: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import.trigger") {
    val userId = securityIdentity.principal.name
    if (connectionId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportConnectionRequiredError())).build()
    }
    if (targetEntityDefinitionId.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportEntityRequiredError())).build()
    }
    importPort.triggerImport(userId, installedAppId, connectionId, targetEntityDefinitionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code), errorDetails = importErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportCreatedMessage())).build() },
    )
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
    importPort.selectDataPath(userId, importJobId, dataPath).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDataPathSelectedMessage())).build() },
    )
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
        .data("info", info)
        .data("importJobId", importJobId)
        .data("statusLabel", statusLabel(view.importJob.status))
        .data("isReady", view.importJob.status == ImportStatus.READY)
        .data("targetEntityName", view.targetEntityDefinition.name)
        .data("mappingName", view.importJob.mapping?.name ?: view.targetEntityDefinition.name)
        .data("propertyRows", buildPropertyRows(view.targetEntityDefinition, view.importJob.mapping, view))
        .data("schemaFieldOptions", view.importJob.detectedSchema.map { SchemaFieldOptionRow(it.path, schemaFieldLabel(it)) })
        .data("conversionOptions", FieldMappingConversion.entries.map { ConversionOptionRow(it.name, conversionLabel(it)) }),
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
    if (request.name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportBlankMappingNameError())).build()
    }
    val fieldMappings = request.fieldMappings.mapNotNull { field ->
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
        fallbackValue = fallbackValue,
        referenceLookup = referenceLookup,
      )
    }

    importPort.updateMapping(userId, importJobId, request.name, fieldMappings).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { view ->
        val message = if (view.validation?.isReady == true) userMsg.userImportMappingStatusReadyMessage() else userMsg.userImportMappingStatusIncompleteMessage()
        Response.ok(DeveloperApiResult(true, message)).build()
      },
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
        .data("info", info)
        .data("importJobId", importJobId)
        .data("totalCount", report.totalCount)
        .data("validCount", report.validCount)
        .data("invalidCount", report.invalidCount)
        .data("invalidObjects", report.invalidObjects.map { it.toRow(view.targetEntityDefinition) }),
    ).build()
  }

  @POST
  @Path("/{importJobId}/dry-run/accept")
  @Produces(MediaType.APPLICATION_JSON)
  fun acceptDryRun(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("rest.user-import.dry-run-accept") {
    val userId = securityIdentity.principal.name
    importPort.acceptDryRun(userId, importJobId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { result ->
        val message = userMsg.userImportDryRunAcceptedMessage(result.savedCount, result.discardedCount)
        Response.ok(DeveloperApiResult(true, message, redirectUrl = "/ui/user/imports/${result.installedAppId.value}")).build()
      },
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

  private fun loadRows(userId: String, installedAppId: String, entityDefinitions: List<EntityDefinition>): List<ImportJobRow> {
    val entityNamesById = entityDefinitions.associate { it.id.value to it.name }
    return importPort.listImportJobs(userId, installedAppId).getOrNull().orEmpty().map { it.toRow(entityNamesById) }
  }

  private fun ImportJob.toRow(entityNamesById: Map<String, String>) = ImportJobRow(
    id = id.value,
    targetEntityName = entityNamesById[targetEntityDefinitionId.value].orEmpty(),
    statusLabel = statusLabel(status),
    awaitingDataPathSelection = status == ImportStatus.DOWNLOADED,
    mappable = status == ImportStatus.DATA_IDENTIFIED || status == ImportStatus.READY,
    readyForDryRun = status == ImportStatus.READY,
    detectedDataPaths = detectedDataPaths.map { it.toRow() },
    selectedDataPath = selectedDataPath,
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun DataPath.toRow() = DataPathRow(
    path = path,
    size = size,
  )

  private fun DryRunObject.toRow(entityDefinition: EntityDefinition): DryRunObjectRow {
    val issuesByProperty = issues.groupBy { it.targetPropertyId }
    val properties = entityDefinition.properties.map { property ->
      val propertyIssues = issuesByProperty[property.id].orEmpty()
      DryRunPropertyRow(
        name = property.name,
        typeLabel = PropertyLabelTemplateExtensions.propertyTypeLabel(property.type),
        value = targetData[property.id].orEmpty(),
        hasIssue = propertyIssues.isNotEmpty(),
        issues = propertyIssues.map { DryRunIssueRow(dryRunIssueMessage(it), it.staticallyChecked) },
      )
    }
    return DryRunObjectRow(index + 1, prettyPrintedSourceData(sourceDataJson), properties)
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
  }

  private fun formatNumber(value: Double): String = if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

  private fun schemaFieldLabel(property: SchemaProperty): String {
    val dominantType = property.typeCounts.filterKeys { it != SchemaPropertyType.NULL }.maxByOrNull { it.value }?.key
    val typeLabel = dominantType?.let { schemaTypeLabel(it) }
    return if (typeLabel != null) "${property.path} ($typeLabel)" else property.path
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
  }

  private fun statusLabel(status: ImportStatus): String = when (status) {
    ImportStatus.DOWNLOADED -> userMsg.userImportStatusDownloaded()
    ImportStatus.DATA_IDENTIFIED -> userMsg.userImportStatusDataIdentified()
    ImportStatus.READY -> userMsg.userImportStatusReady()
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
    ImportError.BLANK_MAPPING_NAME.code -> userMsg.userImportBlankMappingNameError()
    ImportError.ENTITY_DEFINITION_NOT_FOUND.code -> userMsg.userImportEntityDefinitionNotFoundError()
    ImportError.MAPPING_PROPERTY_NOT_FOUND.code -> userMsg.userImportMappingPropertyNotFoundError()
    ImportError.IMPORT_JOB_NOT_READY.code -> userMsg.userImportJobNotReadyError()
    ImportError.CONNECTION_NOT_FOUND.code -> userMsg.userImportConnectionNotFoundError()
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
