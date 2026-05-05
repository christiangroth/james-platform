package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.user.UserProfileServicePort
import io.quarkus.qute.Location
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant

data class DeveloperApiResult(val ok: Boolean, val message: String, val redirectUrl: String? = null, val fieldErrors: Map<String, String>? = null)

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
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userProfile: UserProfileServicePort

  @Inject
  private lateinit var appManagement: AppManagementPort

  @Inject
  private lateinit var appVersionManagement: AppVersionManagementPort

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
      return Response.ok(DeveloperApiResult(false, "App name is required.")).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.ok(DeveloperApiResult(false, "Developer user not found.")).build()
    return appManagement.createApp(name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { app -> Response.ok(DeveloperApiResult(true, "App created.", "/ui/developer/apps/${app.id.value}")).build() },
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
      return Response.ok(DeveloperApiResult(false, "App name is required.")).build()
    }
    val developerId = currentDeveloperUserIdValue()
      ?: return Response.ok(DeveloperApiResult(false, "Developer user not found.")).build()
    return appManagement.updateApp(appId, name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, developerId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "App updated.")).build() },
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
        Response.ok(DeveloperApiResult(true, "Version created.", "/ui/developer/apps/$appId/versions/${version.id.value}")).build()
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
            .data("selectedReport", null),
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
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("hasDiff", hasDiff)
            .data("selectedEntity", selectedEntity)
            .data("selectedReport", null),
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
            .data("selectedReport", selectedReport),
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
        if (error is DisplayTextInvalidError) {
          val names = error.entityNames.joinToString(", ")
          Response.ok(DeveloperApiResult(false, "Invalid display text in: $names. Please fix all display texts before publishing.")).build()
        } else {
          Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build()
        }
      },
      ifRight = { version ->
        Response.ok(DeveloperApiResult(true, "Version published.", "/ui/developer/apps/$appId/versions/${version.id.value}")).build()
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Draft version deleted.", "/ui/developer/apps/$appId")).build() },
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
      return Response.ok(DeveloperApiResult(false, "Entity name is required.")).build()
    }
    return appVersionManagement.addEntity(appId, versionId, name.trim()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { version ->
        val entityId = version.entityDefinitions.find { it.name.equals(name.trim(), ignoreCase = true) }?.id?.value ?: ""
        Response.ok(DeveloperApiResult(true, "Entity added.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build()
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Entity deleted.", "/ui/developer/apps/$appId/versions/$versionId")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Entities reordered.")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Sort criteria saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Display text saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property name is required.")).build()
    }
    if (type.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property type is required.")).build()
    }
    return appVersionManagement.addProperty(appId, versionId, entityId, name.trim(), type.trim(), nullable ?: true).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Property added.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property name is required.")).build()
    }
    if (type.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property type is required.")).build()
    }
    return appVersionManagement.updateProperty(appId, versionId, entityId, propertyId, name.trim(), type.trim(), nullable ?: true).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Property updated.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
    @FormParam("minDouble") minDouble: Double?,
    @FormParam("maxDouble") maxDouble: Double?,
    @FormParam("minLength") minLength: Int?,
    @FormParam("maxLength") maxLength: Int?,
    @FormParam("pattern") pattern: String?,
    @FormParam("minSize") minSize: Int?,
    @FormParam("maxSize") maxSize: Int?,
  ): Response {
    val constraints = mutableSetOf<PropertyConstraint>()
    if (uniqueKey == true) constraints += PropertyConstraint.UniqueKey
    if (minLong != null) constraints += PropertyConstraint.MinLong(minLong)
    if (maxLong != null) constraints += PropertyConstraint.MaxLong(maxLong)
    if (minDouble != null) constraints += PropertyConstraint.MinDouble(minDouble)
    if (maxDouble != null) constraints += PropertyConstraint.MaxDouble(maxDouble)
    if (minLength != null) constraints += PropertyConstraint.MinLength(minLength)
    if (maxLength != null) constraints += PropertyConstraint.MaxLength(maxLength)
    if (!pattern.isNullOrBlank()) constraints += PropertyConstraint.Pattern(pattern.trim())
    if (minSize != null) constraints += PropertyConstraint.MinSize(minSize)
    if (maxSize != null) constraints += PropertyConstraint.MaxSize(maxSize)
    return appVersionManagement.setPropertyConstraints(appId, versionId, entityId, propertyId, constraints).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Constraints saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    return appVersionManagement.setPropertyDefault(appId, versionId, entityId, propertyId, default).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Default value saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteProperty(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
  ): Response {
    return appVersionManagement.deleteProperty(appId, versionId, entityId, propertyId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Property deleted.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
      return Response.ok(DeveloperApiResult(false, "Report name is required.")).build()
    }
    return appVersionManagement.addReport(appId, versionId, name.trim()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, reportErrorMessage(error.code))).build() },
      ifRight = { version ->
        val reportId = version.reports.find { it.name.equals(name.trim(), ignoreCase = true) }?.id?.value ?: ""
        Response.ok(DeveloperApiResult(true, "Report added.", "/ui/developer/apps/$appId/versions/$versionId/reports/$reportId")).build()
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Report saved.", "/ui/developer/apps/$appId/versions/$versionId/reports/$reportId")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Report deleted.", "/ui/developer/apps/$appId/versions/$versionId")).build() },
    )
  }

  private fun currentDeveloperUserIdValue(): String? =
    userProfile.getProfile(securityIdentity.principal.name).getOrNull()?.id?.value

  private fun appErrorMessage(code: String): String = when (code) {
    AppError.BLANK_INPUT.code -> "App name is required."
    AppError.APP_NAME_ALREADY_EXISTS.code -> "An app with this name already exists."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun hasDiffForDraft(appId: String, isDraft: Boolean): Boolean =
    isDraft && (appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()).any { it.status == AppVersionStatus.PUBLISHED }

  private fun versionErrorMessage(code: String): String = when (code) {
    AppVersionError.INVALID_BUMP_TYPE.code -> "Invalid release type. Please try again."
    AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.code -> "A draft version already exists. Publish or delete it before creating a new one."
    AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.code -> "A version with this number already exists."
    AppVersionError.BLANK_RELEASE_NOTES.code -> "Release notes are required."
    AppVersionError.NO_CHANGES.code -> "No changes detected in entities or reports. Please make changes before publishing."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun entityErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Name is required."
    AppVersionError.ENTITY_NAME_ALREADY_EXISTS.code -> "An entity with this name already exists."
    AppVersionError.ENTITY_NOT_FOUND.code -> "Entity not found."
    AppVersionError.ENTITY_IDS_MISMATCH.code -> "Entity IDs do not match the existing entities."
    AppVersionError.PROPERTY_NAME_ALREADY_EXISTS.code -> "A property with this name already exists."
    AppVersionError.PROPERTY_NOT_FOUND.code -> "Property not found."
    AppVersionError.INVALID_PROPERTY_TYPE.code -> "Invalid property type."
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> "Version is not in draft status."
    AppVersionError.DISPLAY_TEXT_USES_NULLABLE_PROPERTY.code -> "Display text may only reference non-nullable properties."
    AppVersionError.DEFAULT_NOT_SUPPORTED.code -> "This property type does not support default values."
    AppVersionError.DEFAULT_VALUE_INVALID.code -> "The default value is invalid for this property type or violates a constraint."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun reportErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Name is required."
    AppVersionError.REPORT_NAME_ALREADY_EXISTS.code -> "A report with this name already exists."
    AppVersionError.REPORT_NOT_FOUND.code -> "Report not found."
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> "Version is not in draft status."
    else -> "An unexpected error occurred. Please try again."
  }
}
