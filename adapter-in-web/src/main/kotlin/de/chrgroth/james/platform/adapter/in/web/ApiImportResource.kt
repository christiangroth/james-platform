package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.MappingView
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionPort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

private const val DEFAULT_DRY_RUN_LIMIT = 20
private const val MAX_DRY_RUN_LIMIT = 500

/**
 * The token owner's import connections. Scope `USER_IMPORT`. A connection is not tied to an App, so the token's App restriction does not apply
 * here; the stored bearer token is write-only.
 */
@Path("/api/v1/user/import-connections")
@ApplicationScoped
@Suppress("Unused")
class ApiImportConnectionResource(
  private val importConnections: ImportConnectionPort,
  private val mapper: ApiImportDtoMapper,
  private val apiRequest: ApiRequestContext,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun list(): Response = httpResponseMetrics.timed("api.user.import-connections-list") {
    importConnections.listConnections(apiRequest.username()).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { list -> ApiResponses.ok(list.sortedBy { it.name.lowercase() }.map { mapper.connection(it) }) },
    )
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun create(request: ApiImportConnectionRequest?): Response = httpResponseMetrics.timed("api.user.import-connection-create") {
    importConnections.createConnection(
      apiRequest.username(),
      request?.name?.trim().orEmpty(),
      request?.baseUrl?.trim().orEmpty(),
      request?.bearerToken?.takeIf { it.isNotBlank() },
    ).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { ApiResponses.created(mapper.connection(it)) },
    )
  }

  /** Replaces name and base URL; the bearer token is only replaced when given, or removed with `clearBearerToken`. */
  @PUT
  @Path("/{connectionId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun update(@PathParam("connectionId") connectionId: String, request: ApiImportConnectionRequest?): Response =
    httpResponseMetrics.timed("api.user.import-connection-update") {
      importConnections.updateConnection(
        apiRequest.username(),
        connectionId,
        request?.name?.trim().orEmpty(),
        request?.baseUrl?.trim().orEmpty(),
        request?.bearerToken?.takeIf { it.isNotBlank() },
        clearBearerToken = request?.clearBearerToken ?: false,
      ).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.ok(mapper.connection(it)) },
      )
    }

  @DELETE
  @Path("/{connectionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun delete(@PathParam("connectionId") connectionId: String): Response = httpResponseMetrics.timed("api.user.import-connection-delete") {
    importConnections.deleteConnection(apiRequest.username(), connectionId).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { ApiResponses.noContent() },
    )
  }

  /** Performs a real request against the connection's base URL with its stored credentials. */
  @POST
  @Path("/{connectionId}/test")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun test(@PathParam("connectionId") connectionId: String): Response = httpResponseMetrics.timed("api.user.import-connection-test") {
    importConnections.testConnection(apiRequest.username(), connectionId).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { ApiResponses.noContent() },
    )
  }
}

/**
 * Imports into the token owner's installations, the same pipeline as the UI: start a job (fetches the payload), select the data path, filter,
 * map, dry-run, accept. Scope `USER_IMPORT` for everything up to the dry-run, so a token can check source data against an App without being
 * able to change any data; accepting a job and running a configured definition (which accepts unattended) additionally require `USER_WRITE`.
 * Jobs and definitions targeting installations outside the token's App restriction (or test installations) behave as if they did not exist.
 */
