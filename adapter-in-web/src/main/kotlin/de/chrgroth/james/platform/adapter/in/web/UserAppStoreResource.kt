package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.DURATION_FORMAT_HINT
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.time.Clock
import io.quarkus.qute.Location
import io.quarkus.qute.RawString
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
  val valueProposals: List<String> = emptyList(),
  val referenceOptions: List<AppDataRow> = emptyList(),
  val listItemType: String? = null,
  val values: List<String> = emptyList(),
  val itemHtmlInputType: String = "text",
  val step: String = "",
  val min: String = "",
  val max: String = "",
) {
  fun valueProposalsString(): String = valueProposals.joinToString(",")

  /**
   * Returns the HTML `step` attribute value: the configured Step constraint if set, otherwise "any" for DOUBLE
   * properties (to allow arbitrary decimals) or empty string for all other property types.
   */
  fun numberStepAttribute(): String = when {
    step.isNotEmpty() -> step
    type == "DOUBLE" -> "any"
    else -> ""
  }
}

data class AppDataComputedPropertyView(
  val id: String,
  val name: String,
  val type: String,
  val value: String?,
)

data class AppDataDetail(
  val id: String,
  val installedAppId: String,
  val entityTypeId: String,
  val entityTypeName: String,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val referenceText: String,
  val displayText: String,
  val properties: List<AppDataPropertyView>,
  val computedProperties: List<AppDataComputedPropertyView>,
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
@BlockAdminAccess
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
  @Location("ui/user/app-entity-detail.html")
  private lateinit var appEntityDetailTemplate: Template

  @Inject
  @Location("ui/user/app-data-new.html")
  private lateinit var appDataNewTemplate: Template

  @Inject
  @Location("ui/user/app-data-edit.html")
  private lateinit var appDataEditTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @Inject
  private lateinit var appData: AppDataPort

  @Inject
  private lateinit var smartDefault: SmartDefaultPort

  @Inject
  private lateinit var computedProperty: ComputedPropertyPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

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
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppInstalledMessage(), "/ui/user/dashboard")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/upgrade")
  @Produces(MediaType.APPLICATION_JSON)
  fun upgradeApp(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.upgradeApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppUpgradedMessage(), "/ui/user/dashboard")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppDetail(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityById = info.installedVersion.entityDefinitions.associateBy { it.id.value }
    val allAppData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()

    val entityTabs = info.installedVersion.entityDefinitions.map { entityDef -> buildEntityTab(entityDef, entityById, allAppData) }

    return Response.ok(
      appDetailTemplate
        .data("info", info)
        .data("entityTabs", entityTabs)
        .data("pageSize", PAGE_SIZE),
    ).build()
  }

  @GET
  @Path("/user/apps/{installedAppId}/entities/{entityTypeId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppEntityDetail(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("entityTypeId") entityTypeId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == entityTypeId }
      ?: return Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
    val entityById = info.installedVersion.entityDefinitions.associateBy { it.id.value }
    val allAppData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()
    val entityTab = buildEntityTab(entityDef, entityById, allAppData)

    return Response.ok(
      appEntityDetailTemplate
        .data("info", info)
        .data("entity", entityTab)
        .data("pageSize", PAGE_SIZE),
    ).build()
  }

  @POST
  @Path("/user/apps/{installedAppId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteInstalledApp(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    return userAppStore.uninstallApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppUninstalledMessage(), "/ui/user/dashboard")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/new")
  @Produces(MediaType.TEXT_HTML)
  fun newAppDataForm(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityId") entityId: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == entityId }
      ?: return Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
    val computedSmartDefaults = smartDefault.computeSmartDefaults(entityDef, Clock.System.now())
    val referenceOptions = computeReferenceOptions(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef)
    return Response.ok(
      appDataNewTemplate
        .data("info", info)
        .data("entity", entityDef)
        .data("smartDefaults", computedSmartDefaults)
        .data("referenceOptions", referenceOptions)
        .data("entityListUrl", entityListUrl(installedAppId, entityDef.id.value, info.installedVersion.entityDefinitions.size))
        .data("objectFieldsJson", objectFieldsJsonFor(entityDef.properties))
        .data("referenceOptionsJson", referenceOptionsJsonFor(referenceOptions)),
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
    val entityTypeId = form.getFirst("entityTypeId") ?: return Response.ok(DeveloperApiResult(false, userMsg.userEntityTypeRequiredError())).build()
    val data = form.entries
      .filter { it.key.startsWith("prop_") }
      .associate { it.key to it.value }
    return appData.createAppData(userId, installedAppId, entityTypeId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { constraintViolationMessage(it) }
          }
          val errorDetails = error.pathedViolations.map { "${it.path}: ${constraintViolationMessage(it.violation)}" }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors, errorDetails = errorDetails)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = {
        val entityCount = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion?.entityDefinitions?.size ?: 1
        Response.ok(DeveloperApiResult(true, userMsg.userDataCreatedMessage(), entityListUrl(installedAppId, entityTypeId, entityCount))).build()
      },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/{dataId}")
  @Produces(MediaType.TEXT_HTML)
  fun appDataEdit(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    return appData.getAppData(userId, installedAppId, dataId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build() },
      ifRight = { appDataItem ->
        val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == appDataItem.entityType.value }
          ?: return Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
        val referenceOptions = computeReferenceOptions(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef)
        val detail = AppDataDetail(
          id = appDataItem.id.value,
          installedAppId = installedAppId,
          entityTypeId = appDataItem.entityType.value,
          entityTypeName = entityDef.name,
          objectVersion = appDataItem.objectVersion,
          createdAt = appDataItem.createdAt,
          lastChangedAt = appDataItem.lastChangedAt,
          referenceText = computeReferenceText(entityDef, appDataItem.id.value, appDataItem.data),
          displayText = computeDisplayText(entityDef, appDataItem.id.value, appDataItem.data),
          properties = entityDef.properties.map { prop ->
            AppDataPropertyView(
              id = prop.id.value,
              name = prop.name,
              type = prop.type.name,
              nullable = prop.nullable,
              value = appDataItem.data[prop.id.value],
              htmlInputType = TemplateFormattingExtensions.htmlInputType(prop),
              valueProposals = prop.valueProposals,
              referenceOptions = referenceOptions[prop.id.value] ?: emptyList(),
              listItemType = prop.listItemType?.name,
              values = if (prop.type == PropertyType.LIST) decodeListValue(appDataItem.data[prop.id.value]) else emptyList(),
              itemHtmlInputType = TemplateFormattingExtensions.itemHtmlInputType(prop),
              step = TemplateFormattingExtensions.constraintStep(prop),
              min = TemplateFormattingExtensions.constraintMin(prop),
              max = TemplateFormattingExtensions.constraintMax(prop),
            )
          },
          computedProperties = if (entityDef.computedProperties.isEmpty()) {
            emptyList()
          } else {
            val computedValues = computedProperty.computeValues(entityDef, appDataItem.data, Clock.System.now())
            entityDef.computedProperties.map { cp ->
              AppDataComputedPropertyView(
                id = cp.id.value,
                name = cp.name,
                type = cp.type.name,
                value = computedValues[cp.id.value],
              )
            }
          },
        )
        Response.ok(
          appDataEditTemplate
            .data("info", info)
            .data("detail", detail)
            .data("entityListUrl", entityListUrl(installedAppId, entityDef.id.value, info.installedVersion.entityDefinitions.size))
            .data("objectFieldsJson", objectFieldsJsonFor(entityDef.properties))
            .data("objectValuesJson", objectValuesJsonFor(entityDef.properties, appDataItem.data))
            .data("referenceOptionsJson", referenceOptionsJsonFor(referenceOptions)),
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
      .associate { it.key to it.value }
    return appData.updateAppData(userId, installedAppId, dataId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { constraintViolationMessage(it) }
          }
          val errorDetails = error.pathedViolations.map { "${it.path}: ${constraintViolationMessage(it.violation)}" }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors, errorDetails = errorDetails)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = { updated ->
        val entityCount = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion?.entityDefinitions?.size ?: 1
        Response.ok(DeveloperApiResult(true, userMsg.userDataUpdatedMessage(), entityListUrl(installedAppId, updated.entityType.value, entityCount))).build()
      },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/value-proposals")
  @Produces(MediaType.APPLICATION_JSON)
  fun getValueProposals(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityTypeId") entityTypeId: String,
    @QueryParam("propertyId") propertyId: String,
    @QueryParam("filter") filters: List<String>,
  ): Response {
    val userId = securityIdentity.principal.name
    val currentData = filters.associate { entry ->
      val idx = entry.indexOf('=')
      if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1)
      else entry to ""
    }
    return appData.getValueProposals(userId, installedAppId, entityTypeId, propertyId, currentData).fold(
      ifLeft = { Response.ok(emptyList<String>()).build() },
      ifRight = { proposals -> Response.ok(proposals).build() },
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
    val entityTypeId = appData.getAppData(userId, installedAppId, dataId).getOrNull()?.entityType?.value
    return appData.deleteAppData(userId, installedAppId, dataId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build() },
      ifRight = { count ->
        val message = if (count > 0) userMsg.userDataDeletedWithReferencesMessage(count) else userMsg.userDataDeletedMessage()
        val entityCount = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion?.entityDefinitions?.size ?: 1
        val redirectUrl = entityTypeId?.let { entityListUrl(installedAppId, it, entityCount) } ?: "/ui/user/apps/$installedAppId"
        Response.ok(DeveloperApiResult(true, message, redirectUrl)).build()
      },
    )
  }

  /** Builds the URL for the entity's data list: the app detail page itself when it is the only entity type, otherwise its dedicated entity page. */
  private fun entityListUrl(installedAppId: String, entityTypeId: String, entityCount: Int): String =
    if (entityCount <= 1) "/ui/user/apps/$installedAppId" else "/ui/user/apps/$installedAppId/entities/$entityTypeId"

  private fun buildEntityTab(entityDef: EntityDefinition, entityById: Map<String, EntityDefinition>, allAppData: List<AppData>): EntityTab {
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
    return EntityTab(
      entityId = entityDef.id.value,
      entityName = entityDef.name,
      rows = entityRows,
      currentPage = 1,
      totalPages = totalPages,
    )
  }

  private fun appStoreErrorMessage(code: String): String = when (code) {
    UserAppStoreError.APP_NOT_FOUND.code -> userMsg.userAppNotFoundError()
    UserAppStoreError.NO_PUBLISHED_VERSION.code -> userMsg.userNoPublishedVersionError()
    UserAppStoreError.ALREADY_INSTALLED.code -> userMsg.userAlreadyInstalledError()
    UserAppStoreError.NOT_INSTALLED.code -> userMsg.userNotInstalledError()
    UserAppStoreError.INSTALLED_APP_NOT_FOUND.code -> userMsg.userInstalledAppNotFoundError()
    UserAppStoreError.ALREADY_UP_TO_DATE.code -> userMsg.userAlreadyUpToDateError()
    else -> msg.commonUnexpectedError()
  }

  private fun appDataErrorMessage(code: String): String = when (code) {
    AppDataError.INSTALLED_APP_NOT_FOUND.code -> userMsg.userInstalledAppNotFoundError()
    AppDataError.ENTITY_NOT_FOUND.code -> userMsg.userEntityNotFoundError()
    AppDataError.CONSTRAINT_VIOLATION.code -> userMsg.userConstraintViolationError()
    AppDataError.APP_DATA_NOT_FOUND.code -> userMsg.userAppDataNotFoundError()
    AppDataError.REFERENCED_BY_NON_NULLABLE_PROPERTY.code -> userMsg.userReferencedByNonNullablePropertyError()
    else -> msg.commonUnexpectedError()
  }

  private fun constraintViolationMessage(violation: PropertyConstraintViolation): String = when (violation) {
    is PropertyConstraintViolation.UniqueKeyViolation -> userMsg.userUniqueKeyViolationError()
    is PropertyConstraintViolation.MinValueViolation -> userMsg.userMinValueViolationError(violation.min.toString())
    is PropertyConstraintViolation.MaxValueViolation -> userMsg.userMaxValueViolationError(violation.max.toString())
    is PropertyConstraintViolation.MinLengthViolation -> userMsg.userMinLengthViolationError(violation.min)
    is PropertyConstraintViolation.MaxLengthViolation -> userMsg.userMaxLengthViolationError(violation.max)
    is PropertyConstraintViolation.PatternViolation -> userMsg.userPatternViolationError(violation.regex)
    is PropertyConstraintViolation.MinSizeViolation -> userMsg.userMinSizeViolationError(violation.min)
    is PropertyConstraintViolation.MaxSizeViolation -> userMsg.userMaxSizeViolationError(violation.max)
    is PropertyConstraintViolation.InvalidReferenceViolation -> userMsg.userInvalidReferenceViolationError()
    is PropertyConstraintViolation.MinDateViolation -> userMsg.userMinDateViolationError(violation.min.toString())
    is PropertyConstraintViolation.MaxDateViolation -> userMsg.userMaxDateViolationError(violation.max.toString())
    is PropertyConstraintViolation.MinTimeViolation -> userMsg.userMinTimeViolationError(violation.min.toString())
    is PropertyConstraintViolation.MaxTimeViolation -> userMsg.userMaxTimeViolationError(violation.max.toString())
    is PropertyConstraintViolation.MinDatetimeViolation -> userMsg.userMinDatetimeViolationError(violation.min.toString())
    is PropertyConstraintViolation.MaxDatetimeViolation -> userMsg.userMaxDatetimeViolationError(violation.max.toString())
    is PropertyConstraintViolation.MinDurationViolation -> userMsg.userMinDurationViolationError(violation.min.toString())
    is PropertyConstraintViolation.MaxDurationViolation -> userMsg.userMaxDurationViolationError(violation.max.toString())
    is PropertyConstraintViolation.StepViolation -> userMsg.userStepViolationError(violation.step.toString())
    is PropertyConstraintViolation.InvalidDurationFormatViolation -> userMsg.userInvalidDurationFormatViolationError(DURATION_FORMAT_HINT)
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

  private fun computeReferenceText(entityDef: EntityDefinition, dataId: String, data: Map<String, String?>): String {
    val uniqueValues = entityDef.properties
      .filter { it.constraints.contains(PropertyConstraint.UniqueKey) }
      .map { data[it.id.value] ?: "" }
    return "${entityDef.name} $dataId [${uniqueValues.joinToString(", ")}]"
  }

  /** Returns the entity's Display Text if configured, otherwise the generic reference text. Used to label Reference property options. */
  private fun resolveReferenceLabel(entityDef: EntityDefinition, dataId: String, data: Map<String, String?>): String =
    if (entityDef.displayText != null) computeDisplayText(entityDef, dataId, data) else computeReferenceText(entityDef, dataId, data)

  /** Collects Reference properties recursively, including those nested inside OBJECT properties at any depth. */
  private fun collectReferenceProperties(properties: List<Property>): List<Property> =
    properties.flatMap { prop ->
      val ownMatch = if (prop.targetEntityId != null && (prop.type == PropertyType.REF || (prop.type == PropertyType.LIST && prop.listItemType == PropertyType.REF))) {
        listOf(prop)
      } else {
        emptyList()
      }
      ownMatch + collectReferenceProperties(prop.nestedProperties)
    }

  /** Builds the selectable Reference options (id + label) for each Reference property of the given entity, including nested ones. */
  private fun computeReferenceOptions(userId: String, installedAppId: String, entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition): Map<String, List<AppDataRow>> {
    val refProperties = collectReferenceProperties(entityDef.properties)
    if (refProperties.isEmpty()) return emptyMap()
    val allData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()
    return refProperties.associate { prop ->
      val targetEntity = entityDefinitions.find { it.id == prop.targetEntityId }
      val options = targetEntity?.let { target ->
        allData.filter { it.entityType == target.id }
          .map { AppDataRow(id = it.id.value, displayText = resolveReferenceLabel(target, it.id.value, it.data)) }
      } ?: emptyList()
      prop.id.value to options
    }
  }

  /** Recursive view of an OBJECT property's nested structure, for client-side rendering of its form fields. */
  private fun Property.toObjectFieldView(): Map<String, Any?> = mapOf(
    "id" to id.value,
    "name" to name,
    "type" to type.name,
    "nullable" to nullable,
    "htmlInputType" to TemplateFormattingExtensions.htmlInputType(this),
    "itemHtmlInputType" to TemplateFormattingExtensions.itemHtmlInputType(this),
    "listItemType" to listItemType?.name,
    "step" to TemplateFormattingExtensions.constraintStep(this),
    "min" to TemplateFormattingExtensions.constraintMin(this),
    "max" to TemplateFormattingExtensions.constraintMax(this),
    "nestedProperties" to nestedProperties.map { it.toObjectFieldView() },
  )

  /** Builds the JSON (propertyId -> field tree) used by object-property-fields.js to render OBJECT property fields. */
  private fun objectFieldsJsonFor(properties: List<Property>): RawString = RawString(
    ObjectMapper().writeValueAsString(properties.filter { it.type == PropertyType.OBJECT }.associate { it.id.value to it.toObjectFieldView() }),
  )

  /** Builds the JSON (propertyId -> decoded existing value) used to prefill OBJECT property fields on the edit form. */
  private fun objectValuesJsonFor(properties: List<Property>, data: Map<String, String?>): RawString = RawString(
    ObjectMapper().writeValueAsString(
      properties.filter { it.type == PropertyType.OBJECT }.associate { it.id.value to decodeObjectValue(data[it.id.value]) },
    ),
  )

  /** Builds the JSON (propertyId -> reference options) used by object-property-fields.js to render nested Reference selects. */
  private fun referenceOptionsJsonFor(referenceOptions: Map<String, List<AppDataRow>>): RawString = RawString(ObjectMapper().writeValueAsString(referenceOptions))

  companion object {
    private val DISPLAY_TEXT_TOKEN_REGEX = Regex("\\{([^}]+)\\}")
    private const val PAGE_SIZE = 50
  }
}
