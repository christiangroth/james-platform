package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

data class AppDataRow(
  val id: String,
  val entityTypeName: String,
  val lastChangedAt: Instant,
)

data class AppDataPropertyView(
  val id: String,
  val name: String,
  val type: String,
  val nullable: Boolean,
  val value: String?,
  val htmlInputType: String,
)

data class AppDataDetail(
  val id: String,
  val installedAppId: String,
  val entityTypeId: String,
  val entityTypeName: String,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val properties: List<AppDataPropertyView>,
)

@Path("/ui")
@ApplicationScoped
@Authenticated
@Suppress("Unused", "TooManyFunctions")
class UserAppStoreResource {

  @Inject
  @Location("ui/user/app-store.html")
  private lateinit var appStoreTemplate: Template

  @Inject
  @Location("ui/user/app-store-detail.html")
  private lateinit var appStoreDetailTemplate: Template

  @Inject
  @Location("ui/user/app-detail.html")
  private lateinit var appDetailTemplate: Template

  @Inject
  @Location("ui/user/app-data-new.html")
  private lateinit var appDataNewTemplate: Template

  @Inject
  @Location("ui/user/app-data-detail.html")
  private lateinit var appDataDetailTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @Inject
  private lateinit var appData: AppDataPort

  @GET
  @Path("/user/app-store")
  @Produces(MediaType.TEXT_HTML)
  fun appStore(): Response {
    val userId = securityIdentity.principal.name
    val allApps = userAppStore.listAllPublishedApps()
    val installedAppIds = userAppStore.getInstalledApps(userId).map { it.installedApp.appId.value }.toSet()
    return Response.ok(
      appStoreTemplate
        .data("apps", allApps)
        .data("installedAppIds", installedAppIds),
    ).build()
  }

