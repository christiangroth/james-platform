package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppConfigMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserAggregationMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.AppDataConstraintViolationError
import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.findPropertyPath
import de.chrgroth.james.platform.domain.model.app.referenceLookupTarget
import de.chrgroth.james.platform.domain.model.app.REFERENCE_NEW_FORM_PREFIX
import de.chrgroth.james.platform.domain.model.app.REFERENCE_LOOKUP_FORM_PREFIX
import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.DisplayTextResolver
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyValueFormat
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.computedSourceIn
import de.chrgroth.james.platform.domain.model.app.hasNestedProperties
import de.chrgroth.james.platform.domain.model.app.isObjectList
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.sortListItems
import de.chrgroth.james.platform.domain.model.app.formatUnitValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.model.readmodel.formatTimeBucketLabel
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppConfigurationPort
import de.chrgroth.james.platform.domain.port.`in`.app.ComputedPropertyPort
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultContext
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.time.Clock
import io.quarkus.qute.RawString
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
import java.math.BigDecimal
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
  val constraintHint: String = "",
  val itemConstraintHint: String = "",
  val hasUnit: Boolean = false,
  val unitFamily: String = "",
  val unitStorageGranularity: String = "",
  val unitDefaultGranularity: String = "",
  val unitFormatHint: String = "",
  // A LIST of OBJECTs: rendered client-side by object-property-fields.js like an OBJECT property, one group of fields per item.
  val objectList: Boolean = false,
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
  val appVersion: String,
  val referenceText: String,
  val displayText: String,
  val properties: List<AppDataPropertyView>,
  val computedProperties: List<AppDataComputedPropertyView>,
  val importProvenance: AppDataImportProvenanceView? = null,
  /** The installation's configuration object (ADR 0027): shown as "Einstellungen", it cannot be deleted. */
  val configuration: Boolean = false,
)

data class AppDataImportProvenanceView(
  val connectionName: String,
  val sourceUrl: String,
)

data class EntityTab(
  val entityId: String,
  val entityName: String,
  val rows: List<AppDataRow>,
  val currentPage: Int,
  val totalPages: Int,
)

data class AggregationView(
  val name: String,
  val valueFormatted: String,
  val stale: Boolean,
)

/** One row of an [AggregationTableView]: `periodLabel`/`groupLabel` are null for the dimension the aggregation doesn't have. */
data class AggregationTableRow(
  val periodLabel: String?,
  val groupLabel: String?,
  val valueFormatted: String,
  val stale: Boolean,
)

/**
 * Compact table display for one grouped and/or time-bucketed [AggregationDefinition] (issue #713), rendered as a flat,
 * sorted row list rather than a Zeitraum×Gruppe matrix - simpler to render and equally valid per the issue's own
 * "oder gruppierte Zeilen" fallback. [rows] is already capped at [MAX_AGGREGATION_TABLE_ROWS]; [truncated] indicates
 * whether [totalCount] exceeds [visibleCount].
 */
data class AggregationTableView(
  val name: String,
  val hasPeriod: Boolean,
  val hasGroup: Boolean,
  val rows: List<AggregationTableRow>,
  val stale: Boolean,
  val truncated: Boolean,
  val visibleCount: Int,
  val totalCount: Int,
)

/** The two kinds of aggregation display built for the entity detail page's aggregation panel (see `buildAggregationViews`). */
data class AggregationPanelViews(
  val simpleValues: List<AggregationView>,
  val tables: List<AggregationTableView>,
) {
  val isEmpty: Boolean get() = simpleValues.isEmpty() && tables.isEmpty()
}

data class InstalledAppStatusResponse(
  val stillInstalled: Boolean,
)

@Path("/ui")
@ApplicationScoped
@Authenticated
@BlockAdminAccess
@Suppress("Unused", "TooManyFunctions")
class UserAppStoreResource {

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
  private lateinit var aggregationRepository: AggregationRepositoryPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @Inject
  private lateinit var aggMsg: UserAggregationMessages

  @Inject
  private lateinit var appConfigMsg: AppConfigMessages

  @Inject
  private lateinit var appConfiguration: AppConfigurationPort

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Path("/user/app-store")
  @Produces(MediaType.TEXT_HTML)
  fun appStore(): Response = httpResponseMetrics.timed("page.user-app-store.store") {
    val userId = securityIdentity.principal.name
    val allApps = userAppStore.listAllPublishedApps()
    val installedAppIds = userAppStore.getInstalledApps(userId).map { it.installedApp.appId.value }.toSet()
    Response.ok(
      UserTemplates.`app-store`(allApps, installedAppIds),
    ).build()
  }

