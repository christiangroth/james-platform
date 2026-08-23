package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserImportDefinitionMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
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
import java.time.Instant

data class ImportDefinitionRow(
  val id: String,
  val connectionName: String,
  val urlPostfix: String,
  val installedAppName: String,
  val targetEntityName: String,
  val configured: Boolean,
  val schedule: String,
  val hasSchedule: Boolean,
  val notifyOnSlack: Boolean,
  val lastRunAt: Instant?,
  val nextRunAt: Instant?,
)

@Path("/ui/user/imports/definitions")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportDefinitionResource {

  @Inject
  @Location("ui/user/import-definitions.html")
  private lateinit var definitionsTemplate: Template

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
  private lateinit var userImportDefinitionMsg: UserImportDefinitionMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun definitions(): Response = httpResponseMetrics.timed("page.user-import-definition.list") {
    val userId = securityIdentity.principal.name
    Response.ok(definitionsTemplate.data("definitions", loadRows(userId))).build()
  }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun definitionsTable(): Any = httpResponseMetrics.timed("fragment.user-import-definition.table") {
    val userId = securityIdentity.principal.name
    definitionsTemplate.getFragment("definitions_table")
      .data("definitions", loadRows(userId))
  }

  @POST
  @Path("/{definitionId}/run")
  @Produces(MediaType.APPLICATION_JSON)
  fun run(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("rest.user-import-definition.run") {
    val userId = securityIdentity.principal.name
    importPort.triggerDefinitionRun(userId, definitionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, definitionErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userImportDefinitionMsg.userImportDefinitionRunQueuedMessage())).build() },
    )
  }

  @POST
  @Path("/{definitionId}/schedule")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun schedule(
    @PathParam("definitionId") definitionId: String,
    @FormParam("schedule") schedule: String?,
    @FormParam("notifyOnSlack") notifyOnSlack: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import-definition.schedule") {
    val userId = securityIdentity.principal.name
    importPort.updateSchedule(userId, definitionId, schedule, !notifyOnSlack.isNullOrBlank()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, definitionErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userImportDefinitionMsg.userImportDefinitionScheduleSavedMessage())).build() },
    )
  }

  @POST
  @Path("/{definitionId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun delete(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("rest.user-import-definition.delete") {
    val userId = securityIdentity.principal.name
    importPort.deleteImportDefinition(userId, definitionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, definitionErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userImportDefinitionMsg.userImportDefinitionDeletedMessage())).build() },
    )
  }

  private fun loadRows(userId: String): List<ImportDefinitionRow> {
    val apps = userAppStore.getInstalledApps(userId)
    val appNamesById = apps.associate { it.installedAppId to it.appName }
    val entityNamesById = apps.flatMap { it.installedVersion.entityDefinitions }.associate { it.id.value to it.name }
    val entityToAppId = apps.flatMap { app -> app.installedVersion.entityDefinitions.map { it.id.value to app.installedAppId } }.toMap()
    val connectionNamesById = importConnectionPort.listConnections(userId).getOrNull().orEmpty().associate { it.id.value to it.name }
    val now = Instant.now()
    return importPort.listAllImportDefinitions(userId).map { it.toRow(connectionNamesById, entityNamesById, entityToAppId, appNamesById, now) }
  }

  private fun ImportDefinition.toRow(
    connectionNamesById: Map<String, String>,
    entityNamesById: Map<String, String>,
    entityToAppId: Map<String, String>,
    appNamesById: Map<String, String>,
    now: Instant,
  ): ImportDefinitionRow {
    val installedAppId = entityToAppId[targetEntityDefinitionId.value]
    return ImportDefinitionRow(
      id = id.value,
      connectionName = connectionNamesById[connectionId.value].orEmpty(),
      urlPostfix = urlPostfix.orEmpty(),
      installedAppName = installedAppId?.let { appNamesById[it] }.orEmpty(),
      targetEntityName = entityNamesById[targetEntityDefinitionId.value].orEmpty(),
      configured = selectedDataPath != null && mapping != null,
      schedule = schedule.orEmpty(),
      hasSchedule = schedule != null,
      notifyOnSlack = notifyOnSlack,
      lastRunAt = lastRunAt,
      nextRunAt = schedule?.let { importPort.nextScheduledRunAt(it, lastRunAt ?: createdAt) }?.takeIf { it.isAfter(now) },
    )
  }

  private fun definitionErrorMessage(code: String): String = when (code) {
    ImportError.DEFINITION_NOT_FOUND.code -> userImportDefinitionMsg.userImportDefinitionNotFoundError()
    ImportError.INVALID_CRON_SCHEDULE.code -> userImportDefinitionMsg.userImportInvalidCronScheduleError()
    ImportError.DEFINITION_NOT_CONFIGURED.code -> userImportDefinitionMsg.userImportDefinitionNotConfiguredError()
    ImportError.SCHEMA_DRIFT_DETECTED.code -> userImportDefinitionMsg.userImportSchemaDriftDetectedError()
    ImportError.INSTALLED_APP_NOT_FOUND.code -> userMsg.userInstalledAppNotFoundError()
    ImportError.ENTITY_DEFINITION_NOT_FOUND.code -> userMsg.userImportEntityDefinitionNotFoundError()
    ImportError.CONNECTION_NOT_FOUND.code -> userMsg.userImportConnectionNotFoundError()
    ImportError.INVALID_JSON_RESPONSE.code -> userMsg.userImportInvalidJsonError()
    ImportError.NOT_A_JSON_OBJECT.code -> userMsg.userImportNotJsonObjectError()
    ImportError.INVALID_DATA_PATH.code -> userMsg.userImportInvalidDataPathError()
    ImportError.IMPORT_JOB_NOT_READY.code -> userMsg.userImportJobNotReadyError()
    ImportError.FETCH_FAILED.code -> userMsg.userImportFetchFailedError()
    ImportError.INVALID_URL.code -> userMsg.userImportInvalidUrlError()
    ImportError.RESPONSE_TOO_LARGE.code -> userMsg.userImportResponseTooLargeError()
    else -> msg.commonUnexpectedError()
  }
}
