package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.user.ApiTokenScope
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import io.quarkus.security.identity.SecurityIdentity
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

/** Token info: who the token belongs to and which scopes are currently in effect. Reachable with any token scope. */
@Path("/api/v1/token")
@ApplicationScoped
@Suppress("Unused")
class ApiTokenInfoResource(
  private val identity: SecurityIdentity,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_READ, ApiTokenRoles.DEVELOPER_READ)
  fun info(): Response = httpResponseMetrics.timed("api.token.info") {
    val scopes = ApiTokenScope.entries.filter { identity.hasRole(ApiTokenRoles.of(it)) }.toSet()
    val appIds: Set<String> = identity.getAttribute(ApiTokenAuthMechanism.API_TOKEN_APP_IDS_ATTRIBUTE) ?: emptySet()
    val tokenId: String = identity.getAttribute(ApiTokenAuthMechanism.API_TOKEN_ID_ATTRIBUTE)
    ApiResponses.ok(ApiDtoMapper.tokenInfo(identity.principal.name, tokenId, scopes, appIds))
  }
}

/**
 * The token owner's installed Apps and their data. Scope `USER_READ` / `USER_WRITE` (write implies read, see
 * [de.chrgroth.james.platform.domain.model.user.ApiTokenScope]); installations of Apps outside the token's App restriction behave as if they did
 * not exist (404), so a restricted token cannot even discover them. Developer test installations are not listed.
 */
@Path("/api/v1/user/apps")
@ApplicationScoped
@Suppress("Unused", "TooManyFunctions")
class ApiUserResource(
  private val userAppStore: UserAppStorePort,
  private val appData: AppDataPort,
  private val apiRequest: ApiRequestContext,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_READ)
  fun listInstalledApps(): Response = httpResponseMetrics.timed("api.user.apps-list") {
    val apps = userAppStore.getInstalledApps(apiRequest.username())
      .filter { !it.installedApp.isTest && apiRequest.coversApp(it.installedApp.appId.value) }
      .map { ApiDtoMapper.installedApp(it) }
    ApiResponses.ok(apps)
  }

  @GET
  @Path("/{installedAppId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_READ)
  fun getInstalledApp(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("api.user.app-get") {
    withInstalledApp(installedAppId) { info ->
      ApiResponses.ok(ApiInstalledAppDetailDto(ApiDtoMapper.installedApp(info), ApiDtoMapper.version(info.installedVersion)))
    }
  }

  @GET
  @Path("/{installedAppId}/data")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_READ)
  fun listData(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityTypeId") entityTypeId: String?,
  ): Response = httpResponseMetrics.timed("api.user.data-list") {
    withInstalledApp(installedAppId) { info ->
      appData.listAppData(apiRequest.username(), installedAppId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { list ->
          ApiResponses.ok(
            list.filter { entityTypeId.isNullOrBlank() || it.entityType.value == entityTypeId }
              .map { ApiDtoMapper.appData(it, entityOf(info, it.entityType.value)) },
          )
        },
      )
    }
  }

  @GET
  @Path("/{installedAppId}/data/{dataId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_READ)
  fun getData(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response = httpResponseMetrics.timed("api.user.data-get") {
    withInstalledApp(installedAppId) { info ->
      appData.getAppData(apiRequest.username(), installedAppId, dataId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.ok(ApiDtoMapper.appData(it, entityOf(info, it.entityType.value))) },
      )
    }
  }

  @POST
  @Path("/{installedAppId}/data")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_WRITE)
  fun createData(
    @PathParam("installedAppId") installedAppId: String,
    request: ApiAppDataRequest?,
  ): Response = httpResponseMetrics.timed("api.user.data-create") {
    val entityTypeId = request?.entityTypeId?.takeIf { it.isNotBlank() } ?: return@timed ApiResponses.badRequest("entityTypeId is required")
    withInstalledApp(installedAppId) { info ->
      appData.createAppData(apiRequest.username(), installedAppId, entityTypeId, ApiFormValues.toFormData(request.values.orEmpty())).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.created(ApiDtoMapper.appData(it, entityOf(info, it.entityType.value))) },
      )
    }
  }

  @PUT
  @Path("/{installedAppId}/data/{dataId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_WRITE)
  fun updateData(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
    request: ApiAppDataRequest?,
  ): Response = httpResponseMetrics.timed("api.user.data-update") {
    withInstalledApp(installedAppId) { info ->
      appData.updateAppData(apiRequest.username(), installedAppId, dataId, ApiFormValues.toFormData(request?.values.orEmpty())).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.ok(ApiDtoMapper.appData(it, entityOf(info, it.entityType.value))) },
      )
    }
  }

  @DELETE
  @Path("/{installedAppId}/data/{dataId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed(ApiTokenRoles.USER_WRITE)
  fun deleteData(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response = httpResponseMetrics.timed("api.user.data-delete") {
    withInstalledApp(installedAppId) {
      appData.deleteAppData(apiRequest.username(), installedAppId, dataId).fold(
        ifLeft = { ApiResponses.domainError(it) },
        ifRight = { ApiResponses.noContent() },
      )
    }
  }

  private fun withInstalledApp(installedAppId: String, block: (InstalledAppInfo) -> Response): Response =
    userAppStore.getInstalledApp(apiRequest.username(), installedAppId).fold(
      ifLeft = { ApiResponses.domainError(it) },
      ifRight = { info ->
        if (info.installedApp.isTest || !apiRequest.coversApp(info.installedApp.appId.value)) {
          ApiResponses.notFound("Installed app $installedAppId not found")
        } else {
          block(info)
        }
      },
    )

  private fun entityOf(info: InstalledAppInfo, entityTypeId: String): EntityDefinition? =
    info.installedVersion.entityDefinitions.firstOrNull { it.id.value == entityTypeId }
}

/** Converts the JSON values of [ApiAppDataRequest] into the form-style `prop_<propertyId>` map the [AppDataPort] expects (as submitted by the UI). */
object ApiFormValues {
  private const val PROPERTY_KEY_PREFIX = "prop_"

  fun toFormData(values: Map<String, Any?>): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    values.forEach { (propertyId, value) -> flatten("$PROPERTY_KEY_PREFIX$propertyId", value, result) }
    return result
  }

  private fun flatten(key: String, value: Any?, result: MutableMap<String, List<String>>) {
    when (value) {
      null -> result[key] = emptyList()
      is Map<*, *> -> value.forEach { (nestedId, nestedValue) -> flatten("$key.$nestedId", nestedValue, result) }
      is Collection<*> -> result[key] = value.filterNotNull().map { it.toString() }
      else -> result[key] = listOf(value.toString())
    }
  }
}