  @GET
  @Path("/user/app-store/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appStoreDetail(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("page.user-app-store.store-detail") {
    val userId = securityIdentity.principal.name
    userAppStore.getPublishedApp(appId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/app-store")).build() },
      ifRight = { detail ->
        val installedApps = userAppStore.getInstalledApps(userId)
        val installed = installedApps.find { it.installedApp.appId.value == appId }
        Response.ok(
          UserTemplates.`app-store-detail`(detail, installed),
        ).build()
      },
    )
  }

  @POST
  @Path("/user/app-store/apps/{appId}/install")
  @Produces(MediaType.APPLICATION_JSON)
  fun installApp(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.user-app-store.app-install") {
    val userId = securityIdentity.principal.name
    userAppStore.installApp(userId, appId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppInstalledMessage(), "/ui/user/dashboard")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/upgrade")
  @Produces(MediaType.APPLICATION_JSON)
  fun upgradeApp(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("rest.user-app-store.app-upgrade") {
    val userId = securityIdentity.principal.name
    userAppStore.upgradeApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppUpgradedMessage(), "/ui/user/dashboard")).build() },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppDetail(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("page.user-app-store.app-detail") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityById = info.installedVersion.entityDefinitions.associateBy { it.id.value }
    val allAppData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()

    val entityTabs = info.installedVersion.dataEntities.map { entityDef -> buildEntityTab(entityDef, entityById, allAppData) }

    Response.ok(
      UserTemplates.`app-detail`(info, entityTabs, PAGE_SIZE),
    ).build()
  }

  @GET
  @Path("/user/apps/{installedAppId}/entities/{entityTypeId}")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppEntityDetail(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("entityTypeId") entityTypeId: String,
  ): Response = httpResponseMetrics.timed("page.user-app-store.app-entity-detail") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.dataEntities.find { it.id.value == entityTypeId } ?: run {
      val configuration = entityTypeId == info.installedVersion.configurationEntity?.id?.value
      return@timed Response.seeOther(URI.create(if (configuration) settingsUrl(installedAppId) else "/ui/user/apps/$installedAppId")).build()
    }
    val entityById = info.installedVersion.entityDefinitions.associateBy { it.id.value }
    val allAppData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()
    val entityTab = buildEntityTab(entityDef, entityById, allAppData)
    val aggregations = buildAggregationViews(entityDef, installedAppId, entityById, allAppData)

