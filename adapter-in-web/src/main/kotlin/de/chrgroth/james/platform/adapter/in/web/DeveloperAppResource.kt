package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.DeveloperMessages
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.error.InvalidObjectStructureError
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
import de.chrgroth.james.platform.domain.model.app.parseDurationValue
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import io.quarkus.qute.Location
import io.quarkus.qute.RawString
import io.quarkus.qute.Template
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
import kotlin.time.toJavaDuration

data class DeveloperApiResult(
  val ok: Boolean,
  val message: String,
  val redirectUrl: String? = null,
  val fieldErrors: Map<String, String>? = null,
  val propertyId: String? = null,
)

data class DashboardAppInfo(
  val app: App,
  val hasDraft: Boolean,
  val latestVersionNumber: String?,
  val latestVersionPublishedAt: Instant?,
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
  @Location("ui/developer/dashboard.html")
  private lateinit var developerDashboardTemplate: Template

  @Inject
  @Location("ui/developer/app-overview.html")
  private lateinit var appOverviewTemplate: Template

  @Inject
  @Location("ui/developer/version-editor.html")
  private lateinit var versionEditorTemplate: Template

  @Inject
  @Location("ui/developer/version-diff.html")
  private lateinit var versionDiffTemplate: Template

  @Inject
  @Location("ui/developer/edit-property.html")
  private lateinit var editPropertyTemplate: Template

  @Inject
  @Location("ui/developer/publish-version.html")
  private lateinit var publishVersionTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userProfile: UserProfileServicePort

  @Inject
  private lateinit var appManagement: AppManagementPort

  @Inject
  private lateinit var appVersionManagement: AppVersionManagementPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var devMsg: DeveloperMessages

  @GET
  @Path("/dashboard")
  @Produces(MediaType.TEXT_HTML)
  fun developerDashboard(): Any {
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
    return developerDashboardTemplate
      .data("username", username)
      .data("apps", appInfos)
  }

  @POST
  @Path("/apps")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createApp(
    @FormParam("name") name: String,
    @FormParam("description") description: String?,
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerAppNameRequiredError())).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    return appManagement.createApp(name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerAppNameRequiredError())).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.ok(DeveloperApiResult(false, devMsg.developerUserNotFoundError())).build()
    return appManagement.updateApp(appId, name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerAppUpdatedMessage())).build() },
    )
  }

  @GET
  @Path("/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appOverview(@PathParam("appId") appId: String): Response {
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    return appManagement.getApp(appId, developerId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/dashboard")).build() },
      ifRight = { app ->
        val versions = appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()
        val hasDraft = versions.any { it.status == AppVersionStatus.DRAFT }
        val publishedByDate = versions.filter { it.status == AppVersionStatus.PUBLISHED }.sortedBy { it.createdAt }
        val publishedIdsWithPredecessor = if (publishedByDate.size > 1) publishedByDate.drop(1).map { it.id.value }.toSet() else emptySet<String>()
        val draftIdWithDiff = if (hasDraft && publishedByDate.isNotEmpty()) setOf(versions.first { it.status == AppVersionStatus.DRAFT }.id.value) else emptySet<String>()
        val versionIdsWithPredecessor = publishedIdsWithPredecessor + draftIdWithDiff
        Response.ok(
          appOverviewTemplate
            .data("app", app)
            .data("versions", versions)
            .data("hasDraft", hasDraft)
            .data("versionsWithDiff", versionIdsWithPredecessor),
        ).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions")
  @Produces(MediaType.APPLICATION_JSON)
  fun createVersion(@PathParam("appId") appId: String): Response {
    return appVersionManagement.createVersion(appId).fold(
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
        val isDraft = version.status == AppVersionStatus.DRAFT
        val hasDiff = hasDiffForDraft(appId, isDraft)
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("hasDiff", hasDiff)
            .data("selectedEntity", null)
            .data("selectedReport", null)
            .data("predefinedSmartDefaultsJson", predefinedSmartDefaultsJson)
            .data("currentProperties", emptyList<Property>())
            .data("currentPropertiesJson", EMPTY_JSON_ARRAY)
            .data("path", "")
            .data("breadcrumb", emptyList<PropertyBreadcrumb>())
            .data("isNestedLevel", false),
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
        val isDraft = version.status == AppVersionStatus.DRAFT
        val hasDiff = hasDiffForDraft(appId, isDraft)
        val selectedEntity = version.entityDefinitions.find { it.id.value == entityId }
        val pathIds = parsePath(pathParam)
        val resolved = selectedEntity?.let { resolvePath(it, pathIds) }
        val currentProperties = resolved?.properties ?: selectedEntity?.properties ?: emptyList()
        val breadcrumb = resolved?.breadcrumb ?: emptyList()
        val currentPropertiesJson = RawString(
          ObjectMapper().writeValueAsString(currentProperties.map { mapOf("id" to it.id.value, "name" to it.name) }),
        )
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("hasDiff", hasDiff)
            .data("selectedEntity", selectedEntity)
            .data("selectedReport", null)
            .data("predefinedSmartDefaultsJson", predefinedSmartDefaultsJson)
            .data("currentProperties", currentProperties)
            .data("currentPropertiesJson", currentPropertiesJson)
            .data("path", breadcrumb.lastOrNull()?.path ?: "")
            .data("breadcrumb", breadcrumb)
            .data("isNestedLevel", breadcrumb.isNotEmpty()),
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
  ): Response = editPropertyPage(appId, versionId, entityId, null, pathParam)

  @GET
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/edit")
  @Produces(MediaType.TEXT_HTML)
  fun editProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @QueryParam("path") pathParam: String?,
  ): Response = editPropertyPage(appId, versionId, entityId, propertyId, pathParam)

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
          editPropertyTemplate
            .data("app", app)
            .data("version", version)
            .data("selectedEntity", selectedEntity)
            .data("selectedProperty", selectedProperty)
            .data("path", breadcrumb.lastOrNull()?.path ?: "")
            .data("breadcrumb", breadcrumb)
            .data("parentPropertyId", parentPropertyId)
            .data("parentPath", parentPath)
            .data("predefinedSmartDefaultsJson", predefinedSmartDefaultsJson)
            .data("currentPropertiesJson", currentPropertiesJson),
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
        Response.ok(
          publishVersionTemplate
            .data("app", app)
            .data("version", version),
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
        val isDraft = version.status == AppVersionStatus.DRAFT
        val hasDiff = hasDiffForDraft(appId, isDraft)
        val selectedReport = version.reports.find { it.id.value == reportId }
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("hasDiff", hasDiff)
            .data("selectedEntity", null)
            .data("selectedReport", selectedReport)
            .data("predefinedSmartDefaultsJson", predefinedSmartDefaultsJson)
            .data("currentProperties", emptyList<Property>())
            .data("currentPropertiesJson", EMPTY_JSON_ARRAY)
            .data("path", "")
            .data("breadcrumb", emptyList<PropertyBreadcrumb>())
            .data("isNestedLevel", false),
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
  ): Response {
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    val appResult = appManagement.getApp(appId, developerId)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersionDiff(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { diff ->
        Response.ok(
          versionDiffTemplate
            .data("app", app)
            .data("diff", diff),
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
  ): Response {
    return appVersionManagement.computeVersionBump(appId, versionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build() },
      ifRight = { bump ->
        Response.ok(
          VersionBumpResponse(
            hasBreakingChanges = bump.hasBreakingChanges,
            hasChanges = bump.hasChanges,
            suggestedVersionOnBreaking = bump.suggestedVersionOnBreaking.value,
            suggestedVersionOnFeature = bump.suggestedVersionOnFeature.value,
            suggestedVersionOnBugfix = bump.suggestedVersionOnBugfix.value,
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
  ): Response {
    return appVersionManagement.publishVersion(appId, bumpType, releaseNotes.orEmpty()).fold(
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
  ): Response {
    return appVersionManagement.deleteDraftVersion(appId, versionId).fold(
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerEntityNameRequiredError())).build()
    }
    return appVersionManagement.addEntity(appId, versionId, name.trim()).fold(
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
  ): Response {
    return appVersionManagement.deleteEntity(appId, versionId, entityId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerEntityDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/reorder")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun reorderEntities(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    entityIds: List<String>,
  ): Response {
    return appVersionManagement.reorderEntities(appId, versionId, entityIds).fold(
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
  ): Response {
    val domainSortBy = sortBy.map { req -> SortCriteria(propertyId = req.propertyId, direction = req.direction) }
    return appVersionManagement.updateEntitySortCriteria(appId, versionId, entityId, domainSortBy).fold(
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
  ): Response {
    return appVersionManagement.updateEntityDisplayText(appId, versionId, entityId, displayText).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerDisplayTextSavedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerPropertyNameRequiredError())).build()
    }
    if (type.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerPropertyTypeRequiredError())).build()
    }
    val pathIds = parsePath(path)
    return appVersionManagement.addProperty(appId, versionId, entityId, name.trim(), type.trim(), nullable ?: true, targetEntityId, listItemType, pathIds).fold(
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerPropertyNameRequiredError())).build()
    }
    if (type.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerPropertyTypeRequiredError())).build()
    }
    return appVersionManagement.updateProperty(appId, versionId, entityId, propertyId, name.trim(), type.trim(), nullable ?: true, parsePath(path)).fold(
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
    @FormParam("minDuration") minDuration: String?,
    @FormParam("maxDuration") maxDuration: String?,
    @FormParam("path") path: String?,
  ): Response {
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
    parseDuration(minDuration)?.let { constraints += PropertyConstraint.MinDuration(it) }
    parseDuration(maxDuration)?.let { constraints += PropertyConstraint.MaxDuration(it) }
    return appVersionManagement.setPropertyConstraints(appId, versionId, entityId, propertyId, constraints, parsePath(path)).fold(
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
  ): Response {
    return appVersionManagement.setPropertyDefault(appId, versionId, entityId, propertyId, default, parsePath(path)).fold(
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
  ): Response {
    return appVersionManagement.setPropertySmartDefault(appId, versionId, entityId, propertyId, smartDefault, parsePath(path)).fold(
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
  ): Response {
    val valueProposals = form["valueProposal"] ?: emptyList()
    val path = form["path"]?.firstOrNull()
    return appVersionManagement.setPropertyValueProposals(appId, versionId, entityId, propertyId, valueProposals, parsePath(path)).fold(
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
  ): Response {
    return appVersionManagement.setPropertyTargetEntity(appId, versionId, entityId, propertyId, targetEntityId, parsePath(path)).fold(
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
  ): Response {
    return appVersionManagement.setPropertyListItemType(appId, versionId, entityId, propertyId, listItemType, parsePath(path)).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerListItemTypeSavedMessage(), entityEditorUrl(appId, versionId, entityId, path))).build() },
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
    @FormParam("itemMinDuration") itemMinDuration: String?,
    @FormParam("itemMaxDuration") itemMaxDuration: String?,
    @FormParam("path") path: String?,
  ): Response {
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
    parseDuration(itemMinDuration)?.let { itemConstraints += PropertyConstraint.MinDuration(it) }
    parseDuration(itemMaxDuration)?.let { itemConstraints += PropertyConstraint.MaxDuration(it) }
    return appVersionManagement.setPropertyItemConstraints(appId, versionId, entityId, propertyId, itemConstraints, parsePath(path)).fold(
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
  ): Response {
    val propertyIds = form["propertyId"] ?: emptyList()
    val path = form["path"]?.firstOrNull()
    return appVersionManagement.reorderProperties(appId, versionId, entityId, propertyIds, parsePath(path)).fold(
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
  ): Response {
    return appVersionManagement.deleteProperty(appId, versionId, entityId, propertyId, parsePath(path)).fold(
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerComputedPropertyNameRequiredError())).build()
    }
    return appVersionManagement.addComputedProperty(appId, versionId, entityId, name.trim(), type).fold(
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerComputedPropertyNameRequiredError())).build()
    }
    return appVersionManagement.updateComputedProperty(appId, versionId, entityId, computedPropertyId, name.trim(), type).fold(
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
  ): Response {
    return appVersionManagement.setComputedPropertyScript(appId, versionId, entityId, computedPropertyId, script).fold(
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
  ): Response {
    val computedPropertyIds = form["computedPropertyId"] ?: emptyList()
    return appVersionManagement.reorderComputedProperties(appId, versionId, entityId, computedPropertyIds).fold(
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
  ): Response {
    return appVersionManagement.deleteComputedProperty(appId, versionId, entityId, computedPropertyId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerComputedPropertyDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/reports")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun addReport(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @FormParam("name") name: String,
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, devMsg.developerReportNameRequiredError())).build()
    }
    return appVersionManagement.addReport(appId, versionId, name.trim()).fold(
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
  ): Response {
    return appVersionManagement.updateReport(appId, versionId, reportId, html ?: "", script ?: "").fold(
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
  ): Response {
    return appVersionManagement.deleteReport(appId, versionId, reportId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, reportErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, devMsg.developerReportDeletedMessage(), "/ui/developer/apps/$appId/versions/$versionId")).build() },
    )
  }

  private fun currentDeveloperUserIdValue(): String? =
    userProfile.getProfile(securityIdentity.principal.name).getOrNull()?.id?.value

  private fun appErrorMessage(code: String): String = when (code) {
    AppError.BLANK_INPUT.code -> devMsg.developerAppNameRequiredError()
    AppError.APP_NAME_ALREADY_EXISTS.code -> devMsg.developerAppNameExistsError()
    else -> msg.commonUnexpectedError()
  }

  private fun hasDiffForDraft(appId: String, isDraft: Boolean): Boolean =
    isDraft && (appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()).any { it.status == AppVersionStatus.PUBLISHED }

  private fun versionErrorMessage(code: String): String = when (code) {
    AppVersionError.INVALID_BUMP_TYPE.code -> devMsg.developerInvalidBumpTypeError()
    AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.code -> devMsg.developerDraftVersionExistsError()
    AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.code -> devMsg.developerVersionNumberExistsError()
    AppVersionError.BLANK_RELEASE_NOTES.code -> devMsg.developerReleaseNotesRequiredError()
    AppVersionError.NO_CHANGES.code -> devMsg.developerNoChangesWarning()
    AppVersionError.INVALID_OBJECT_STRUCTURE.code -> devMsg.developerInvalidObjectStructureGenericError()
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
    else -> msg.commonUnexpectedError()
  }

  private fun parseLocalDate(value: String?): java.time.LocalDate? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

  private fun parseLocalTime(value: String?): java.time.LocalTime? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }

  private fun parseLocalDateTime(value: String?): java.time.LocalDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

  private fun parseDuration(value: String?): java.time.Duration? =
    value?.takeIf { it.isNotBlank() }?.let { parseDurationValue(it) }?.toJavaDuration()

  private fun reportErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> msg.commonNameRequired()
    AppVersionError.REPORT_NAME_ALREADY_EXISTS.code -> devMsg.developerReportNameExistsError()
    AppVersionError.REPORT_NOT_FOUND.code -> devMsg.developerReportNotFoundError()
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> devMsg.developerVersionNotInDraftError()
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
