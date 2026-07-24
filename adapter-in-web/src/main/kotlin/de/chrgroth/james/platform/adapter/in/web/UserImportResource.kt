package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.DryRunObject
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FieldMappingConversion
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingType
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
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
  val size: Int,
)

data class ImportDocumentRow(
  val id: String,
  val installedAppId: String,
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
  val selected: Boolean,
)

data class SchemaFieldOptionRow(
  val path: String,
  val label: String,
)

data class ConversionOptionRow(
  val value: String,
  val label: String,
)

data class MappingPropertyRow(
  val id: String,
  val name: String,
  val typeLabel: String,
  val mandatory: Boolean,
  val constraintHint: String,
  val hasPattern: Boolean,
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

data class FieldMappingRequest @JsonCreator constructor(
  @param:JsonProperty("targetPropertyId") val targetPropertyId: String,
  @param:JsonProperty("sourcePath") val sourcePath: String?,
  @param:JsonProperty("conversion") val conversion: String?,
  @param:JsonProperty("fallbackValue") val fallbackValue: String?,
)

data class MappingSaveRequest @JsonCreator constructor(
  @param:JsonProperty("name") val name: String,
  @param:JsonProperty("type") val type: String,
  @param:JsonProperty("targetEntityDefinitionId") val targetEntityDefinitionId: String,
  @param:JsonProperty("fieldMappings") val fieldMappings: List<FieldMappingRequest>,
)

@Path("/ui/user/apps/{installedAppId}/imports")
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
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun imports(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    return Response.ok(
      importsTemplate
        .data("info", info)
        .data("documents", loadRows(userId, installedAppId)),
    ).build()
  }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun importsTable(@PathParam("installedAppId") installedAppId: String): Any {
    val userId = securityIdentity.principal.name
    return importsTemplate.getFragment("imports_table")
      .data("documents", loadRows(userId, installedAppId))
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerImport(
    @PathParam("installedAppId") installedAppId: String,
    @FormParam("sourceUrl") sourceUrl: String?,
    @FormParam("bearerToken") bearerToken: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    if (sourceUrl.isNullOrBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportUrlRequiredError())).build()
    }
    if (bearerToken.isNullOrBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportTokenRequiredError())).build()
    }
    return importPort.triggerImport(userId, installedAppId, sourceUrl, bearerToken).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportCreatedMessage())).build() },
    )
  }

  @POST
  @Path("/{importDocumentId}/select-path")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun selectDataPath(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
    @FormParam("dataPath") dataPath: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    if (dataPath.isNullOrBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportBlankDataPathError())).build()
    }
    return importPort.selectDataPath(userId, installedAppId, importDocumentId, dataPath).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDataPathSelectedMessage())).build() },
    )
  }

  @GET
  @Path("/{importDocumentId}/mapping")
  @Produces(MediaType.TEXT_HTML)
  fun mapping(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
    @QueryParam("entityDefinitionId") entityDefinitionIdParam: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val view = importPort.getMappingView(userId, installedAppId, importDocumentId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/apps/$installedAppId/imports")).build() },
      ifRight = { it },
    )

    val existingMapping = view.importDocument.mapping
    val selectedEntityId = entityDefinitionIdParam?.takeIf { it.isNotBlank() } ?: existingMapping?.targetEntityDefinitionId?.value
    val selectedEntity = view.entityDefinitions.find { it.id.value == selectedEntityId }
    val currentMapping = existingMapping?.takeIf { selectedEntity != null && it.targetEntityDefinitionId == selectedEntity.id }

    return Response.ok(
      mappingTemplate
        .data("info", info)
        .data("importDocumentId", importDocumentId)
        .data("statusLabel", statusLabel(view.importDocument.status))
        .data("isReady", view.importDocument.status == ImportStatus.READY)
        .data("hasEntitySelected", selectedEntity != null)
        .data("entityOptions", view.entityDefinitions.map { EntityOptionRow(it.id.value, it.name, it.id.value == selectedEntity?.id?.value) })
        .data("mappingName", currentMapping?.name ?: selectedEntity?.name.orEmpty())
        .data("mappingType", (currentMapping?.type ?: MappingType.FIND).name)
        .data("mappingTypes", listOf(MappingType.FIND.name, MappingType.FIND_OR_CREATE.name))
        .data("propertyRows", selectedEntity?.let { buildPropertyRows(it, currentMapping, view) }.orEmpty())
        .data("schemaFieldOptions", view.importDocument.detectedSchema.map { SchemaFieldOptionRow(it.path, schemaFieldLabel(it)) })
        .data("conversionOptions", FieldMappingConversion.entries.map { ConversionOptionRow(it.name, conversionLabel(it)) }),
    ).build()
  }

  @POST
  @Path("/{importDocumentId}/mapping")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun saveMapping(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
    request: MappingSaveRequest,
  ): Response {
    val userId = securityIdentity.principal.name
    if (request.name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportBlankMappingNameError())).build()
    }
    val type = runCatching { MappingType.valueOf(request.type) }.getOrElse {
      return Response.ok(DeveloperApiResult(false, msg.commonUnexpectedError())).build()
    }
    val fieldMappings = request.fieldMappings.mapNotNull { field ->
      if (field.targetPropertyId.isBlank()) return@mapNotNull null
      val sourcePath = field.sourcePath?.takeIf { it.isNotBlank() }
      val fallbackValue = field.fallbackValue?.takeIf { it.isNotBlank() }
      if (sourcePath == null && fallbackValue == null) return@mapNotNull null
      FieldMapping(
        targetPropertyId = PropertyId(field.targetPropertyId),
        sourcePath = sourcePath,
        conversion = runCatching { FieldMappingConversion.valueOf(field.conversion ?: FieldMappingConversion.NONE.name) }.getOrDefault(FieldMappingConversion.NONE),
        fallbackValue = fallbackValue,
      )
    }

    return importPort.updateMapping(userId, installedAppId, importDocumentId, request.name, type, request.targetEntityDefinitionId, fieldMappings).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { view ->
        val message = if (view.validation?.isReady == true) userMsg.userImportMappingStatusReadyMessage() else userMsg.userImportMappingStatusIncompleteMessage()
        Response.ok(DeveloperApiResult(true, message)).build()
      },
    )
  }

  @GET
  @Path("/{importDocumentId}/dry-run")
  @Produces(MediaType.TEXT_HTML)
  fun dryRun(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val view = importPort.getMappingView(userId, installedAppId, importDocumentId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/apps/$installedAppId/imports")).build() },
      ifRight = { it },
    )
    val entityDefinition = view.importDocument.mapping?.let { mapping -> view.entityDefinitions.find { it.id == mapping.targetEntityDefinitionId } }
    val report = importPort.dryRun(userId, installedAppId, importDocumentId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/apps/$installedAppId/imports/$importDocumentId/mapping")).build() },
      ifRight = { it },
    )

    return Response.ok(
      dryRunTemplate
        .data("info", info)
        .data("importDocumentId", importDocumentId)
        .data("totalCount", report.totalCount)
        .data("validCount", report.validCount)
        .data("invalidCount", report.invalidCount)
        .data("invalidObjects", entityDefinition?.let { entity -> report.invalidObjects.map { it.toRow(entity) } }.orEmpty()),
    ).build()
  }

  @POST
  @Path("/{importDocumentId}/dry-run/accept")
  @Produces(MediaType.APPLICATION_JSON)
  fun acceptDryRun(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    return importPort.acceptDryRun(userId, installedAppId, importDocumentId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { result ->
        val message = userMsg.userImportDryRunAcceptedMessage(result.savedCount, result.discardedCount)
        Response.ok(DeveloperApiResult(true, message, redirectUrl = "/ui/user/apps/$installedAppId/imports")).build()
      },
    )
  }

  @POST
  @Path("/{importDocumentId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteImport(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    return importPort.deleteImportDocument(userId, installedAppId, importDocumentId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDeletedMessage())).build() },
    )
  }

  private fun loadRows(userId: String, installedAppId: String): List<ImportDocumentRow> =
    importPort.listImportDocuments(userId, installedAppId).getOrNull().orEmpty().map { it.toRow() }

  private fun ImportDocument.toRow() = ImportDocumentRow(
    id = id.value,
    installedAppId = installedAppId.value,
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
    return entityDefinition.properties.map { property ->
      val fieldMapping = fieldMappingsByProperty[property.id]
      val issues = issuesByProperty[property.id].orEmpty()
      MappingPropertyRow(
        id = property.id.value,
        name = property.name,
        typeLabel = PropertyLabelTemplateExtensions.propertyTypeLabel(property.type),
        mandatory = !property.nullable,
        constraintHint = PropertyLabelTemplateExtensions.constraintHint(property),
        hasPattern = property.constraints.any { it is PropertyConstraint.Pattern },
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
    ImportError.BLANK_URL.code -> userMsg.userImportUrlRequiredError()
    ImportError.BLANK_BEARER_TOKEN.code -> userMsg.userImportTokenRequiredError()
    ImportError.INVALID_URL.code -> userMsg.userImportInvalidUrlError()
    ImportError.FETCH_FAILED.code -> userMsg.userImportFetchFailedError()
    ImportError.INVALID_JSON_RESPONSE.code -> userMsg.userImportInvalidJsonError()
    ImportError.NOT_A_JSON_OBJECT.code -> userMsg.userImportNotJsonObjectError()
    ImportError.RESPONSE_TOO_LARGE.code -> userMsg.userImportResponseTooLargeError()
    ImportError.IMPORT_DOCUMENT_NOT_FOUND.code -> userMsg.userImportDocumentNotFoundError()
    ImportError.IMPORT_DOCUMENT_NOT_DOWNLOADED.code -> userMsg.userImportDocumentNotDownloadedError()
    ImportError.BLANK_DATA_PATH.code -> userMsg.userImportBlankDataPathError()
    ImportError.INVALID_DATA_PATH.code -> userMsg.userImportInvalidDataPathError()
    ImportError.IMPORT_DOCUMENT_NOT_MAPPABLE.code -> userMsg.userImportDocumentNotMappableError()
    ImportError.BLANK_MAPPING_NAME.code -> userMsg.userImportBlankMappingNameError()
    ImportError.ENTITY_DEFINITION_NOT_FOUND.code -> userMsg.userImportEntityDefinitionNotFoundError()
    ImportError.MAPPING_PROPERTY_NOT_FOUND.code -> userMsg.userImportMappingPropertyNotFoundError()
    ImportError.IMPORT_DOCUMENT_NOT_READY.code -> userMsg.userImportDocumentNotReadyError()
    else -> msg.commonUnexpectedError()
  }

  companion object {
    private val objectMapper = ObjectMapper()
  }
}
