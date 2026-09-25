package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.DeveloperAggregationMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.DeveloperMessages
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DeveloperTestInstallationError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.error.InvalidAggregationDefinitionError
import de.chrgroth.james.platform.domain.error.InvalidObjectStructureError
import de.chrgroth.james.platform.domain.error.TestDataGeneratorError
import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PredefinedSmartDefault
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.port.`in`.app.AggregationInput
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.DeveloperTestInstallationPort
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGenerationOutcome
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGeneratorPort
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import io.quarkus.qute.RawString
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
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
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

data class DeveloperApiResult(
  val ok: Boolean,
  val message: String,
  val redirectUrl: String? = null,
  val fieldErrors: Map<String, String>? = null,
  val propertyId: String? = null,
  val errorDetails: List<String>? = null,
  /** Set for [TestDataGeneratorPort.generateTestData] once a large run was enqueued instead of generated inline. */
  val queued: Boolean = false,
)

/** Polled by the version editor page while a generation run enqueued via [DeveloperAppResource.generateTestData] runs in the background. */
data class TestDataGenerationStatusResponse(
  val generating: Boolean,
)

data class AppStatusResponse(
  val stillExists: Boolean,
)

data class DashboardAppInfo(
  val app: App,
  val hasDraft: Boolean,
  val latestVersionNumber: String?,
  val latestVersionPublishedAt: Instant?,
)

/** A Developer-owned test installation shown on the app overview page. */
data class TestInstallationInfo(
  val installedAppId: String,
  val version: AppVersion,
  val installedAt: Instant,
)

/** One AggregationDefinition in the version editor, with enum and property references as plain strings plus display labels. */
data class AggregationEditorRow(
  val id: String,
  val name: String,
  val function: String,
  val functionLabel: String,
  val sourceProperty: String,
  val sourcePropertyName: String,
  val refPath: String,
  val timeBucket: String,
  val timeProperty: String,
  val groupBy: String,
  val details: String,
)

/** A top-level property offered in the aggregation editor's selects, flagged by the roles it may fill (see docs/adr/0020-aggregation-definitions.md). */
data class AggregationPropertyOptionRow(
  val id: String,
  val name: String,
  val numeric: Boolean,
  val ref: Boolean,
  val dateTime: Boolean,
  val groupable: Boolean,
)

data class SortCriteriaRequest @JsonCreator constructor(
  @param:JsonProperty("propertyId") val propertyId: String,
  @param:JsonProperty("direction") val direction: SortDirection,
)

/** A single segment of the breadcrumb trail when editing properties nested inside an OBJECT property. */
data class PropertyBreadcrumb(
  val id: String,
  val name: String,
  val path: String,
)

@Path("/ui/developer")
@ApplicationScoped
@Authenticated
@Suppress("Unused", "TooManyFunctions", "LargeClass")
class DeveloperAppResource {

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userProfile: UserProfileServicePort

  @Inject
  private lateinit var appManagement: AppManagementPort

  @Inject
  private lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  private lateinit var developerTestInstallations: DeveloperTestInstallationPort

  @Inject
  private lateinit var testDataGenerator: TestDataGeneratorPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var devMsg: DeveloperMessages

  @Inject
  private lateinit var aggregationMsg: DeveloperAggregationMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Path("/dashboard")
  @Produces(MediaType.TEXT_HTML)
  fun developerDashboard(): Any = httpResponseMetrics.timed("page.developer.dashboard") {
    val username = securityIdentity.principal.name
    val developerId = currentDeveloperUserIdValue()
    val apps = if (developerId != null) appManagement.listApps(developerId) else emptyList()
    val appInfos = apps.map { app ->
      val versions = appVersionManagement.listVersions(app.id.value).getOrNull() ?: emptyList()
      val hasDraft = versions.any { it.status == AppVersionStatus.DRAFT }
      val latestPublished = versions
        .filter { it.status == AppVersionStatus.PUBLISHED }
        .maxByOrNull { it.createdAt }
      DashboardAppInfo(
        app = app,
        hasDraft = hasDraft,
        latestVersionNumber = latestPublished?.versionNumber?.value,
        latestVersionPublishedAt = latestPublished?.createdAt,
      )
    }
    DeveloperTemplates.dashboard(username, appInfos)
  }

