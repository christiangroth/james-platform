package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserImportDefinitionMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.SchedulePreset
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
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

/** One past, [ImportStatus.ACCEPTED] run of an [de.chrgroth.james.platform.domain.model.imports.ImportDefinition], for its read-only "Historie" modal (issue #676). */
data class ImportHistoryRowRow(
  val date: Instant,
  val statusLabel: String,
  val added: Int?,
  val replaced: Int?,
  val discarded: Int?,
)

@Path("/ui/user/imports/definitions")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportDefinitionResource {

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var importPort: ImportPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @Inject
  private lateinit var userImportDefinitionMsg: UserImportDefinitionMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  /**
   * Read-only history fragment for a definition's "Historie" modal (issue #676): every [ImportStatus.ACCEPTED] job
   * of [definitionId], newest first. Ownership is checked against [ImportPort.listAllImportDefinitions] (itself
   * already scoped to [userId]) rather than trusting the path parameter, so a definition id belonging to another
   * user never leaks its history - mirrors the ownership pattern used by [run]/[schedule]/[delete].
   */
  @GET
  @Path("/{definitionId}/history")
  @Produces(MediaType.TEXT_HTML)
  fun history(@PathParam("definitionId") definitionId: String): Any = httpResponseMetrics.timed("fragment.user-import-definition.history") {
    val userId = securityIdentity.principal.name
    val owned = importPort.listAllImportDefinitions(userId).any { it.id.value == definitionId }
    if (!owned) {
      return@timed UserTemplates.`import-history`(emptyList())
    }
    val rows = importPort.listAllImportJobs(userId)
      .filter { it.importDefinitionId.value == definitionId && it.status == ImportStatus.ACCEPTED }
      .sortedByDescending { it.lastChangedAt }
      .map { ImportHistoryRowRow(date = it.lastChangedAt, statusLabel = userImportDefinitionMsg.userImportStatusAccepted(), added = it.addedCount, replaced = it.replacedCount, discarded = it.discardedCount) }
    UserTemplates.`import-history`(rows)
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

  /**
   * Starts a fresh interactive [de.chrgroth.james.platform.domain.model.imports.ImportJob] for [definitionId] -
   * unlike [run], works regardless of whether the definition is fully configured yet, so a definition whose every
   * job got deleted before the initial data-path/filter/mapping wizard was ever finished (previously a dead end,
   * only recoverable by deleting the definition and starting over) can be reused (issue #679). The redirect lands on
   * [UserImportResource.importJob], which itself always opens the furthest step the new job has reached.
   */
  @POST
  @Path("/{definitionId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  fun start(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("rest.user-import-definition.start") {
    val userId = securityIdentity.principal.name
    importPort.startImportJob(userId, definitionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, definitionErrorMessage(error.code))).build() },
      ifRight = { job ->
        Response.ok(DeveloperApiResult(true, userImportDefinitionMsg.userImportDefinitionJobStartedMessage(), redirectUrl = "/ui/user/imports/${job.id.value}")).build()
      },
    )
  }

  @POST
  @Path("/{definitionId}/schedule")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun schedule(
    @PathParam("definitionId") definitionId: String,
    @FormParam("mode") mode: String?,
    @FormParam("time") time: String?,
    @FormParam("intervalMinutes") intervalMinutes: String?,
    @FormParam("notifyOnSlack") notifyOnSlack: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import-definition.schedule") {
    val userId = securityIdentity.principal.name
    // No free cron input: the structured form values are converted to the stored cron expression here, which then
    // still goes through ImportPort.updateSchedule's CronSchedule validation.
    val cron: String? = when (mode) {
      "NONE" -> null
      "DAILY" -> SchedulePreset.daily(time)?.toCron() ?: return@timed invalidScheduleResponse()
      "INTERVAL" -> SchedulePreset.interval(intervalMinutes?.trim()?.toIntOrNull())?.toCron() ?: return@timed invalidScheduleResponse()
      else -> return@timed invalidScheduleResponse()
    }
    importPort.updateSchedule(userId, definitionId, cron, !notifyOnSlack.isNullOrBlank()).fold(
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

  private fun invalidScheduleResponse(): Response =
    Response.ok(DeveloperApiResult(false, userImportDefinitionMsg.userImportInvalidCronScheduleError())).build()

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
