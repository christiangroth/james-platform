package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.SortDirection
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
  val displayText: String,
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

data class EntityTab(
  val entityId: String,
  val entityName: String,
  val rows: List<AppDataRow>,
  val currentPage: Int,
  val totalPages: Int,
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
    val entityById = info.installedVersion.entityDefinitions.associateBy { it.id.value }
    val allAppData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()

    val entities = info.installedVersion.entityDefinitions
    val entityTabs = entities.map { entityDef ->
      val entityRows = allAppData
        .filter { it.entityType.value == entityDef.id.value }
        .let { rows -> applySortCriteria(rows, entityDef, entityById) }
        .map { item ->
          AppDataRow(
            id = item.id.value,
            displayText = computeDisplayText(entityDef, item.id.value, item.data),
          )
        }
      val totalPages = maxOf(1, (entityRows.size + PAGE_SIZE - 1) / PAGE_SIZE)
      EntityTab(
        entityId = entityDef.id.value,
        entityName = entityDef.name,
        rows = entityRows,
        currentPage = 1,
        totalPages = totalPages,
      )
    }

    return Response.ok(
      appDetailTemplate
        .data("info", info)
        .data("entityTabs", entityTabs)
        .data("pageSize", PAGE_SIZE),
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
      ifRight = { count ->
        val message = if (count > 0) "Data deleted. $count reference(s) cleared." else "Data deleted."
        Response.ok(DeveloperApiResult(true, message, "/ui/user/apps/$installedAppId")).build()
      },
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
    AppDataError.REFERENCED_BY_NON_NULLABLE_PROPERTY.code -> "Cannot delete: this entry is referenced by a required property in another record. Please remove or update the referencing records first."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun constraintViolationMessage(violation: PropertyConstraintViolation): String = when (violation) {
    is PropertyConstraintViolation.UniqueKeyViolation -> "Value must be unique."
    is PropertyConstraintViolation.MinValueViolation -> "Value is below the allowed minimum of ${violation.min}."
    is PropertyConstraintViolation.MaxValueViolation -> "Value exceeds the allowed maximum of ${violation.max}."
    is PropertyConstraintViolation.MinLengthViolation -> "Value must be at least ${violation.min} characters long."
    is PropertyConstraintViolation.MaxLengthViolation -> "Value must not exceed ${violation.max} characters."
    is PropertyConstraintViolation.PatternViolation -> "Value does not match the required pattern: ${violation.regex}."
    is PropertyConstraintViolation.MinSizeViolation -> "List must have at least ${violation.min} elements."
    is PropertyConstraintViolation.MaxSizeViolation -> "List must not have more than ${violation.max} elements."
  }

  private fun applySortCriteria(
    rows: List<AppData>,
    entityDef: EntityDefinition,
    entityById: Map<String, EntityDefinition>,
  ): List<AppData> {
    if (entityDef.sortBy.isEmpty()) return rows
    var comparator: Comparator<AppData>? = null
    for (criteria in entityDef.sortBy) {
      val propDef = entityDef.properties.find { it.id.value == criteria.propertyId } ?: continue
      val propType = propDef.type
      val singleComparator = Comparator<AppData> { a, b ->
        val aVal = a.data[criteria.propertyId]
        val bVal = b.data[criteria.propertyId]
        when {
          aVal == null && bVal == null -> 0
          aVal == null -> 1
          bVal == null -> -1
          propType == PropertyType.LONG -> {
            val aLong = aVal.toLongOrNull()
            val bLong = bVal.toLongOrNull()
            when {
              aLong != null && bLong != null -> aLong.compareTo(bLong)
              aLong != null -> -1
              bLong != null -> 1
              else -> aVal.compareTo(bVal)
            }
          }
          propType == PropertyType.DOUBLE -> {
            val aDouble = aVal.toDoubleOrNull()
            val bDouble = bVal.toDoubleOrNull()
            when {
              aDouble != null && bDouble != null -> aDouble.compareTo(bDouble)
              aDouble != null -> -1
              bDouble != null -> 1
              else -> aVal.compareTo(bVal)
            }
          }
          propType == PropertyType.BOOLEAN -> aVal.compareTo(bVal)
          else -> aVal.compareTo(bVal, ignoreCase = true)
        }
      }
      val directedComparator = if (criteria.direction == SortDirection.DESC) singleComparator.reversed() else singleComparator
      comparator = comparator?.thenComparing(directedComparator) ?: directedComparator
    }
    return if (comparator != null) rows.sortedWith(comparator) else rows
  }

  private fun computeDisplayText(entityDef: EntityDefinition?, dataId: String, data: Map<String, String?>): String {
    val template = entityDef?.displayText ?: return dataId
    val nameToId = entityDef.properties.associate { it.name to it.id.value }
    val result = DISPLAY_TEXT_TOKEN_REGEX.replace(template) { match ->
      val key = match.groupValues[1]
      if (key == "id") dataId
      else {
        val propId = nameToId[key]
        if (propId == null) "<?>" else data[propId] ?: ""
      }
    }.trim()
    return result.ifBlank { dataId }
  }

  companion object {
    private val DISPLAY_TEXT_TOKEN_REGEX = Regex("\\{([^}]+)\\}")
    private const val PAGE_SIZE = 50
  }
}