  @POST
  @Path("/apps")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createApp(
    @FormParam("name") name: String,
    @FormParam("description") description: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.app-create") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerAppNameRequiredError())).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    appManagement.createApp(name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { app -> Response.ok(DeveloperApiResult(true, devMsg.developerAppCreatedMessage(), "/ui/developer/apps/${app.id.value}")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateApp(
    @PathParam("appId") appId: String,
    @FormParam("name") name: String,
    @FormParam("description") description: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.app-update") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerAppNameRequiredError())).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    appManagement.updateApp(appId, name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerAppUpdatedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/deactivate")
  @Produces(MediaType.APPLICATION_JSON)
  fun deactivateApp(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.developer.app-deactivate") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    appManagement.deactivateApp(appId, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { result ->
        Response.ok(DeveloperApiResult(true, devMsg.developerAppDeactivatedMessage(result.activeInstallationCount))).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/activate")
  @Produces(MediaType.APPLICATION_JSON)
  fun activateApp(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.developer.app-activate") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    appManagement.activateApp(appId, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerAppActivatedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteApp(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.developer.app-delete") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    appManagement.deleteApp(appId, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerAppDeleteQueuedMessage())).build() },
    )
  }

  /** Polled by the app overview page while a delete enqueued via [deleteApp] runs in the background. */
  @GET
  @Path("/apps/{appId}/status")
  @Produces(MediaType.APPLICATION_JSON)
  fun appStatus(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.developer.app-status") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    val stillExists = appManagement.getApp(appId, developerId).isRight()
    Response.ok(AppStatusResponse(stillExists)).build()
  }

  @GET
  @Path("/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appOverview(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("page.developer.app-overview") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    appManagement.getApp(appId, developerId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/dashboard")).build() },
      ifRight = { app ->
        val versions = appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()
        val hasDraft = versions.any { it.status == AppVersionStatus.DRAFT }
        val publishedByDate = versions.filter { it.status == AppVersionStatus.PUBLISHED }.sortedBy { it.createdAt }
        val publishedIdsWithPredecessor = if (publishedByDate.size > 1) publishedByDate.drop(1).map { it.id.value }.toSet() else emptySet<String>()
        val draftIdWithDiff = if (hasDraft && publishedByDate.isNotEmpty()) setOf(versions.first { it.status == AppVersionStatus.DRAFT }.id.value) else emptySet<String>()
        val versionIdsWithPredecessor = publishedIdsWithPredecessor + draftIdWithDiff
        val installationCount = appManagement.getActiveInstallationCount(appId, developerId).getOrNull() ?: 0
        val testInstallations = developerTestInstallations.listTestInstallations(appId, developerId).getOrNull().orEmpty()
          .mapNotNull { installed ->
            val versionId = installed.installedVersionId?.value ?: return@mapNotNull null
            val version = versions.find { it.id.value == versionId } ?: return@mapNotNull null
            TestInstallationInfo(installedAppId = installed.id.value, version = version, installedAt = installed.installedAt)
          }
          .sortedByDescending { it.installedAt }
        Response.ok(
          DeveloperTemplates.`app-overview`(app, versions, hasDraft, versionIdsWithPredecessor, installationCount, testInstallations),
        ).build()
      },
    )
  }

  /** A test installation for any version of a Developer's own app, without a real User account. */
  @POST
  @Path("/apps/{appId}/test-installations")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createTestInstallation(
    @PathParam("appId") appId: String,
    @FormParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.test-installation-create") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    // Stamped with the raw principal name, not developerId, so it is reachable via the User-facing lookups (see UserAppStoreResource).
    developerTestInstallations.createTestInstallation(appId, versionId, developerId, securityIdentity.principal.name).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, testInstallationErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerTestInstallationCreatedMessage(), "/ui/developer/apps/$appId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/test-installations/{installedAppId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteTestInstallation(
    @PathParam("appId") appId: String,
    @PathParam("installedAppId") installedAppId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.test-installation-delete") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    developerTestInstallations.deleteTestInstallation(appId, installedAppId, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, testInstallationErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerTestInstallationDeletedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions")
  @Produces(MediaType.APPLICATION_JSON)
  fun createVersion(@PathParam("appId") appId: String): Response = httpResponseMetrics.timed("rest.developer.version-create") {
    appVersionManagement.createVersion(appId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build() },
      ifRight = { version ->
        Response.ok(DeveloperApiResult(true, devMsg.developerVersionCreatedMessage(), "/ui/developer/apps/$appId/versions/${version.id.value}")).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}")
  @Produces(MediaType.TEXT_HTML)
  fun versionEditor(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("page.developer.version-editor") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        val publishedVersion = latestPublishedVersionForDraft(appId, isDraft)
        val hasDiff = publishedVersion != null
        Response.ok(
          DeveloperTemplates.`version-editor`(
            app = app,
            version = version,
            isDraft = isDraft,
            hasDiff = hasDiff,
            selectedEntity = null,
            selectedReport = null,
            predefinedSmartDefaultsJson = predefinedSmartDefaultsJson,
            currentProperties = emptyList<Property>(),
            currentPropertiesJson = EMPTY_JSON_ARRAY,
            path = "",
            breadcrumb = emptyList<PropertyBreadcrumb>(),
            isNestedLevel = false,
            testInstallations = testInstallationsForVersion(appId, version, developerId),
            publishedVersion = publishedVersion,
            removedEntities = removedEntities(publishedVersion, version),
            removedProperties = emptyList<Property>(),
            aggregations = emptyList<AggregationEditorRow>(),
            aggregationPropertyOptions = emptyList<AggregationPropertyOptionRow>(),
          ),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}")
  @Produces(MediaType.TEXT_HTML)
  fun versionEntityEditor(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @QueryParam("path") pathParam: String?,
  ): Response = httpResponseMetrics.timed("page.developer.version-entity-editor") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        val publishedVersion = latestPublishedVersionForDraft(appId, isDraft)
        val hasDiff = publishedVersion != null
        val selectedEntity = version.entityDefinitions.find { it.id.value == entityId }
        val pathIds = parsePath(pathParam)
        val resolved = selectedEntity?.let { resolvePath(it, pathIds) }
        val currentProperties = resolved?.properties ?: selectedEntity?.properties ?: emptyList()
        val breadcrumb = resolved?.breadcrumb ?: emptyList()
        val currentPropertiesJson = RawString(
          ObjectMapper().writeValueAsString(currentProperties.map { mapOf("id" to it.id.value, "name" to it.name) }),
        )
        Response.ok(
          DeveloperTemplates.`version-editor`(
            app = app,
            version = version,
            isDraft = isDraft,
            hasDiff = hasDiff,
            selectedEntity = selectedEntity,
            selectedReport = null,
            predefinedSmartDefaultsJson = predefinedSmartDefaultsJson,
            currentProperties = currentProperties,
            currentPropertiesJson = currentPropertiesJson,
            path = breadcrumb.lastOrNull()?.path ?: "",
            breadcrumb = breadcrumb,
            isNestedLevel = breadcrumb.isNotEmpty(),
            testInstallations = testInstallationsForVersion(appId, version, developerId),
            publishedVersion = publishedVersion,
            removedEntities = removedEntities(publishedVersion, version),
            removedProperties = if (breadcrumb.isEmpty()) removedProperties(publishedVersion, selectedEntity) else emptyList(),
            aggregations = selectedEntity?.let { aggregationRows(it) }.orEmpty(),
            aggregationPropertyOptions = selectedEntity?.let { aggregationPropertyOptions(it) }.orEmpty(),
          ),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/new")
  @Produces(MediaType.TEXT_HTML)
  fun newProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @QueryParam("path") pathParam: String?,
  ): Response = httpResponseMetrics.timed("page.developer.property-new") { editPropertyPage(appId, versionId, entityId, null, pathParam) }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/edit")
  @Produces(MediaType.TEXT_HTML)
  fun editProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @QueryParam("path") pathParam: String?,
  ): Response = httpResponseMetrics.timed("page.developer.property-edit") { editPropertyPage(appId, versionId, entityId, propertyId, pathParam) }

  private fun editPropertyPage(
    appId: String,
    versionId: String,
    entityId: String,
    propertyId: String?,
    pathParam: String?,
  ): Response {
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val selectedEntity = version.entityDefinitions.find { it.id.value == entityId }
          ?: return@fold Response.seeOther(URI.create("/ui/developer/apps/$appId/versions/$versionId")).build()
        val pathIds = parsePath(pathParam)
        val resolved = resolvePath(selectedEntity, pathIds)
        val currentProperties = resolved?.properties ?: (selectedEntity.properties.takeIf { pathIds.isEmpty() } ?: emptyList())
        val breadcrumb = resolved?.breadcrumb ?: emptyList()
        val selectedProperty = propertyId?.let { id -> currentProperties.find { it.id.value == id } }
        val currentPropertiesJson = RawString(
          ObjectMapper().writeValueAsString(currentProperties.map { mapOf("id" to it.id.value, "name" to it.name) }),
        )
        // The immediate parent OBJECT property (if any), so Cancel can return into the merged property editor
        // instead of the old standalone nested-properties browsing view.
        val parentPropertyId = breadcrumb.lastOrNull()?.id
        val parentPath = if (breadcrumb.size >= 2) breadcrumb[breadcrumb.size - 2].path else ""
        Response.ok(
          DeveloperTemplates.`edit-property`(
            app = app,
            version = version,
            selectedEntity = selectedEntity,
            selectedProperty = selectedProperty,
            path = breadcrumb.lastOrNull()?.path ?: "",
            breadcrumb = breadcrumb,
            parentPropertyId = parentPropertyId,
            parentPath = parentPath,
            predefinedSmartDefaultsJson = predefinedSmartDefaultsJson,
            currentPropertiesJson = currentPropertiesJson,
          ),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/publish")
  @Produces(MediaType.TEXT_HTML)
  fun publishVersionPage(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("page.developer.publish-version") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        Response.ok(
          DeveloperTemplates.`publish-version`(app, version),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/reports/{reportId}")
  @Produces(MediaType.TEXT_HTML)
  fun versionReportEditor(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("reportId") reportId: String,
  ): Response = httpResponseMetrics.timed("page.developer.report-editor") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        val publishedVersion = latestPublishedVersionForDraft(appId, isDraft)
        val hasDiff = publishedVersion != null
        val selectedReport = version.reports.find { it.id.value == reportId }
        Response.ok(
          DeveloperTemplates.`version-editor`(
            app = app,
            version = version,
            isDraft = isDraft,
            hasDiff = hasDiff,
            selectedEntity = null,
            selectedReport = selectedReport,
            predefinedSmartDefaultsJson = predefinedSmartDefaultsJson,
            currentProperties = emptyList<Property>(),
            currentPropertiesJson = EMPTY_JSON_ARRAY,
            path = "",
            breadcrumb = emptyList<PropertyBreadcrumb>(),
            isNestedLevel = false,
            testInstallations = testInstallationsForVersion(appId, version, developerId),
            publishedVersion = publishedVersion,
            removedEntities = removedEntities(publishedVersion, version),
            removedProperties = emptyList<Property>(),
            aggregations = emptyList<AggregationEditorRow>(),
            aggregationPropertyOptions = emptyList<AggregationPropertyOptionRow>(),
          ),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/diff")
  @Produces(MediaType.TEXT_HTML)
  fun versionDiff(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("page.developer.version-diff") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return@timed Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    appVersionManagement.getVersionDiff(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { diff ->
        Response.ok(
          DeveloperTemplates.`version-diff`(app, diff),
        ).build()
      },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}/version-bump")
  @Produces(MediaType.APPLICATION_JSON)
  fun getVersionBump(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.version-bump") {
    appVersionManagement.computeVersionBump(appId, versionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build() },
      ifRight = { bump ->
        Response.ok(
          VersionBumpResponse(
            hasBreakingChanges = bump.hasBreakingChanges,
            hasChanges = bump.hasChanges,
            suggestedVersionOnBreaking = bump.suggestedVersionOnBreaking.value,
            suggestedVersionOnFeature = bump.suggestedVersionOnFeature.value,
            suggestedVersionOnBugfix = bump.suggestedVersionOnBugfix.value,
            breakingEntityIds = bump.breakingEntityIds.map { it.value },
            breakingPropertyIds = bump.breakingPropertyIds.map { it.value },
          ),
        ).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/publish")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun publishVersion(
    @PathParam("appId") appId: String,
    @FormParam("bumpType") bumpType: String?,
    @FormParam("releaseNotes") releaseNotes: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.version-publish") {
    appVersionManagement.publishVersion(appId, bumpType, releaseNotes.orEmpty()).fold(
      ifLeft = { error ->
        when (error) {
          is DisplayTextInvalidError -> {
            val names = error.entityNames.joinToString(", ")
            Response.ok(DeveloperApiResult(false, devMsg.developerInvalidDisplayTextError(names))).build()
          }
          is InvalidObjectStructureError -> {
            val names = error.entityNames.joinToString(", ")
            Response.ok(DeveloperApiResult(false, devMsg.developerInvalidObjectStructureError(names))).build()
          }
          is InvalidAggregationDefinitionError -> {
            val names = error.entityNames.joinToString(", ")
            Response.ok(DeveloperApiResult(false, aggregationMsg.developerInvalidAggregationDefinitionError(names))).build()
          }
          else -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build()
        }
      },
      ifRight = { version ->
        Response.ok(DeveloperApiResult(true, devMsg.developerVersionPublishedMessage(), "/ui/developer/apps/$appId/versions/${version.id.value}")).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteDraftVersion(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.version-delete-draft") {
    appVersionManagement.deleteDraftVersion(appId, versionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerDraftVersionDeletedMessage(), "/ui/developer/apps/$appId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addEntity(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @FormParam("name") name: String,
  ): Response = httpResponseMetrics.timed("rest.developer.entity-add") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerEntityNameRequiredError())).build()
    }
    appVersionManagement.addEntity(appId, versionId, name.trim()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { version ->
        val entityId = version.entityDefinitions.find { it.name.equals(name.trim(), ignoreCase = true) }?.id?.value ?: ""
        Response.ok(DeveloperApiResult(true, devMsg.developerEntityAddedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteEntity(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.entity-delete") {
    appVersionManagement.deleteEntity(appId, versionId, entityId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerEntityDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId")).build() },
    )
  }

  /** The synchronous, bounded generation case triggered from the entity editor. */
  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/generate-test-data")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun generateTestData(
    @PathParam("appId") appId: String,
    @PathParam("entityId") entityId: String,
    @FormParam("installedAppId") installedAppId: String,
    @FormParam("count") count: Int,
    @FormParam("seed") seed: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.test-data-generate") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    val seedValue = seed?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
    testDataGenerator.generateTestData(appId, installedAppId, entityId, count, developerId, seedValue).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, testDataGeneratorErrorMessage(error.code))).build() },
      ifRight = { outcome ->
        when (outcome) {
          is TestDataGenerationOutcome.Completed ->
            Response.ok(DeveloperApiResult(true, devMsg.developerGenerateTestDataSuccessMessage(outcome.objects.size))).build()
          is TestDataGenerationOutcome.Enqueued ->
            Response.ok(DeveloperApiResult(true, devMsg.developerGenerateTestDataQueuedMessage(), queued = true)).build()
        }
      },
    )
  }

  /** Polled by the version editor page while a generation run enqueued by [generateTestData] runs in the background. */
  @GET
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/generate-test-data/status")
  @Produces(MediaType.APPLICATION_JSON)
  fun generateTestDataStatus(
    @PathParam("appId") appId: String,
    @PathParam("entityId") entityId: String,
    @QueryParam("installedAppId") installedAppId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.test-data-generate-status") {
    val developerId = currentDeveloperUserIdValue()
      ?: return@timed Response.ok(TestDataGenerationStatusResponse(false)).build()
    Response.ok(TestDataGenerationStatusResponse(testDataGenerator.isGenerating(appId, installedAppId, entityId, developerId))).build()
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/reorder")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun reorderEntities(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    entityIds: List<String>,
  ): Response = httpResponseMetrics.timed("rest.developer.entities-reorder") {
    appVersionManagement.reorderEntities(appId, versionId, entityIds).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerEntitiesReorderedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/sort-criteria")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateEntitySortCriteria(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    sortBy: List<SortCriteriaRequest>,
  ): Response = httpResponseMetrics.timed("rest.developer.entity-sort-criteria-update") {
    val domainSortBy = sortBy.map { req -> SortCriteria(propertyId = req.propertyId, direction = req.direction) }
    appVersionManagement.updateEntitySortCriteria(appId, versionId, entityId, domainSortBy).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerSortCriteriaSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/display-text")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateEntityDisplayText(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @FormParam("displayText") displayText: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.entity-display-text-update") {
    appVersionManagement.updateEntityDisplayText(appId, versionId, entityId, displayText).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerDisplayTextSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/migration-script")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateEntityMigrationScript(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @FormParam("migrationScript") migrationScript: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.entity-migration-script-update") {
    appVersionManagement.updateEntityMigrationScript(appId, versionId, entityId, migrationScript).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerMigrationScriptSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @FormParam("name") name: String,
    @FormParam("type") type: String,
    @FormParam("nullable") nullable: Boolean?,
    @FormParam("targetEntityId") targetEntityId: String?,
    @FormParam("listItemType") listItemType: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-add") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerPropertyNameRequiredError())).build()
    }
    if (type.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerPropertyTypeRequiredError())).build()
    }
    val pathIds = parsePath(path)
    appVersionManagement.addProperty(appId, versionId, entityId, name.trim(), type.trim(), nullable ?: true, targetEntityId, listItemType, pathIds).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { version ->
        val newPropertyId = propertiesAtPathOf(version, entityId, pathIds)?.find { it.name == name.trim() }?.id?.value
        Response.ok(
          DeveloperApiResult(true, devMsg.developerPropertyAddedMessage(), entityEditorUrl(appId, versionId, entityId, path), propertyId = newPropertyId),
        ).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("name") name: String,
    @FormParam("type") type: String,
    @FormParam("nullable") nullable: Boolean?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-update") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerPropertyNameRequiredError())).build()
    }
    if (type.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerPropertyTypeRequiredError())).build()
    }
    appVersionManagement.updateProperty(appId, versionId, entityId, propertyId, name.trim(), type.trim(), nullable ?: true, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerPropertyUpdatedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/constraints")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  @Suppress("LongParameterList", "CyclomaticComplexMethod")
  fun setPropertyConstraints(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("uniqueKey") uniqueKey: Boolean?,
    @FormParam("minLong") minLong: Long?,
    @FormParam("maxLong") maxLong: Long?,
    @FormParam("stepLong") stepLong: Long?,
    @FormParam("minDouble") minDouble: Double?,
    @FormParam("maxDouble") maxDouble: Double?,
    @FormParam("stepDouble") stepDouble: Double?,
    @FormParam("minLength") minLength: Int?,
    @FormParam("maxLength") maxLength: Int?,
    @FormParam("pattern") pattern: String?,
    @FormParam("minSize") minSize: Int?,
    @FormParam("maxSize") maxSize: Int?,
    @FormParam("minDate") minDate: String?,
    @FormParam("maxDate") maxDate: String?,
    @FormParam("minTime") minTime: String?,
    @FormParam("maxTime") maxTime: String?,
    @FormParam("minDatetime") minDatetime: String?,
    @FormParam("maxDatetime") maxDatetime: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-constraints-set") {
    val constraints = mutableSetOf<PropertyConstraint>()
    if (uniqueKey == true) constraints += PropertyConstraint.UniqueKey
    if (minLong != null) constraints += PropertyConstraint.MinLong(minLong)
    if (maxLong != null) constraints += PropertyConstraint.MaxLong(maxLong)
    if (stepLong != null) constraints += PropertyConstraint.StepLong(stepLong)
    if (minDouble != null) constraints += PropertyConstraint.MinDouble(minDouble)
    if (maxDouble != null) constraints += PropertyConstraint.MaxDouble(maxDouble)
    if (stepDouble != null) constraints += PropertyConstraint.StepDouble(stepDouble)
    if (minLength != null) constraints += PropertyConstraint.MinLength(minLength)
    if (maxLength != null) constraints += PropertyConstraint.MaxLength(maxLength)
    if (!pattern.isNullOrBlank()) constraints += PropertyConstraint.Pattern(pattern.trim())
    if (minSize != null) constraints += PropertyConstraint.MinSize(minSize)
    if (maxSize != null) constraints += PropertyConstraint.MaxSize(maxSize)
    parseLocalDate(minDate)?.let { constraints += PropertyConstraint.MinDate(it) }
    parseLocalDate(maxDate)?.let { constraints += PropertyConstraint.MaxDate(it) }
    parseLocalTime(minTime)?.let { constraints += PropertyConstraint.MinTime(it) }
    parseLocalTime(maxTime)?.let { constraints += PropertyConstraint.MaxTime(it) }
    parseLocalDateTime(minDatetime)?.let { constraints += PropertyConstraint.MinDatetime(it) }
    parseLocalDateTime(maxDatetime)?.let { constraints += PropertyConstraint.MaxDatetime(it) }
    appVersionManagement.setPropertyConstraints(appId, versionId, entityId, propertyId, constraints, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerConstraintsSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/default")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyDefault(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("default") default: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-default-set") {
    appVersionManagement.setPropertyDefault(appId, versionId, entityId, propertyId, default, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerDefaultValueSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/smart-default")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertySmartDefault(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("smartDefault") smartDefault: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-smart-default-set") {
    appVersionManagement.setPropertySmartDefault(appId, versionId, entityId, propertyId, smartDefault, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerSmartDefaultSavedMessage(), parentPropertyEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/value-proposals")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyValueProposals(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.developer.property-value-proposals-set") {
    val valueProposals = form["valueProposal"] ?: emptyList()
    val path = form["path"]?.firstOrNull()
    appVersionManagement.setPropertyValueProposals(appId, versionId, entityId, propertyId, valueProposals, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerValueProposalsSavedMessage(), parentPropertyEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/target-entity")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyTargetEntity(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("targetEntityId") targetEntityId: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-target-entity-set") {
    appVersionManagement.setPropertyTargetEntity(appId, versionId, entityId, propertyId, targetEntityId, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerTargetEntitySavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/list-item-type")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyListItemType(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("listItemType") listItemType: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-list-item-type-set") {
    appVersionManagement.setPropertyListItemType(appId, versionId, entityId, propertyId, listItemType, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerListItemTypeSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/unit")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyUnit(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("family") family: String?,
    @FormParam("storageGranularity") storageGranularity: String?,
    @FormParam("defaultGranularity") defaultGranularity: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-unit-set") {
    appVersionManagement.setPropertyUnit(appId, versionId, entityId, propertyId, family, storageGranularity, defaultGranularity, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerUnitSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/item-constraints")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertyItemConstraints(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("itemMinLong") itemMinLong: Long?,
    @FormParam("itemMaxLong") itemMaxLong: Long?,
    @FormParam("itemStepLong") itemStepLong: Long?,
    @FormParam("itemMinDouble") itemMinDouble: Double?,
    @FormParam("itemMaxDouble") itemMaxDouble: Double?,
    @FormParam("itemStepDouble") itemStepDouble: Double?,
    @FormParam("itemMinLength") itemMinLength: Int?,
    @FormParam("itemMaxLength") itemMaxLength: Int?,
    @FormParam("itemPattern") itemPattern: String?,
    @FormParam("itemMinDate") itemMinDate: String?,
    @FormParam("itemMaxDate") itemMaxDate: String?,
    @FormParam("itemMinTime") itemMinTime: String?,
    @FormParam("itemMaxTime") itemMaxTime: String?,
    @FormParam("itemMinDatetime") itemMinDatetime: String?,
    @FormParam("itemMaxDatetime") itemMaxDatetime: String?,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-item-constraints-set") {
    val itemConstraints = mutableSetOf<PropertyConstraint>()
    if (itemMinLong != null) itemConstraints += PropertyConstraint.MinLong(itemMinLong)
    if (itemMaxLong != null) itemConstraints += PropertyConstraint.MaxLong(itemMaxLong)
    if (itemStepLong != null) itemConstraints += PropertyConstraint.StepLong(itemStepLong)
    if (itemMinDouble != null) itemConstraints += PropertyConstraint.MinDouble(itemMinDouble)
    if (itemMaxDouble != null) itemConstraints += PropertyConstraint.MaxDouble(itemMaxDouble)
    if (itemStepDouble != null) itemConstraints += PropertyConstraint.StepDouble(itemStepDouble)
    if (itemMinLength != null) itemConstraints += PropertyConstraint.MinLength(itemMinLength)
    if (itemMaxLength != null) itemConstraints += PropertyConstraint.MaxLength(itemMaxLength)
    if (!itemPattern.isNullOrBlank()) itemConstraints += PropertyConstraint.Pattern(itemPattern.trim())
    parseLocalDate(itemMinDate)?.let { itemConstraints += PropertyConstraint.MinDate(it) }
    parseLocalDate(itemMaxDate)?.let { itemConstraints += PropertyConstraint.MaxDate(it) }
    parseLocalTime(itemMinTime)?.let { itemConstraints += PropertyConstraint.MinTime(it) }
    parseLocalTime(itemMaxTime)?.let { itemConstraints += PropertyConstraint.MaxTime(it) }
    parseLocalDateTime(itemMinDatetime)?.let { itemConstraints += PropertyConstraint.MinDatetime(it) }
    parseLocalDateTime(itemMaxDatetime)?.let { itemConstraints += PropertyConstraint.MaxDatetime(it) }
    appVersionManagement.setPropertyItemConstraints(appId, versionId, entityId, propertyId, itemConstraints, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerItemConstraintsSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/reorder")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun reorderProperties(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.developer.properties-reorder") {
    val propertyIds = form["propertyId"] ?: emptyList()
    val path = form["path"]?.firstOrNull()
    appVersionManagement.reorderProperties(appId, versionId, entityId, propertyIds, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerPropertiesReorderedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/delete")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("path") path: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.property-delete") {
    appVersionManagement.deleteProperty(appId, versionId, entityId, propertyId, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerPropertyDeletedMessage(), parentPropertyEditorUrl(appId, versionId, entityId, path))).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/computed-properties")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @FormParam("name") name: String,
    @FormParam("type") type: String,
  ): Response = httpResponseMetrics.timed("rest.developer.computed-property-add") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerComputedPropertyNameRequiredError())).build()
    }
    appVersionManagement.addComputedProperty(appId, versionId, entityId, name.trim(), type).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertyAddedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
    @FormParam("name") name: String,
    @FormParam("type") type: String,
  ): Response = httpResponseMetrics.timed("rest.developer.computed-property-update") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerComputedPropertyNameRequiredError())).build()
    }
    appVersionManagement.updateComputedProperty(appId, versionId, entityId, computedPropertyId, name.trim(), type).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertyUpdatedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}/script")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setComputedPropertyScript(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
    @FormParam("script") script: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.computed-property-script-set") {
    appVersionManagement.setComputedPropertyScript(appId, versionId, entityId, computedPropertyId, script).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertyScriptSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/reorder")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun reorderComputedProperties(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.developer.computed-properties-reorder") {
    val computedPropertyIds = form["computedPropertyId"] ?: emptyList()
    appVersionManagement.reorderComputedProperties(appId, versionId, entityId, computedPropertyIds).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertiesReorderedMessage())).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/computed-properties/{computedPropertyId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteComputedProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("computedPropertyId") computedPropertyId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.computed-property-delete") {
    appVersionManagement.deleteComputedProperty(appId, versionId, entityId, computedPropertyId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertyDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/aggregations")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.developer.aggregation-add") {
    val input = aggregationInput(form)
    if (input.name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, aggregationMsg.developerAggregationNameRequiredError())).build()
    }
    appVersionManagement.addAggregation(appId, versionId, entityId, input).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, aggregationErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, aggregationMsg.developerAggregationAddedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/aggregations/{aggregationId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("aggregationId") aggregationId: String,
    form: MultivaluedMap<String, String>,
  ): Response = httpResponseMetrics.timed("rest.developer.aggregation-update") {
    val input = aggregationInput(form)
    if (input.name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, aggregationMsg.developerAggregationNameRequiredError())).build()
    }
    appVersionManagement.updateAggregation(appId, versionId, entityId, aggregationId, input).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, aggregationErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, aggregationMsg.developerAggregationUpdatedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/aggregations/{aggregationId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteAggregation(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("aggregationId") aggregationId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.aggregation-delete") {
    appVersionManagement.deleteAggregation(appId, versionId, entityId, aggregationId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, aggregationErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, aggregationMsg.developerAggregationDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  private fun aggregationInput(form: MultivaluedMap<String, String>): AggregationInput = AggregationInput(
    name = form.getFirst("name").orEmpty().trim(),
    function = form.getFirst("function").orEmpty(),
    sourceProperty = form.getFirst("sourceProperty").orEmpty(),
    refPath = form.getFirst("refPath"),
    timeBucket = form.getFirst("timeBucket"),
    timeProperty = form.getFirst("timeProperty"),
    groupBy = form.getFirst("groupBy"),
  )

  private fun aggregationRows(entity: EntityDefinition): List<AggregationEditorRow> {
    fun nameOf(propertyId: String?): String = propertyId?.let { id -> entity.properties.find { it.id.value == id }?.name ?: id }.orEmpty()
    return entity.aggregations.map { aggregation ->
      AggregationEditorRow(
        id = aggregation.id.value,
        name = aggregation.name,
        function = aggregation.function.name,
        functionLabel = aggregationFunctionLabel(aggregation.function),
        sourceProperty = aggregation.sourceProperty.value,
        sourcePropertyName = nameOf(aggregation.sourceProperty.value),
        refPath = aggregation.refPath?.value.orEmpty(),
        timeBucket = aggregation.timeBucket?.name.orEmpty(),
        timeProperty = aggregation.timeProperty?.value.orEmpty(),
        groupBy = aggregation.groupBy?.value.orEmpty(),
        details = aggregationDetails(aggregation, ::nameOf),
      )
    }
  }

  private fun aggregationDetails(aggregation: AggregationDefinition, nameOf: (String?) -> String): String = listOfNotNull(
    aggregation.refPath?.let { aggregationMsg.developerAggregationDetailPerRef(nameOf(it.value)) },
    aggregation.timeBucket?.let { bucket ->
      val bucketText = aggregationMsg.developerAggregationDetailPerTimeBucket(timeBucketLabel(bucket))
      aggregation.timeProperty?.let { "$bucketText (${nameOf(it.value)})" } ?: bucketText
    },
    aggregation.groupBy?.let { aggregationMsg.developerAggregationDetailGroupBy(nameOf(it.value)) },
  ).joinToString(" · ")

  private fun aggregationPropertyOptions(entity: EntityDefinition): List<AggregationPropertyOptionRow> = entity.properties.map {
    AggregationPropertyOptionRow(
      id = it.id.value,
      name = it.name,
      numeric = it.type == PropertyType.LONG || it.type == PropertyType.DOUBLE,
      ref = it.type == PropertyType.REF,
      dateTime = it.type == PropertyType.DATE || it.type == PropertyType.DATETIME,
      groupable = it.type != PropertyType.LIST && it.type != PropertyType.OBJECT,
    )
  }

  private fun aggregationFunctionLabel(function: AggregationFunction): String = when (function) {
    AggregationFunction.SUM -> aggregationMsg.developerAggregationFunctionSum()
    AggregationFunction.COUNT -> aggregationMsg.developerAggregationFunctionCount()
    AggregationFunction.AVG -> aggregationMsg.developerAggregationFunctionAvg()
    AggregationFunction.MIN -> aggregationMsg.developerAggregationFunctionMin()
    AggregationFunction.MAX -> aggregationMsg.developerAggregationFunctionMax()
  }

  private fun timeBucketLabel(timeBucket: TimeBucket): String = when (timeBucket) {
    TimeBucket.TAG -> aggregationMsg.developerAggregationTimeBucketTag()
    TimeBucket.WOCHE -> aggregationMsg.developerAggregationTimeBucketWoche()
    TimeBucket.MONAT -> aggregationMsg.developerAggregationTimeBucketMonat()
    TimeBucket.JAHR -> aggregationMsg.developerAggregationTimeBucketJahr()
  }

  private fun aggregationErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> aggregationMsg.developerAggregationNameRequiredError()
    AppVersionError.AGGREGATION_NOT_FOUND.code -> aggregationMsg.developerAggregationNotFoundError()
    AppVersionError.AGGREGATION_NAME_ALREADY_EXISTS.code -> aggregationMsg.developerAggregationNameExistsError()
    AppVersionError.AGGREGATION_FUNCTION_INVALID.code -> aggregationMsg.developerAggregationFunctionInvalidError()
    AppVersionError.AGGREGATION_SOURCE_PROPERTY_INVALID.code -> aggregationMsg.developerAggregationSourcePropertyInvalidError()
    AppVersionError.AGGREGATION_REF_PATH_INVALID.code -> aggregationMsg.developerAggregationRefPathInvalidError()
    AppVersionError.AGGREGATION_TIME_BUCKET_INVALID.code -> aggregationMsg.developerAggregationTimeBucketInvalidError()
    AppVersionError.AGGREGATION_TIME_PROPERTY_INVALID.code -> aggregationMsg.developerAggregationTimePropertyInvalidError()
    AppVersionError.AGGREGATION_GROUP_BY_INVALID.code -> aggregationMsg.developerAggregationGroupByInvalidError()
    AppVersionError.AGGREGATION_REF_PATH_AND_GROUP_BY_EXCLUSIVE.code -> aggregationMsg.developerAggregationRefPathAndGroupByExclusiveError()
    else -> entityErrorMessage(code)
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/reports")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addReport(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @FormParam("name") name: String,
  ): Response = httpResponseMetrics.timed("rest.developer.report-add") {
    if (name.isBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, devMsg.developerReportNameRequiredError())).build()
    }
    appVersionManagement.addReport(appId, versionId, name.trim()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, reportErrorMessage(error.code))).build() },
      ifRight = { version ->
        val reportId = version.reports.find { it.name.equals(name.trim(), ignoreCase = true) }?.id?.value ?: ""
        Response.ok(DeveloperApiResult(true, devMsg.developerReportAddedMessage(), "/ui/developer/apps/$appId/versions/$versionId/reports/$reportId")).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/reports/{reportId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateReport(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("reportId") reportId: String,
    @FormParam("html") html: String?,
    @FormParam("script") script: String?,
  ): Response = httpResponseMetrics.timed("rest.developer.report-update") {
    appVersionManagement.updateReport(appId, versionId, reportId, html ?: "", script ?: "").fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, reportErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerReportSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/reports/$reportId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/reports/{reportId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteReport(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("reportId") reportId: String,
  ): Response = httpResponseMetrics.timed("rest.developer.report-delete") {
    appVersionManagement.deleteReport(appId, versionId, reportId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, reportErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerReportDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId")).build() },
    )
  }

  private fun currentDeveloperUserIdValue(): String? =
    userProfile.getProfile(securityIdentity.principal.name).getOrNull()?.id?.value

  private fun appErrorMessage(code: String): String = when (code) {
    AppError.BLANK_INPUT.code -> devMsg.developerAppNameRequiredError()
    AppError.APP_NAME_ALREADY_EXISTS.code -> devMsg.developerAppNameExistsError()
    AppError.ALREADY_INACTIVE.code -> devMsg.developerAppAlreadyInactiveError()
    AppError.HAS_ACTIVE_INSTALLATIONS.code -> devMsg.developerAppHasActiveInstallationsError()
    AppError.ALREADY_ACTIVE.code -> devMsg.developerAppAlreadyActiveError()
    AppError.APP_INACTIVE.code -> devMsg.developerAppInactiveError()
    else -> msg.commonUnexpectedError()
  }

  private fun testInstallationErrorMessage(code: String): String = when (code) {
    DeveloperTestInstallationError.APP_NOT_FOUND.code -> devMsg.developerUserNotFoundError()
    DeveloperTestInstallationError.VERSION_NOT_FOUND.code -> devMsg.developerTestInstallationVersionNotFoundError()
    DeveloperTestInstallationError.INSTALLATION_NOT_FOUND.code -> devMsg.developerTestInstallationNotFoundError()
    else -> msg.commonUnexpectedError()
  }

  private fun testDataGeneratorErrorMessage(code: String): String = when (code) {
    TestDataGeneratorError.APP_NOT_FOUND.code -> devMsg.developerUserNotFoundError()
    TestDataGeneratorError.INSTALLATION_NOT_FOUND.code -> devMsg.developerTestInstallationNotFoundError()
    TestDataGeneratorError.ENTITY_NOT_FOUND.code -> devMsg.developerGenerateTestDataEntityNotFoundError()
    TestDataGeneratorError.INVALID_COUNT.code -> devMsg.developerGenerateTestDataInvalidCountError()
    TestDataGeneratorError.GENERATION_FAILED.code -> devMsg.developerGenerateTestDataGenerationFailedError()
    TestDataGeneratorError.ALREADY_GENERATING.code -> devMsg.developerGenerateTestDataAlreadyGeneratingError()
    else -> msg.commonUnexpectedError()
  }

  /** The latest published version of [appId] a draft is compared against, or null if [isDraft] is false or nothing has been published yet. */
  private fun latestPublishedVersionForDraft(appId: String, isDraft: Boolean): AppVersion? =
    if (!isDraft) {
      null
    } else {
      appVersionManagement.listVersions(appId).getOrNull().orEmpty()
        .filter { it.status == AppVersionStatus.PUBLISHED }
        .maxByOrNull { it.createdAt }
    }

  /** Entities of [published] that no longer exist in [draft], shown greyed out in the draft editor so removals stay visible. */
  private fun removedEntities(published: AppVersion?, draft: AppVersion): List<EntityDefinition> {
    if (published == null) return emptyList()
    val draftEntityIds = draft.entityDefinitions.map { it.id }.toSet()
    return published.entityDefinitions.filter { it.id !in draftEntityIds }
  }

  /** Top-level properties of [draftEntity] in [published] that no longer exist in the draft. */
  private fun removedProperties(published: AppVersion?, draftEntity: EntityDefinition?): List<Property> {
    if (published == null || draftEntity == null) return emptyList()
    val publishedEntity = published.entityDefinitions.find { it.id == draftEntity.id } ?: return emptyList()
    val draftPropertyIds = draftEntity.properties.map { it.id }.toSet()
    return publishedEntity.properties.filter { it.id !in draftPropertyIds }
  }

  /** Test installations pinned to [version], the only ones a test data generation run for an entity of this version can target. */
  private fun testInstallationsForVersion(appId: String, version: AppVersion, developerId: String): List<TestInstallationInfo> =
    developerTestInstallations.listTestInstallations(appId, developerId).getOrNull().orEmpty()
      .filter { it.installedVersionId == version.id }
      .map { TestInstallationInfo(installedAppId = it.id.value, version = version, installedAt = it.installedAt) }

  private fun versionErrorMessage(code: String): String = when (code) {
    AppVersionError.INVALID_BUMP_TYPE.code -> devMsg.developerInvalidBumpTypeError()
    AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.code -> devMsg.developerDraftVersionExistsError()
    AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.code -> devMsg.developerVersionNumberExistsError()
    AppVersionError.BLANK_RELEASE_NOTES.code -> devMsg.developerReleaseNotesRequiredError()
    AppVersionError.NO_CHANGES.code -> devMsg.developerNoChangesWarning()
    AppVersionError.INVALID_OBJECT_STRUCTURE.code -> devMsg.developerInvalidObjectStructureGenericError()
    AppVersionError.APP_INACTIVE.code -> devMsg.developerAppInactiveError()
    else -> msg.commonUnexpectedError()
  }

  private fun entityErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> msg.commonNameRequired()
    AppVersionError.ENTITY_NAME_ALREADY_EXISTS.code -> devMsg.developerEntityNameExistsError()
    AppVersionError.ENTITY_NOT_FOUND.code -> devMsg.developerEntityNotFoundError()
    AppVersionError.ENTITY_IDS_MISMATCH.code -> devMsg.developerEntityIdsMismatchError()
    AppVersionError.PROPERTY_NAME_ALREADY_EXISTS.code -> devMsg.developerPropertyNameExistsError()
    AppVersionError.PROPERTY_NOT_FOUND.code -> devMsg.developerPropertyNotFoundError()
    AppVersionError.INVALID_PROPERTY_TYPE.code -> devMsg.developerInvalidPropertyTypeError()
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> devMsg.developerVersionNotInDraftError()
    AppVersionError.DISPLAY_TEXT_USES_NULLABLE_PROPERTY.code -> devMsg.developerDisplayTextNullablePropertyError()
    AppVersionError.DEFAULT_NOT_SUPPORTED.code -> devMsg.developerDefaultNotSupportedError()
    AppVersionError.DEFAULT_VALUE_INVALID.code -> devMsg.developerDefaultValueInvalidError()
    AppVersionError.SMART_DEFAULT_NOT_SUPPORTED.code -> devMsg.developerSmartDefaultNotSupportedError()
    AppVersionError.SMART_DEFAULT_SCRIPT_INVALID.code -> devMsg.developerSmartDefaultScriptInvalidError()
    AppVersionError.VALUE_PROPOSALS_NOT_SUPPORTED.code -> devMsg.developerValueProposalsNotSupportedError()
    AppVersionError.BOTH_DEFAULTS_SET.code -> devMsg.developerBothDefaultsSetError()
    AppVersionError.PROPERTY_IDS_MISMATCH.code -> devMsg.developerPropertyIdsMismatchError()
    AppVersionError.TARGET_ENTITY_NOT_SUPPORTED.code -> devMsg.developerTargetEntityNotSupportedError()
    AppVersionError.TARGET_ENTITY_NOT_FOUND.code -> devMsg.developerTargetEntityNotFoundError()
    AppVersionError.TARGET_ENTITY_REQUIRED.code -> devMsg.developerTargetEntityRequiredError()
    AppVersionError.COMPUTED_PROPERTY_NOT_FOUND.code -> devMsg.developerComputedPropertyNotFoundError()
    AppVersionError.COMPUTED_PROPERTY_NAME_ALREADY_EXISTS.code -> devMsg.developerComputedPropertyNameExistsError()
    AppVersionError.COMPUTED_PROPERTY_TYPE_NOT_SUPPORTED.code -> devMsg.developerComputedPropertyTypeNotSupportedError()
    AppVersionError.LIST_ITEM_TYPE_NOT_SUPPORTED.code -> devMsg.developerListItemTypeNotSupportedError()
    AppVersionError.LIST_ITEM_TYPE_REQUIRED.code -> devMsg.developerListItemTypeRequiredError()
    AppVersionError.LIST_ITEM_TYPE_INVALID.code -> devMsg.developerListItemTypeInvalidError()
    AppVersionError.UNIT_NOT_SUPPORTED.code -> devMsg.developerUnitNotSupportedError()
    AppVersionError.UNIT_FAMILY_INVALID.code -> devMsg.developerUnitFamilyInvalidError()
    AppVersionError.UNIT_GRANULARITY_INVALID.code -> devMsg.developerUnitGranularityInvalidError()
    AppVersionError.APP_INACTIVE.code -> devMsg.developerAppInactiveError()
    else -> msg.commonUnexpectedError()
  }

  private fun parseLocalDate(value: String?): java.time.LocalDate? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

  private fun parseLocalTime(value: String?): java.time.LocalTime? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }

  private fun parseLocalDateTime(value: String?): java.time.LocalDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

  private fun reportErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> msg.commonNameRequired()
    AppVersionError.REPORT_NAME_ALREADY_EXISTS.code -> devMsg.developerReportNameExistsError()
    AppVersionError.REPORT_NOT_FOUND.code -> devMsg.developerReportNotFoundError()
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> devMsg.developerVersionNotInDraftError()
    AppVersionError.APP_INACTIVE.code -> devMsg.developerAppInactiveError()
    else -> msg.commonUnexpectedError()
  }

  /** Parses a comma-joined chain of ancestor OBJECT property IDs into a path, as used to address nested property levels. */
  private fun parsePath(pathParam: String?): List<String> = pathParam?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

  /** Builds the URL for the entity editor at the given nesting path, preserving the `path` query param if non-empty. */
  private fun entityEditorUrl(appId: String, versionId: String, entityId: String, path: String?): String {
    val base = "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId"
    return if (path.isNullOrBlank()) base else "$base?path=$path"
  }

  /**
   * Builds the URL to return to after saving or deleting a property at the given nesting path: the immediate parent
   * OBJECT property's own (merged) editor page, where its nested structure is managed, or the entity editor at top
   * level if the property has no parent.
   */
  private fun parentPropertyEditorUrl(appId: String, versionId: String, entityId: String, path: String?): String {
    val pathIds = parsePath(path)
    if (pathIds.isEmpty()) return entityEditorUrl(appId, versionId, entityId, null)
    val parentId = pathIds.last()
    val parentPath = pathIds.dropLast(1).joinToString(",")
    val base = "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId/properties/$parentId/edit"
    return if (parentPath.isBlank()) base else "$base?path=$parentPath"
  }

  private data class ResolvedPath(val properties: List<Property>, val breadcrumb: List<PropertyBreadcrumb>)

  /** Descends into `entity` following `pathIds`, returning the properties at that level plus the breadcrumb trail. Null if the path is invalid. */
  private fun resolvePath(entity: EntityDefinition, pathIds: List<String>): ResolvedPath? {
    var currentProperties = entity.properties
    val breadcrumb = mutableListOf<PropertyBreadcrumb>()
    val accumulatedIds = mutableListOf<String>()
    for (id in pathIds) {
      val property = currentProperties.find { it.id.value == id && it.type == PropertyType.OBJECT } ?: return null
      accumulatedIds += id
      breadcrumb += PropertyBreadcrumb(id = property.id.value, name = property.name, path = accumulatedIds.joinToString(","))
      currentProperties = property.nestedProperties
    }
    return ResolvedPath(currentProperties, breadcrumb)
  }

  private fun propertiesAtPathOf(version: AppVersion, entityId: String, pathIds: List<String>): List<Property>? {
    val entity = version.entityDefinitions.find { it.id.value == entityId } ?: return null
    return resolvePath(entity, pathIds)?.properties ?: entity.properties.takeIf { pathIds.isEmpty() }
  }

  companion object {
    private val EMPTY_JSON_ARRAY: RawString = RawString("[]")
    private val predefinedSmartDefaultsJson: RawString = RawString(
      ObjectMapper().writeValueAsString(
        PredefinedSmartDefault.byTypeName.mapValues { (_, pds) ->
          pds.map { mapOf("label" to it.label, "script" to it.script) }
        }
      )
    )
  }
}