@Path("/api/v1/user/imports")
@ApplicationScoped
@Suppress("Unused", "TooManyFunctions")
class ApiImportResource(
  private val imports: ImportPort,
  private val userAppStore: UserAppStorePort,
  private val mapper: ApiImportDtoMapper,
  private val apiRequest: ApiRequestContext,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  // region definitions

  @GET
  @Path("/definitions")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun listDefinitions(): Response = httpResponseMetrics.timed("api.user.import-definitions-list") {
    val installations = coveredInstallations()
    ApiResponses.ok(
      imports.listAllImportDefinitions(apiRequest.username())
        .filter { covers(installations, it) }
        .sortedByDescending { it.lastChangedAt }
        .map { mapper.definition(it) },
    )
  }

  @DELETE
  @Path("/definitions/{definitionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun deleteDefinition(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("api.user.import-definition-delete") {
    withDefinition(definitionId) {
      imports.deleteImportDefinition(apiRequest.username(), definitionId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.noContent() },
      )
    }
  }

  /** Starts a new interactive job for an existing definition (fetches the payload again, reusing connection, URL postfix and target entity). */
  @POST
  @Path("/definitions/{definitionId}/jobs")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun startDefinitionJob(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("api.user.import-definition-start") {
    withDefinition(definitionId) {
      imports.startImportJob(apiRequest.username(), definitionId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { job -> jobResponse(job.id.value, created = true) },
      )
    }
  }

  /** Runs a fully configured definition unattended up to the accept, like the UI's "Run". Requires `USER_IMPORT` and `USER_WRITE`. */
  @POST
  @Path("/definitions/{definitionId}/run")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun runDefinition(@PathParam("definitionId") definitionId: String): Response = httpResponseMetrics.timed("api.user.import-definition-run") {
    requireWrite()?.let { return@timed it }
    withDefinition(definitionId) {
      imports.triggerDefinitionRun(apiRequest.username(), definitionId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { Response.status(Response.Status.ACCEPTED).entity(mapper.jobSummary(it)).type(MediaType.APPLICATION_JSON).build() },
      )
    }
  }

  // endregion

  // region jobs

  @GET
  @Path("/jobs")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun listJobs(): Response = httpResponseMetrics.timed("api.user.import-jobs-list") {
    val installedAppIds = coveredInstallations().map { it.installedAppId }.toSet()
    ApiResponses.ok(
      imports.listAllImportJobs(apiRequest.username())
        .filter { it.installedAppId.value in installedAppIds }
        .sortedByDescending { it.createdAt }
        .map { mapper.jobSummary(it) },
    )
  }

  /** Checks that the connection (plus optional URL postfix) answers with a JSON object, without creating anything. */
  @POST
  @Path("/test-url")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun testUrl(request: ApiImportTestUrlRequest?): Response = httpResponseMetrics.timed("api.user.import-test-url") {
    if (request == null) return@timed ApiResponses.badRequest("request body is required")
    val connectionId = request.connectionId?.takeIf { it.isNotBlank() } ?: return@timed ApiResponses.badRequest("connectionId is required")
    imports.testConnectionUrl(apiRequest.username(), connectionId, request.urlPostfix?.trim()?.takeIf { it.isNotEmpty() }).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { ApiResponses.noContent() },
    )
  }

  /** Fetches from the connection and creates a new definition plus job targeting the entity in the installation. */
  @POST
  @Path("/jobs")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun startJob(request: ApiImportStartRequest?): Response = httpResponseMetrics.timed("api.user.import-job-start") {
    if (request == null) return@timed ApiResponses.badRequest("request body is required")
    val installedAppId = request.installedAppId?.takeIf { it.isNotBlank() } ?: return@timed ApiResponses.badRequest("installedAppId is required")
    val connectionId = request.connectionId?.takeIf { it.isNotBlank() } ?: return@timed ApiResponses.badRequest("connectionId is required")
    val targetEntityDefinitionId = request.targetEntityDefinitionId?.takeIf { it.isNotBlank() }
      ?: return@timed ApiResponses.badRequest("targetEntityDefinitionId is required")
    if (coveredInstallations().none { it.installedAppId == installedAppId }) {
      return@timed ApiResponses.notFound("Installed app $installedAppId not found")
    }
    imports.triggerImport(
      apiRequest.username(),
      installedAppId,
      connectionId,
      targetEntityDefinitionId,
      request.urlPostfix?.trim()?.takeIf { it.isNotEmpty() },
    ).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { job -> jobResponse(job.id.value, created = true) },
    )
  }

  @GET
  @Path("/jobs/{importJobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun getJob(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("api.user.import-job-get") {
    jobResponse(importJobId, created = false)
  }

  @DELETE
  @Path("/jobs/{importJobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun deleteJob(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("api.user.import-job-delete") {
    withJob(importJobId) {
      imports.deleteImportJob(apiRequest.username(), importJobId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.noContent() },
      )
    }
  }

  /** Selects where the records live in the payload, as a dot path (`/` separators and a leading `/` are accepted as well). */
  @PUT
  @Path("/jobs/{importJobId}/data-path")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun selectDataPath(@PathParam("importJobId") importJobId: String, request: ApiDataPathRequest?): Response =
    httpResponseMetrics.timed("api.user.import-job-data-path") {
      withJob(importJobId) { view ->
        val dataPath = request?.dataPath.orEmpty().trim().removePrefix("/").replace("/", ".")
        // a job whose payload has a single data path gets it selected automatically; confirming that path again is not an error
        if (view.importJob.status != ImportStatus.DOWNLOADED && dataPath.isNotEmpty() && view.importDefinition.selectedDataPath == dataPath) {
          return@withJob ApiResponses.ok(mapper.job(view.importJob, view.importDefinition))
        }
        imports.selectDataPath(apiRequest.username(), importJobId, dataPath).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { jobResponse(importJobId, created = false) },
        )
      }
    }

  // endregion

  // region filter

  @GET
  @Path("/jobs/{importJobId}/filter")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun getFilter(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("api.user.import-job-filter-get") {
    withJob(importJobId) {
      imports.getFilterView(apiRequest.username(), importJobId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.ok(mapper.filter(it)) },
      )
    }
  }

  /** Replaces the filter rules (applied in order, see the UI's filter step); answers with the new record counts. */
  @PUT
  @Path("/jobs/{importJobId}/filter")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun updateFilter(@PathParam("importJobId") importJobId: String, request: ApiFilterRulesRequest?): Response =
    httpResponseMetrics.timed("api.user.import-job-filter-update") {
      withJob(importJobId) {
        val rules = ApiImportDtoMapper.parseFilterRules(request?.rules.orEmpty()).getOrElse { return@withJob ApiResponses.badRequest(it.message.orEmpty()) }
        imports.updateFilter(apiRequest.username(), importJobId, rules).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.ok(mapper.filter(it)) },
        )
      }
    }

  /** Distinct values found at [sourcePath] in the job's records. */
  @GET
  @Path("/jobs/{importJobId}/filter/values")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun filterValues(@PathParam("importJobId") importJobId: String, @QueryParam("sourcePath") sourcePath: String?): Response =
    httpResponseMetrics.timed("api.user.import-job-filter-values") {
      if (sourcePath.isNullOrBlank()) return@timed ApiResponses.badRequest("sourcePath is required")
      withJob(importJobId) {
        imports.resolveFilterFieldValues(apiRequest.username(), importJobId, sourcePath).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.ok(it) },
        )
      }
    }

  /** One source record at [index] among the records the filter keeps (`matched=true`, default) or removes (`matched=false`). */
  @GET
  @Path("/jobs/{importJobId}/records")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun record(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("matched") matched: Boolean?,
    @QueryParam("index") index: Int?,
  ): Response = httpResponseMetrics.timed("api.user.import-job-record") {
    val position = index?.coerceAtLeast(0) ?: 0
    withJob(importJobId) {
      imports.resolveFilterSample(apiRequest.username(), importJobId, matched ?: true, position).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.ok(mapper.record(it.total, position, it.sourceDataJson)) },
      )
    }
  }

  // endregion

  // region mapping and dry-run

  @GET
  @Path("/jobs/{importJobId}/mapping")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun getMapping(@PathParam("importJobId") importJobId: String): Response = httpResponseMetrics.timed("api.user.import-job-mapping-get") {
    withJob(importJobId) { view -> ApiResponses.ok(mapper.mapping(view)) }
  }

  /** Replaces the field mappings; answers with the validation against the target entity and the detected (filtered) schema. */
  @PUT
  @Path("/jobs/{importJobId}/mapping")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun updateMapping(@PathParam("importJobId") importJobId: String, request: ApiFieldMappingsRequest?): Response =
    httpResponseMetrics.timed("api.user.import-job-mapping-update") {
      withJob(importJobId) {
        val mappings = ApiImportDtoMapper.parseFieldMappings(request?.fieldMappings.orEmpty())
          .getOrElse { return@withJob ApiResponses.badRequest(it.message.orEmpty()) }
        imports.updateMapping(apiRequest.username(), importJobId, mappings).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.ok(mapper.mapping(it)) },
        )
      }
    }

  /** Previews what the given (not saved) field mappings produce for the record at [index]. */
  @POST
  @Path("/jobs/{importJobId}/mapping/sample")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun mappingSample(
    @PathParam("importJobId") importJobId: String,
    @QueryParam("index") index: Int?,
    request: ApiFieldMappingsRequest?,
  ): Response = httpResponseMetrics.timed("api.user.import-job-mapping-sample") {
    val position = index?.coerceAtLeast(0) ?: 0
    withJob(importJobId) {
      val mappings = ApiImportDtoMapper.parseFieldMappings(request?.fieldMappings.orEmpty())
        .getOrElse { return@withJob ApiResponses.badRequest(it.message.orEmpty()) }
      imports.resolveMappingSample(apiRequest.username(), importJobId, position, mappings).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { sample -> ApiResponses.ok(ApiMappingSampleDto(sample.total, position, sample.dryRunObject?.let { mapper.dryRunObject(it) })) },
      )
    }
  }

  /**
   * Builds every target object without saving anything and validates it against the target entity's constraints. Counts are complete, the
   * object lists are capped at [limit] (default 20, at most 500) each.
   */
  @GET
  @Path("/jobs/{importJobId}/dry-run")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun dryRun(@PathParam("importJobId") importJobId: String, @QueryParam("limit") limit: Int?): Response =
    httpResponseMetrics.timed("api.user.import-job-dry-run") {
      withJob(importJobId) {
        imports.dryRun(apiRequest.username(), importJobId).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { ApiResponses.ok(mapper.dryRun(it, (limit ?: DEFAULT_DRY_RUN_LIMIT).coerceIn(0, MAX_DRY_RUN_LIMIT))) },
        )
      }
    }

  /**
   * Accepts the dry-run in the background: valid objects are saved, invalid ones discarded. `mode=REPLACE` deletes all existing objects of the
   * target entity first, default is `ADD`. Requires `USER_IMPORT` and `USER_WRITE`; poll the job until its status is `ACCEPTED`.
   */
  @POST
  @Path("/jobs/{importJobId}/accept")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_IMPORT)
  fun accept(@PathParam("importJobId") importJobId: String, @QueryParam("mode") mode: String?): Response =
    httpResponseMetrics.timed("api.user.import-job-accept") {
      requireWrite()?.let { return@timed it }
      val replaceExisting = when (mode?.uppercase()) {
        null, "", "ADD" -> false
        "REPLACE" -> true
        else -> return@timed ApiResponses.badRequest("mode must be ADD or REPLACE")
      }
      withJob(importJobId) {
        imports.acceptDryRun(apiRequest.username(), importJobId, replaceExisting).fold(
          ifLeft = { ApiResponses.domainError(it) },
          ifRight = { Response.status(Response.Status.ACCEPTED).entity(mapper.jobSummary(it)).type(MediaType.APPLICATION_JSON).build() },
        )
      }
    }

  // endregion

  private fun requireWrite(): Response? =
    if (apiRequest.hasRole(ApiTokenRoles.USER_WRITE)) null else ApiResponses.forbidden("Writing data requires the USER_WRITE scope in addition to USER_IMPORT")

  private fun coveredInstallations(): List<InstalledAppInfo> =
    userAppStore.getInstalledApps(apiRequest.username()).filter { !it.installedApp.isTest && apiRequest.coversApp(it.installedApp.appId.value) }

  /** A definition has no installation of its own, it belongs to the installation whose App defines its target entity. */
  private fun covers(installations: List<InstalledAppInfo>, definition: ImportDefinition): Boolean =
    installations.any { info -> info.installedVersion.entityDefinitions.any { it.id == definition.targetEntityDefinitionId } }

  private fun withDefinition(definitionId: String, block: (ImportDefinition) -> Response): Response {
    val definition = imports.listAllImportDefinitions(apiRequest.username()).firstOrNull { it.id.value == definitionId }
    return if (definition == null || !covers(coveredInstallations(), definition)) {
      ApiResponses.notFound("Import definition $definitionId not found")
    } else {
      block(definition)
    }
  }

  private fun withJob(importJobId: String, block: (MappingView) -> Response): Response =
    imports.getMappingView(apiRequest.username(), importJobId).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { view ->
        if (coveredInstallations().none { it.installedAppId == view.importJob.installedAppId.value }) {
          ApiResponses.notFound("Import job $importJobId not found")
        } else {
          block(view)
        }
      },
    )

  private fun jobResponse(importJobId: String, created: Boolean): Response = withJob(importJobId) { view ->
    val dto = mapper.job(view.importJob, view.importDefinition)
    if (created) ApiResponses.created(dto) else ApiResponses.ok(dto)
  }
}
