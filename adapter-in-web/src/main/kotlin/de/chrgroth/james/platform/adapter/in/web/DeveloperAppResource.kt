package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.error.InvalidObjectStructureError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PredefinedSmartDefault
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.Response
import java.net.URI
import java.time.Instant
import java.util.UUID

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

/** Recursive request shape for defining the structure of an OBJECT property, mirroring the fields of a top-level property. */
data class NestedPropertyRequest @JsonCreator constructor(
  @param:JsonProperty("id") val id: String? = null,
  @param:JsonProperty("name") val name: String,
  @param:JsonProperty("type") val type: String,
  @param:JsonProperty("nullable") val nullable: Boolean = true,
  @param:JsonProperty("targetEntityId") val targetEntityId: String? = null,
  @param:JsonProperty("listItemType") val listItemType: String? = null,
  @param:JsonProperty("nestedProperties") val nestedProperties: List<NestedPropertyRequest> = emptyList(),
) {
  fun toProperty(): Property {
    val propertyType = runCatching { PropertyType.valueOf(type.trim().uppercase()) }.getOrDefault(PropertyType.STRING)
    return Property(
      id = PropertyId(id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()),
      name = name.trim(),
      type = propertyType,
      nullable = nullable,
      targetEntityId = targetEntityId?.takeIf { it.isNotBlank() }?.let { EntityDefinitionId(it) },
      listItemType = listItemType?.takeIf { it.isNotBlank() }?.let { runCatching { PropertyType.valueOf(it.trim().uppercase()) }.getOrNull() },
      nestedProperties = if (propertyType == PropertyType.OBJECT) nestedProperties.map { it.toProperty() } else emptyList(),
    )
  }
}

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
            .data("selectedReport", null)
            .data("predefinedSmartDefaultsJson", predefinedSmartDefaultsJson)
            .data("entityPropertiesJson", EMPTY_JSON_ARRAY)
            .data("nestedPropertiesJson", EMPTY_JSON_OBJECT),
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
        val entityPropertiesJson = RawString(
          ObjectMapper().writeValueAsString(
            selectedEntity?.properties?.map { mapOf("id" to it.id.value, "name" to it.name) } ?: emptyList<Any>()
          )
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
            .data("entityPropertiesJson", entityPropertiesJson)
            .data("nestedPropertiesJson", nestedPropertiesJsonFor(selectedEntity)),
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
            .data("entityPropertiesJson", EMPTY_JSON_ARRAY)
            .data("nestedPropertiesJson", EMPTY_JSON_OBJECT),
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
            Response.ok(DeveloperApiResult(false, "Invalid display text in: $names. Please fix all display texts before publishing.")).build()
          }
          is InvalidObjectStructureError -> {
            val names = error.entityNames.joinToString(", ")
            Response.ok(DeveloperApiResult(false, "Invalid Object properties in: $names. Every Object property needs at least one nested property.")).build()
          }
          else -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build()
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
    @FormParam("targetEntityId") targetEntityId: String?,
    @FormParam("listItemType") listItemType: String?,
  ): Response {
    if (name.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property name is required.")).build()
    }
    if (type.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Property type is required.")).build()
    }
    return appVersionManagement.addProperty(appId, versionId, entityId, name.trim(), type.trim(), nullable ?: true, targetEntityId, listItemType).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { version ->
        val newPropertyId = version.entityDefinitions.find { it.id.value == entityId }?.properties?.find { it.name == name.trim() }?.id?.value
        Response.ok(
          DeveloperApiResult(true, "Property added.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId", propertyId = newPropertyId),
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
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/smart-default")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun setPropertySmartDefault(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    @FormParam("smartDefault") smartDefault: String?,
  ): Response {
    return appVersionManagement.setPropertySmartDefault(appId, versionId, entityId, propertyId, smartDefault).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Smart default saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
    return appVersionManagement.setPropertyValueProposals(appId, versionId, entityId, propertyId, valueProposals).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Value proposals saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    return appVersionManagement.setPropertyTargetEntity(appId, versionId, entityId, propertyId, targetEntityId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Target entity saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
  ): Response {
    return appVersionManagement.setPropertyListItemType(appId, versionId, entityId, propertyId, listItemType).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "List item type saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
    return appVersionManagement.setPropertyItemConstraints(appId, versionId, entityId, propertyId, itemConstraints).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Item constraints saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/{versionId}/entities/{entityId}/properties/{propertyId}/nested-properties")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  fun setNestedProperties(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @PathParam("entityId") entityId: String,
    @PathParam("propertyId") propertyId: String,
    nestedProperties: List<NestedPropertyRequest>,
  ): Response = appVersionManagement.setNestedProperties(appId, versionId, entityId, propertyId, nestedProperties.map { it.toProperty() }).fold(
    ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
    ifRight = { Response.ok(DeveloperApiResult(true, "Nested properties saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
  )

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
    return appVersionManagement.reorderProperties(appId, versionId, entityId, propertyIds).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Properties reordered.")).build() },
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
      return Response.ok(DeveloperApiResult(false, "Computed property name is required.")).build()
    }
    return appVersionManagement.addComputedProperty(appId, versionId, entityId, name.trim(), type).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Computed property added.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
      return Response.ok(DeveloperApiResult(false, "Computed property name is required.")).build()
    }
    return appVersionManagement.updateComputedProperty(appId, versionId, entityId, computedPropertyId, name.trim(), type).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, entityErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, "Computed property updated.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Computed property script saved.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Computed properties reordered.")).build() },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Computed property deleted.", "/ui/developer/apps/$appId/versions/$versionId/entities/$entityId")).build() },
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
    AppVersionError.INVALID_OBJECT_STRUCTURE.code -> "Some Object properties have no nested properties defined. Please fix all Object properties before publishing."
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
    AppVersionError.SMART_DEFAULT_NOT_SUPPORTED.code -> "This property type does not support smart defaults."
    AppVersionError.SMART_DEFAULT_SCRIPT_INVALID.code -> "The smart default script is invalid."
    AppVersionError.VALUE_PROPOSALS_NOT_SUPPORTED.code -> "Value proposals are only supported for String properties."
    AppVersionError.BOTH_DEFAULTS_SET.code -> "A property cannot have both a default value and a smart default. Please clear one before setting the other."
    AppVersionError.PROPERTY_IDS_MISMATCH.code -> "Property IDs do not match the existing properties."
    AppVersionError.TARGET_ENTITY_NOT_SUPPORTED.code -> "Only Reference properties support a target entity."
    AppVersionError.TARGET_ENTITY_NOT_FOUND.code -> "Target entity not found."
    AppVersionError.TARGET_ENTITY_REQUIRED.code -> "Target entity is required for Reference properties."
    AppVersionError.COMPUTED_PROPERTY_NOT_FOUND.code -> "Computed property not found."
    AppVersionError.COMPUTED_PROPERTY_NAME_ALREADY_EXISTS.code -> "A computed property with this name already exists."
    AppVersionError.COMPUTED_PROPERTY_TYPE_NOT_SUPPORTED.code -> "This type does not support computed properties."
    AppVersionError.LIST_ITEM_TYPE_NOT_SUPPORTED.code -> "Only List properties support a list item type."
    AppVersionError.LIST_ITEM_TYPE_REQUIRED.code -> "An item type is required for List properties."
    AppVersionError.LIST_ITEM_TYPE_INVALID.code -> "Invalid list item type."
    AppVersionError.NESTED_PROPERTIES_NOT_SUPPORTED.code -> "Only Object properties support nested properties."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun parseLocalDate(value: String?): java.time.LocalDate? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

  private fun parseLocalTime(value: String?): java.time.LocalTime? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }

  private fun parseLocalDateTime(value: String?): java.time.LocalDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

  private fun parseDuration(value: String?): java.time.Duration? = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.Duration.parse(it) }.getOrNull() }

  private fun reportErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Name is required."
    AppVersionError.REPORT_NAME_ALREADY_EXISTS.code -> "A report with this name already exists."
    AppVersionError.REPORT_NOT_FOUND.code -> "Report not found."
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> "Version is not in draft status."
    else -> "An unexpected error occurred. Please try again."
  }

  /** Recursive view of an OBJECT property's nested structure, for embedding into the version editor page as JSON. */
  private fun Property.toNestedPropertyView(): Map<String, Any?> = mapOf(
    "id" to id.value,
    "name" to name,
    "type" to type.name,
    "nullable" to nullable,
    "targetEntityId" to targetEntityId?.value,
    "listItemType" to listItemType?.name,
    "nestedProperties" to nestedProperties.map { it.toNestedPropertyView() },
  )

  private fun nestedPropertiesJsonFor(entity: EntityDefinition?): RawString = RawString(
    ObjectMapper().writeValueAsString(
      entity?.properties?.filter { it.type == PropertyType.OBJECT }?.associate { it.id.value to it.toNestedPropertyView() } ?: emptyMap<String, Any>(),
    ),
  )

  companion object {
    private val EMPTY_JSON_ARRAY: RawString = RawString("[]")
    private val EMPTY_JSON_OBJECT: RawString = RawString("{}")
    private val predefinedSmartDefaultsJson: RawString = RawString(
      ObjectMapper().writeValueAsString(
        PredefinedSmartDefault.byTypeName.mapValues { (_, pds) ->
          pds.map { mapOf("label" to it.label, "script" to it.script) }
        }
      )
    )
  }
}