    Response.ok(
      UserTemplates.`app-entity-detail`(info, entityTab, aggregations, PAGE_SIZE),
    ).build()
  }

  /** The "Einstellungen" of an installation (ADR 0027): the edit form of its configuration object, created from the defaults if missing. */
  @GET
  @Path("/user/apps/{installedAppId}/settings")
  @Produces(MediaType.TEXT_HTML)
  fun installedAppSettings(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("page.user-app-store.app-settings") {
    val userId = securityIdentity.principal.name
    appConfiguration.getConfiguration(userId, installedAppId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build() },
      ifRight = { Response.seeOther(URI.create("/ui/user/apps/$installedAppId/data/${it.id.value}")).build() },
    )
  }

  @POST
  @Path("/user/apps/{installedAppId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteInstalledApp(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("rest.user-app-store.app-delete") {
    val userId = securityIdentity.principal.name
    userAppStore.uninstallApp(userId, installedAppId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appStoreErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userAppUninstallQueuedMessage())).build() },
    )
  }

  /** Polled by the app detail page while an uninstall enqueued via [deleteInstalledApp] runs in the background. */
  @GET
  @Path("/user/apps/{installedAppId}/status")
  @Produces(MediaType.APPLICATION_JSON)
  fun installedAppStatus(@PathParam("installedAppId") installedAppId: String): Response = httpResponseMetrics.timed("rest.user-app-store.app-status") {
    val userId = securityIdentity.principal.name
    val stillInstalled = userAppStore.getInstalledApp(userId, installedAppId).isRight()
    Response.ok(InstalledAppStatusResponse(stillInstalled)).build()
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/new")
  @Produces(MediaType.TEXT_HTML)
  fun newAppDataForm(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityId") entityId: String?,
  ): Response = httpResponseMetrics.timed("page.user-app-store.data-new") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == entityId }
      ?: return@timed Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
    if (entityDef.configuration) return@timed Response.seeOther(URI.create(settingsUrl(installedAppId))).build()
    val computedSmartDefaults = smartDefaultsFor(userId, info, entityDef)
    val referenceOptions = computeReferenceOptions(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef)
    // a REF smart default (e.g. last(schuhe)) is an object id: the lookup field shows the referenced object's text for it
    val lookups = referenceLookupForms(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef, currentData = computedSmartDefaults)
    Response.ok(
      UserTemplates.`app-data-new`(
        info = info,
        entity = entityDef,
        smartDefaults = computedSmartDefaults,
        referenceOptions = referenceOptions,
        entityListUrl = entityListUrl(installedAppId, entityDef.id.value, info.installedVersion),
        objectFieldsJson = objectFieldsJsonFor(entityDef.properties, lookups),
        referenceOptionsJson = referenceOptionsJsonFor(referenceOptions + lookups.referenceOptions),
        referenceLookupsJson = RawString(ObjectMapper().writeValueAsString(lookups.lookups)),
        smartDefaultDependenciesJson = RawString(ObjectMapper().writeValueAsString(smartDefault.formDependencies(entityDef))),
      ),
    ).build()
  }

  /**
   * Re-evaluates the smart defaults of the create form while it is filled in (ADR 0029): [form] carries `entityId` and the `prop_` values entered
   * so far; answers the smart default values by property id. The form applies those depending on a changed field and not edited by the user.
   */
  @POST
  @Path("/user/apps/{installedAppId}/data/defaults")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun reevaluateSmartDefaults(
    @PathParam("installedAppId") installedAppId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-smart-defaults") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.ok(emptyMap<String, String>()).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == form.getFirst("entityId") }
      ?: return@timed Response.ok(emptyMap<String, String>()).build()
    val formValues = form.entries
      .filter { it.key.startsWith(PROPERTY_FORM_PREFIX) }
      .associate { (key, values) -> key.removePrefix(PROPERTY_FORM_PREFIX) to values.filter { it.isNotEmpty() }.let { if (it.size > 1) encodeListValue(it) else it.firstOrNull() } }
    Response.ok(smartDefaultsFor(userId, info, entityDef, formValues).filterValues { it != null }).build()
  }

  /** The smart defaults of [entityDef] for the create form of installation [info], given the [formValues] entered so far (by property id). */
  private fun smartDefaultsFor(userId: String, info: InstalledAppInfo, entityDef: EntityDefinition, formValues: Map<String, String?> = emptyMap()): Map<String, String?> {
    if (entityDef.properties.none { it.smartDefault != null }) return emptyMap()
    val context = SmartDefaultContext(
      now = Clock.System.now(),
      formValues = formValues,
      entries = { appData.listAppData(userId, info.installedApp.id.value).getOrNull().orEmpty() },
      config = appConfiguration.configurationValues(info.installedApp, info.installedVersion),
      configEntity = info.installedVersion.configurationEntity,
    )
    return smartDefault.computeSmartDefaults(entityDef, context)
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/defaults")
  @Produces(MediaType.APPLICATION_JSON)
  fun newAppDataDefaults(
    @PathParam("installedAppId") installedAppId: String,
    @QueryParam("entityId") entityId: String?,
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-defaults") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.ok(emptyMap<String, String>()).build() },
      ifRight = { it },
    )
    val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == entityId }
      ?: return@timed Response.ok(emptyMap<String, String>()).build()
    val computedSmartDefaults = smartDefaultsFor(userId, info, entityDef)
    val defaults = entityDef.properties
      .filter { it.type.supportsDefault() || it.smartDefault != null }
      .mapNotNull { property -> (property.default ?: computedSmartDefaults[property.id.value])?.let { property.id.value to it } }
      .toMap()
    Response.ok(defaults).build()
  }

  @POST
  @Path("/user/apps/{installedAppId}/data")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createAppData(
    @PathParam("installedAppId") installedAppId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-create") {
    val userId = securityIdentity.principal.name
    val entityTypeId = form.getFirst("entityTypeId") ?: return@timed Response.ok(DeveloperApiResult(false, userMsg.userEntityTypeRequiredError())).build()
    val data = appDataFormValues(form)
    appData.createAppData(userId, installedAppId, entityTypeId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { PropertyLabelTemplateExtensions.constraintViolationMessage(it) }
          }
          val errorDetails = error.pathedViolations.map { "${it.path}: ${PropertyLabelTemplateExtensions.constraintViolationMessage(it.violation)}" }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors, errorDetails = errorDetails)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = {
        val version = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion
        Response.ok(DeveloperApiResult(true, userMsg.userDataCreatedMessage(), entityListUrl(installedAppId, entityTypeId, version))).build()
      },
    )
  }

  @GET
  @Path("/user/apps/{installedAppId}/data/{dataId}")
  @Produces(MediaType.TEXT_HTML)
  fun appDataEdit(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("dataId") dataId: String,
  ): Response = httpResponseMetrics.timed("page.user-app-store.data-edit") {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    appData.getAppData(userId, installedAppId, dataId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build() },
      ifRight = { appDataItem ->
        val entityDef = info.installedVersion.entityDefinitions.find { it.id.value == appDataItem.entityType.value }
          ?: return@timed Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
        val referenceOptions = computeReferenceOptions(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef)
        val lookups = referenceLookupForms(userId, installedAppId, info.installedVersion.entityDefinitions, entityDef, appDataItem.data)
        val detail = AppDataDetail(
          id = appDataItem.id.value,
          installedAppId = installedAppId,
          entityTypeId = appDataItem.entityType.value,
          entityTypeName = entityDef.name,
          objectVersion = appDataItem.objectVersion,
          createdAt = appDataItem.createdAt,
          lastChangedAt = appDataItem.lastChangedAt,
          appVersion = appDataItem.appVersion.value,
          referenceText = computeReferenceText(entityDef, appDataItem.id.value, appDataItem.data),
          displayText = computeDisplayText(
            entityDef, appDataItem.id.value, appDataItem.data,
            DisplayTextContext(info.installedVersion.entityDefinitions.associateBy { it.id.value }, appData.listAppData(userId, installedAppId).getOrNull().orEmpty()),
          ),
          importProvenance = appDataItem.importProvenance?.let {
            AppDataImportProvenanceView(connectionName = it.connectionName, sourceUrl = it.sourceUrl)
          },
          configuration = entityDef.configuration,
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
              values = if (prop.type == PropertyType.LIST) sortListItems(prop.listItemType, decodeListValue(appDataItem.data[prop.id.value])) else emptyList(),
              itemHtmlInputType = TemplateFormattingExtensions.itemHtmlInputType(prop),
              step = TemplateFormattingExtensions.constraintStep(prop),
              min = TemplateFormattingExtensions.constraintMin(prop),
              max = TemplateFormattingExtensions.constraintMax(prop),
              constraintHint = PropertyLabelTemplateExtensions.constraintHint(prop),
              itemConstraintHint = PropertyLabelTemplateExtensions.itemConstraintHint(prop),
              hasUnit = TemplateFormattingExtensions.hasUnit(prop),
              unitFamily = TemplateFormattingExtensions.unitFamily(prop),
              unitStorageGranularity = TemplateFormattingExtensions.unitStorageGranularity(prop),
              unitDefaultGranularity = TemplateFormattingExtensions.unitDefaultGranularity(prop),
              unitFormatHint = TemplateFormattingExtensions.unitFormatHint(prop),
              objectList = prop.isObjectList,
            )
          },
          computedProperties = if (entityDef.computedProperties.isEmpty()) {
            emptyList()
          } else {
            val config = appConfiguration.configurationValues(info.installedApp, info.installedVersion)
            val computedValues = computedProperty.computeValues(entityDef, appDataItem.data, Clock.System.now(), config, info.installedVersion.configurationEntity)
            entityDef.computedProperties.map { cp ->
              val raw = computedValues[cp.id.value]
              val unit = cp.unit
              AppDataComputedPropertyView(
                id = cp.id.value,
                name = cp.name,
                type = cp.type.name,
                value = if (raw != null && unit != null) raw.toBigDecimalOrNull()?.let { formatUnitValue(it, unit.storageGranularity) } ?: raw else raw,
              )
            }
          },
        )
        Response.ok(
          UserTemplates.`app-data-edit`(
            info = info,
            detail = detail,
            entityListUrl = entityListUrl(installedAppId, entityDef.id.value, info.installedVersion),
            objectFieldsJson = objectFieldsJsonFor(entityDef.properties, lookups),
            objectValuesJson = objectValuesJsonFor(entityDef.properties, appDataItem.data),
            referenceOptionsJson = referenceOptionsJsonFor(referenceOptions + lookups.referenceOptions),
            referenceLookupsJson = RawString(ObjectMapper().writeValueAsString(lookups.lookups)),
          ),
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
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-update") {
    val userId = securityIdentity.principal.name
    val data = appDataFormValues(form)
    appData.updateAppData(userId, installedAppId, dataId, data).fold(
      ifLeft = { error ->
        if (error is AppDataConstraintViolationError) {
          val fieldErrors = error.propertyViolations.mapValues { (_, violations) ->
            violations.joinToString(" ") { PropertyLabelTemplateExtensions.constraintViolationMessage(it) }
          }
          val errorDetails = error.pathedViolations.map { "${it.path}: ${PropertyLabelTemplateExtensions.constraintViolationMessage(it.violation)}" }
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code), fieldErrors = fieldErrors, errorDetails = errorDetails)).build()
        } else {
          Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build()
        }
      },
      ifRight = { updated ->
        val version = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion
        val message = if (version?.configurationEntity?.id == updated.entityType) appConfigMsg.appConfigSavedMessage() else userMsg.userDataUpdatedMessage()
        Response.ok(DeveloperApiResult(true, message, entityListUrl(installedAppId, updated.entityType.value, version))).build()
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
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-value-proposals") {
    val userId = securityIdentity.principal.name
    val currentData = filters.associate { entry ->
      val idx = entry.indexOf('=')
      if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1)
      else entry to ""
    }
    appData.getValueProposals(userId, installedAppId, entityTypeId, propertyId, currentData).fold(
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
  ): Response = httpResponseMetrics.timed("rest.user-app-store.data-delete") {
    val userId = securityIdentity.principal.name
    val entityTypeId = appData.getAppData(userId, installedAppId, dataId).getOrNull()?.entityType?.value
    appData.deleteAppData(userId, installedAppId, dataId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appDataErrorMessage(error.code))).build() },
      ifRight = { count ->
        val message = if (count > 0) userMsg.userDataDeletedWithReferencesMessage(count) else userMsg.userDataDeletedMessage()
        val version = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()?.installedVersion
        val redirectUrl = entityTypeId?.let { entityListUrl(installedAppId, it, version) } ?: "/ui/user/apps/$installedAppId"
        Response.ok(DeveloperApiResult(true, message, redirectUrl)).build()
      },
    )
  }

  /**
   * Builds the URL for the entity's data list: the app detail page itself when it is the only data entity type (or the configuration, ADR 0027),
   * otherwise its dedicated entity page.
   */
  private fun entityListUrl(installedAppId: String, entityTypeId: String, version: AppVersion?): String {
    val configuration = version?.configurationEntity?.id?.value == entityTypeId
    return if (configuration || (version?.dataEntities?.size ?: 1) <= 1) "/ui/user/apps/$installedAppId" else "/ui/user/apps/$installedAppId/entities/$entityTypeId"
  }

  private fun settingsUrl(installedAppId: String): String = "/ui/user/apps/$installedAppId/settings"

  private fun buildEntityTab(entityDef: EntityDefinition, entityById: Map<String, EntityDefinition>, allAppData: List<AppData>): EntityTab {
    val context = DisplayTextContext(entityById, allAppData)
    val entityRows = allAppData
      .filter { it.entityType.value == entityDef.id.value }
      .let { rows -> applySortCriteria(rows, entityDef, entityById, allAppData) }
      .map { item ->
        AppDataRow(
          id = item.id.value,
          displayText = computeDisplayText(entityDef, item.id.value, item.data, context),
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

  /**
   * Builds the entity detail page's aggregation panel (issue #642, extended by #713): [AggregationDefinition]s with
   * no `refPath`/`timeBucket`/`groupBy` resolve to exactly one [AggregationValue] (`groupKey`/`bucketKey` both null)
   * and are shown as a single value ([AggregationPanelViews.simpleValues]); every other aggregation - grouped via
   * `refPath`/`groupBy`, bucketed via `timeBucket`, or both - resolves to several values and is shown as a compact
   * table ([AggregationPanelViews.tables], see [buildAggregationTableView]). One read-model query per
   * [AggregationDefinition] either way; reference display texts for `refPath` groups are resolved from [allAppData],
   * already loaded once for the whole page, so no additional per-row lookups are needed.
   */
  private fun buildAggregationViews(
    entityDef: EntityDefinition,
    installedAppId: String,
    entityById: Map<String, EntityDefinition>,
    allAppData: List<AppData>,
  ): AggregationPanelViews {
    val (grouped, simple) = entityDef.aggregations.partition { it.refPath != null || it.timeBucket != null || it.groupBy != null }

    val simpleValues = simple.mapNotNull { aggregation ->
      aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(InstalledAppId(installedAppId), aggregation.id)
        .find { it.id.groupKey == null && it.id.bucketKey == null }
        ?.let { value ->
          AggregationView(
            name = aggregation.name,
            valueFormatted = formatAggregationValue(aggregation, sourceUnitOf(aggregation, entityDef), value.value),
            stale = value.status == AggregationValueStatus.STALE,
          )
        }
    }

    val tables = grouped.mapNotNull { aggregation -> buildAggregationTableView(aggregation, entityDef, installedAppId, entityById, allAppData) }

    return AggregationPanelViews(simpleValues = simpleValues, tables = tables)
  }

  /** Builds the table view for one grouped/time-bucketed [aggregation], or null if it has no values yet (nothing computed so far). */
  private fun buildAggregationTableView(
    aggregation: AggregationDefinition,
    entityDef: EntityDefinition,
    installedAppId: String,
    entityById: Map<String, EntityDefinition>,
    allAppData: List<AppData>,
  ): AggregationTableView? {
    val values = aggregationRepository.findAllByInstalledAppIdAndAggregationDefinitionId(InstalledAppId(installedAppId), aggregation.id)
    if (values.isEmpty()) return null

    val hasPeriod = aggregation.timeBucket != null
    val hasGroup = aggregation.refPath != null || aggregation.groupBy != null
    val refTargetLabels = refTargetDisplayTexts(aggregation, entityDef, entityById, allAppData)
    val groupByProperty = aggregation.groupBy?.let { groupBy -> entityDef.properties.find { it.id == groupBy } }

    fun groupLabelOf(value: AggregationValue): String? {
      if (!hasGroup) return null
      val key = value.id.groupKey ?: return aggMsg.userAggregationTableNoGroupLabel()
      return when {
        aggregation.refPath != null -> refTargetLabels[key] ?: key
        groupByProperty != null -> formatGroupValue(groupByProperty, key)
        else -> key
      }
    }

    fun periodLabelOf(value: AggregationValue): String? {
      if (!hasPeriod) return null
      val key = value.id.bucketKey ?: return aggMsg.userAggregationTableNoPeriodLabel()
      return formatBucketLabel(aggregation.timeBucket!!, key)
    }

    val sourceUnit = sourceUnitOf(aggregation, entityDef)
    val rowsWithSortKeys = values.map { value ->
      Triple(value, groupLabelOf(value), periodLabelOf(value))
    }
    val sorted = when {
      hasPeriod -> rowsWithSortKeys.sortedWith(compareByDescending<Triple<AggregationValue, String?, String?>> { it.first.id.bucketKey ?: "" }.thenBy { it.second ?: "" })
      else -> rowsWithSortKeys.sortedBy { it.second ?: "" }
    }

    val totalCount = sorted.size
    val limited = sorted.take(MAX_AGGREGATION_TABLE_ROWS)
    val rows = limited.map { (value, groupLabel, periodLabel) ->
      AggregationTableRow(
        periodLabel = periodLabel,
        groupLabel = groupLabel,
        valueFormatted = formatAggregationValue(aggregation, sourceUnit, value.value),
        stale = value.status == AggregationValueStatus.STALE,
      )
    }

    return AggregationTableView(
      name = aggregation.name,
      hasPeriod = hasPeriod,
      hasGroup = hasGroup,
      rows = rows,
      stale = values.any { it.status == AggregationValueStatus.STALE },
      truncated = totalCount > rows.size,
      visibleCount = rows.size,
      totalCount = totalCount,
    )
  }

  /** Resolves the referenced Entity's Display Text per AppData id, for [aggregation]'s `refPath` target - empty map if [aggregation] has no `refPath`. */
  private fun refTargetDisplayTexts(
    aggregation: AggregationDefinition,
    entityDef: EntityDefinition,
    entityById: Map<String, EntityDefinition>,
    allAppData: List<AppData>,
  ): Map<String, String> {
    val refPath = aggregation.refPath ?: return emptyMap()
    val refProperty = entityDef.properties.find { it.id == refPath } ?: return emptyMap()
    val targetEntity = refProperty.targetEntityId?.let { entityById[it.value] } ?: return emptyMap()
    val context = DisplayTextContext(entityById, allAppData)
    return allAppData.filter { it.entityType == targetEntity.id }
      .associate { it.id.value to computeDisplayText(targetEntity, it.id.value, it.data, context) }
  }

  /** The unit of [aggregation]'s source: a (nested) property's or a computed property's unit, null without one. */
  private fun sourceUnitOf(aggregation: AggregationDefinition, entityDef: EntityDefinition): PropertyUnit? =
    entityDef.findPropertyPath(aggregation.sourceProperty)?.property?.unit ?: aggregation.computedSourceIn(entityDef)?.unit

  /** Formats an aggregation's raw numeric value, applying the source's unit granularity if it has one (COUNT never carries the source's unit). */
  private fun formatAggregationValue(aggregation: AggregationDefinition, unit: PropertyUnit?, value: Double): String {
    return if (unit != null && aggregation.function != AggregationFunction.COUNT) {
      formatUnitValue(BigDecimal.valueOf(value), unit.storageGranularity)
    } else {
      TemplateFormattingExtensions.formatted(value)
    }
  }

  /** Formats a `groupBy` group key (the raw property value of [property]) for display, respecting date/time formatting and the property's unit if any. */
  private fun formatGroupValue(property: Property, raw: String): String = PropertyValueFormat.format(property, raw)

  private fun formatBucketLabel(bucket: TimeBucket, bucketKey: String): String = formatTimeBucketLabel(bucket, bucketKey)

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
    AppDataError.CONFIGURATION_NOT_DEFINED.code -> appConfigMsg.appConfigNotDefinedError()
    AppDataError.CONFIGURATION_ALREADY_EXISTS.code -> appConfigMsg.appConfigAlreadyExistsError()
    AppDataError.CONFIGURATION_NOT_DELETABLE.code -> appConfigMsg.appConfigNotDeletableError()
    else -> msg.commonUnexpectedError()
  }

  /**
   * Sorts [rows] by the entity's sort criteria. A REF criterion compares the display texts of the referenced objects (resolved from
   * [allAppData]) instead of their ids, so it sorts alphabetically by what the user sees. Ties after all criteria are broken by the
   * object id, so the order is stable across reloads and pages.
   */
  private fun applySortCriteria(
    rows: List<AppData>,
    entityDef: EntityDefinition,
    entityById: Map<String, EntityDefinition>,
    allAppData: List<AppData>,
  ): List<AppData> {
    if (entityDef.sortBy.isEmpty()) return rows
    val appDataById by lazy { allAppData.associateBy { it.id.value } }
    fun referenceText(refId: String?): String? = refId?.let { id ->
      appDataById[id]?.let { target -> computeDisplayText(entityById[target.entityType.value], id, target.data) } ?: id
    }
    var comparator: Comparator<AppData>? = null
    for (criteria in entityDef.sortBy) {
      val propDef = entityDef.properties.find { it.id.value == criteria.propertyId } ?: continue
      val propType = propDef.type
      val singleComparator = Comparator<AppData> { a, b ->
        val aVal = if (propType == PropertyType.REF) referenceText(a.data[criteria.propertyId]) else a.data[criteria.propertyId]
        val bVal = if (propType == PropertyType.REF) referenceText(b.data[criteria.propertyId]) else b.data[criteria.propertyId]
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
    val stable = comparator?.thenComparing { row: AppData -> row.id.value } ?: return rows
    return rows.sortedWith(stable)
  }

  /**
   * Resolves an entity's display text via the shared [DisplayTextResolver] (same formatting as reports): a REF placeholder shows the
   * referenced object's own display text when [context] can resolve it, else its id.
   */
  private fun computeDisplayText(entityDef: EntityDefinition?, dataId: String, data: Map<String, String?>, context: DisplayTextContext? = null): String =
    (context?.resolver ?: DisplayTextResolver(emptyMap(), emptyList())).displayText(entityDef, dataId, data)

  /** What [computeDisplayText] needs to resolve REF placeholders: the installed version's entities and the installation's data. */
  private class DisplayTextContext(entityById: Map<String, EntityDefinition>, allAppData: List<AppData>) {
    val resolver = DisplayTextResolver(entityById, allAppData)
  }

  private fun computeReferenceText(entityDef: EntityDefinition, dataId: String, data: Map<String, String?>): String {
    val uniqueValues = entityDef.properties
      .filter { it.constraints.contains(PropertyConstraint.UniqueKey) }
      .map { data[it.id.value] ?: "" }
    return "${entityDef.name} $dataId [${uniqueValues.joinToString(", ")}]"
  }

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
    val context = DisplayTextContext(entityDefinitions.associateBy { it.id.value }, allData)
    return refProperties.associate { prop ->
      val targetEntity = entityDefinitions.find { it.id == prop.targetEntityId }
      val options = targetEntity?.let { target ->
        allData.filter { it.entityType == target.id }
          .map { AppDataRow(id = it.id.value, displayText = computeDisplayText(target, it.id.value, it.data, context)) }
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
    "constraintHint" to PropertyLabelTemplateExtensions.constraintHint(this),
    "itemConstraintHint" to PropertyLabelTemplateExtensions.itemConstraintHint(this),
    "hasUnit" to TemplateFormattingExtensions.hasUnit(this),
    "unitFamily" to TemplateFormattingExtensions.unitFamily(this),
    "unitStorageGranularity" to TemplateFormattingExtensions.unitStorageGranularity(this),
    "unitDefaultGranularity" to TemplateFormattingExtensions.unitDefaultGranularity(this),
    "unitFormatHint" to TemplateFormattingExtensions.unitFormatHint(this),
    "defaultValue" to default,
    "nestedProperties" to nestedProperties.map { it.toObjectFieldView() },
  )

  /**
   * Builds the JSON (propertyId -> field tree) used by object-property-fields.js to render OBJECT property fields, plus the
   * `refnew_<propertyId>` pseudo-OBJECT trees of auto-creating reference lookups (the target entity's further fields, ADR 0026).
   */
  private fun objectFieldsJsonFor(properties: List<Property>, lookups: ReferenceLookupForms): RawString = RawString(
    ObjectMapper().writeValueAsString(
      properties.filter { it.hasNestedProperties }.associate { it.id.value to it.toObjectFieldView() } + lookups.newObjectFields,
    ),
  )

  /** Form values relevant for AppData create/update: property values plus reference lookup texts and new-target values (ADR 0026). */
  private fun appDataFormValues(form: MultivaluedMap<String, String>): Map<String, List<String>> = form.entries
    .filter { entry -> listOf("prop_", REFERENCE_LOOKUP_FORM_PREFIX, REFERENCE_NEW_FORM_PREFIX).any { entry.key.startsWith(it) } }
    .associate { it.key to it.value }

  /** Client-side data for the reference lookup fields of one form (see reference-lookup-fields.js). */
  private data class ReferenceLookupForms(
    val lookups: Map<String, Map<String, Any?>>,
    val newObjectFields: Map<String, Map<String, Any?>>,
    val referenceOptions: Map<String, List<AppDataRow>>,
  )

  /**
   * For every REF property of [entityDef] with a valid reference lookup (ADR 0026): the target's lookup values as proposals (most
   * referenced by this property first), the current text for [currentData] (edit form) and — with auto-create — the target entity's
   * further properties as a pseudo-OBJECT field tree with their defaults, plus the reference options its own REF properties need.
   */
  private fun referenceLookupForms(
    userId: String,
    installedAppId: String,
    entityDefinitions: List<EntityDefinition>,
    entityDef: EntityDefinition,
    currentData: Map<String, String?>?,
  ): ReferenceLookupForms {
    val lookupProperties = entityDef.properties.mapNotNull { prop -> prop.referenceLookupTarget(entityDefinitions)?.let { Triple(prop, it.first, it.second) } }
    if (lookupProperties.isEmpty()) return ReferenceLookupForms(emptyMap(), emptyMap(), emptyMap())
    val allData = appData.listAppData(userId, installedAppId).getOrNull() ?: emptyList()
    val lookups = mutableMapOf<String, Map<String, Any?>>()
    val newObjectFields = mutableMapOf<String, Map<String, Any?>>()
    val referenceOptions = mutableMapOf<String, List<AppDataRow>>()
    for ((prop, target, lookupProperty) in lookupProperties) {
      val targets = allData.filter { it.entityType == target.id }
      val usage = allData.filter { it.entityType == entityDef.id }.mapNotNull { it.data[prop.id.value] }.groupingBy { it }.eachCount()
      val proposals = targets
        .mapNotNull { item -> item.data[lookupProperty.id.value]?.takeIf { it.isNotBlank() }?.let { it to (usage[item.id.value] ?: 0) } }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .map { it.first }
      val currentText = currentData?.get(prop.id.value)?.let { id -> targets.find { it.id.value == id }?.data?.get(lookupProperty.id.value) }
      val autoCreate = prop.referenceLookup?.autoCreate == true
      val newProperties = if (autoCreate) target.properties.filter { it.id != lookupProperty.id } else emptyList()
      if (newProperties.isNotEmpty()) {
        newObjectFields["$REFERENCE_NEW_FORM_PREFIX${prop.id.value}"] = mapOf(
          "id" to "$REFERENCE_NEW_FORM_PREFIX${prop.id.value}",
          "name" to target.name,
          "type" to PropertyType.OBJECT.name,
          "nullable" to true,
          "nestedProperties" to newProperties.map { it.toObjectFieldView() },
        )
        referenceOptions += computeReferenceOptions(userId, installedAppId, entityDefinitions, target)
      }
      val smartDefaults = if (newProperties.isNotEmpty()) smartDefault.computeSmartDefaults(target, SmartDefaultContext(Clock.System.now(), entries = { allData })) else emptyMap()
      lookups[prop.id.value] = mapOf(
        "proposals" to proposals,
        "value" to currentText,
        "autoCreate" to autoCreate,
        "hasNewFields" to newProperties.isNotEmpty(),
        "targetEntityName" to target.name,
        "newDefaults" to newProperties.mapNotNull { p -> (p.default ?: smartDefaults[p.id.value])?.let { p.id.value to it } }.toMap(),
      )
    }
    return ReferenceLookupForms(lookups, newObjectFields, referenceOptions)
  }



  /** Builds the JSON (propertyId -> decoded existing value) used to prefill OBJECT property fields on the edit form. */
  private fun objectValuesJsonFor(properties: List<Property>, data: Map<String, String?>): RawString = RawString(
    ObjectMapper().writeValueAsString(
      properties.filter { it.hasNestedProperties }.associate { property ->
        val raw = data[property.id.value]
        property.id.value to if (property.isObjectList) decodeListValue(raw).map { decodeObjectValue(it) } else decodeObjectValue(raw)
      },
    ),
  )

  /** Builds the JSON (propertyId -> reference options) used by object-property-fields.js to render nested Reference selects. */
  private fun referenceOptionsJsonFor(referenceOptions: Map<String, List<AppDataRow>>): RawString = RawString(ObjectMapper().writeValueAsString(referenceOptions))

  companion object {
    private const val PAGE_SIZE = 25

    /** Row cap for one aggregation's table in the aggregation panel, so a long-running installation's page doesn't explode (issue #713). */
    private const val MAX_AGGREGATION_TABLE_ROWS = 30
  }
}

/** Prefix of the form fields carrying property values (`prop_<propertyId>`). */
private const val PROPERTY_FORM_PREFIX = "prop_"