  @GET
  @Path("/user/app-store/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appStoreDetail(@PathParam("appId") appId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.getPublishedApp(appId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/app-store")).build() },
      ifRight = { detail ->
        val installedApps = userAppStore.getInstalledApps(userId)
        val installed = installedApps.find { it.installedApp.appId.value == appId }
        Response.ok(
          appStoreDetailTemplate
            .data("detail", detail)
            .data("installedApp", installed),
        ).build()
      },
    )
  }

  @POST
  @Path("/user/app-store/apps/{appId}/install")
  @Produces(MediaType.APPLICATION_JSON)
  fun installApp(@PathParam("appId") appId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.installApp(userId, appId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "App installed.", "/ui/user/dashboard")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/upgrade")
  @Produces(MediaType.APPLICATION_JSON)
  fun upgradeApp(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.upgradeApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "App upgraded.", "/ui/user/dashboard")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppDetail(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    val info = installedApps.find { it.installedApp.id.value == installedAppId }
      ?: return Response.seeOther(URI.create("/ui/user/dashboard")).build()
    val entityNames = info.installedVersion.entityDefinitions.associate { it.id.value to it.name }
    val appDataRows = appData.listAppData(userId, installedAppId).getOrNull()
      ?.map { AppDataRow(id = it.id.value, entityTypeName = entityNames[it.entityType.value] ?: it.entityType.value, lastChangedAt = it.lastChangedAt) }
      ?: emptyList()
    val singleEntityId = info.installedVersion.entityDefinitions.takeIf { it.size == 1 }?.first()?.id?.value
    return Response.ok(
      appDetailTemplate
        .data("info", info)
        .data("appDataList", appDataRows)
        .data("singleEntityId", singleEntityId),
    ).build()
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/new")
  @Produces(MediaType.TEXT_HTML)
  fun newAppDataForm(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityId") entityId: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    val info = installedApps.find { it.installedApp.id.value == installedAppId }
      ?: return Response.seeOther(URI.create("/ui/user/dashboard")).build()
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == entityId }
      ?: return Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
    return Response.ok(
      appDataNewTemplate
        .data("info", info)
        .data("entity", entityDef),
    ).build()
  }

  @POST
  @Path("/user/apps/{installedAppId}/data")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createAppData(
    @PathParam("installedAppId") installedAppId: String,
    form: MultivaluedMap<String, String>,
  ): Response {
    val userId = securityIdentity.principal.name
    val entityTypeId = form.getFirst("entityTypeId") ?: return Response.ok(DeveloperApiResult(false, "Entity type is required.")).build()
    val data = form.entries
      .filter { it.key.startsWith("prop_") }
      .associate { it.key to (it.value.firstOrNull() ?: "") }
    return appData.createAppData(userId, installedAppId, entityTypeId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { constraintViolationMessage(it) }
          }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = { Response.ok(DeveloperApiResult(true, "Data created.", "/ui/user/apps/$installedAppId")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/{dataId}")
  @Produces(MediaType.TEXT_HTML)
  fun appDataDetail(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    val installedApps = userAppStore.getInstalledApps(userId)
    val info = installedApps.find { it.installedApp.id.value == installedAppId }
      ?: return Response.seeOther(URI.create("/ui/user/dashboard")).build()
    return appData.getAppData(userId, installedAppId, dataId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build() },
      ifRight = { appDataItem ->
        val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == appDataItem.entityType.value }
          ?: return Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
        val detail = AppDataDetail(
          id = appDataItem.id.value,
          installedAppId = installedAppId,
          entityTypeId = appDataItem.entityType.value,
          entityTypeName = entityDef.name,
          objectVersion = appDataItem.objectVersion,
          createdAt = appDataItem.createdAt,
          lastChangedAt = appDataItem.lastChangedAt,
          properties = entityDef.properties.map { prop ->
            AppDataPropertyView(
              id = prop.id.value,
              name = prop.name,
              type = prop.type.name,
              nullable = prop.nullable,
              value = appDataItem.data[prop.id.value],
              htmlInputType = when (prop.type) {
                PropertyType.BOOLEAN -> "checkbox"
                PropertyType.LONG, PropertyType.DOUBLE -> "number"
                PropertyType.DATE -> "date"
                PropertyType.TIME -> "time"
                PropertyType.DATETIME -> "datetime-local"
                else -> "text"
              },
            )
          },
        )
        Response.ok(
          appDataDetailTemplate
            .data("info", info)
            .data("detail", detail),
        ).build()
      },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/data/{dataId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateAppData(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
    form: MultivaluedMap<String, String>,
  ): Response {
    val userId = securityIdentity.principal.name
    val data = form.entries
      .filter { it.key.startsWith("prop_") }
      .associate { it.key to (it.value.firstOrNull() ?: "") }
    return appData.updateAppData(userId, installedAppId, dataId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { constraintViolationMessage(it) }
          }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = { Response.ok(DeveloperApiResult(true, "Data updated.", "/ui/user/apps/$installedAppId/data/$dataId")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/data/{dataId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteAppData(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    return appData.deleteAppData(userId, installedAppId, dataId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Data deleted.", "/ui/user/apps/$installedAppId")).build() },
    )
  }

  private fun appStoreErrorMessage(code: String): String = when (code) {
    UserAppStoreError.APP_NOT_FOUND.code -> "App not found."
    UserAppStoreError.NO_PUBLISHED_VERSION.code -> "No published version available."
    UserAppStoreError.ALREADY_INSTALLED.code -> "App is already installed."
    UserAppStoreError.NOT_INSTALLED.code -> "App is not installed."
    UserAppStoreError.INSTALLED_APP_NOT_FOUND.code -> "Installed app not found."
    UserAppStoreError.ALREADY_UP_TO_DATE.code -> "App is already up to date."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun appDataErrorMessage(code: String): String = when (code) {
    AppDataError.INSTALLED_APP_NOT_FOUND.code -> "Installed app not found."
    AppDataError.ENTITY_NOT_FOUND.code -> "Entity type not found."
    AppDataError.CONSTRAINT_VIOLATION.code -> "One or more values violate constraints."
    AppDataError.APP_DATA_NOT_FOUND.code -> "App data not found."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun constraintViolationMessage(violation: PropertyConstraintViolation): String = when (violation) {
    PropertyConstraintViolation.UNIQUE_KEY_VIOLATION -> "Value must be unique."
    PropertyConstraintViolation.MIN_VALUE_VIOLATION -> "Value is below the allowed minimum."
    PropertyConstraintViolation.MAX_VALUE_VIOLATION -> "Value exceeds the allowed maximum."
    PropertyConstraintViolation.MIN_LENGTH_VIOLATION -> "Value is too short."
    PropertyConstraintViolation.MAX_LENGTH_VIOLATION -> "Value is too long."
    PropertyConstraintViolation.PATTERN_VIOLATION -> "Value does not match the required pattern."
    PropertyConstraintViolation.MIN_SIZE_VIOLATION -> "List has too few elements."
    PropertyConstraintViolation.MAX_SIZE_VIOLATION -> "List has too many elements."
  }
}
