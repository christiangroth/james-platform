package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
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

data class DeveloperApiResult(val ok: Boolean, val message: String, val redirectUrl: String? = null)

@Path("/ui/developer")
@ApplicationScoped
@Authenticated
@Suppress("Unused", "TooManyFunctions")
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
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var appManagement: AppManagementPort

  @Inject
  private lateinit var appVersionManagement: AppVersionManagementPort

  @GET
  @Path("/dashboard")
  @Produces(MediaType.TEXT_HTML)
  fun developerDashboard() = developerDashboardTemplate
    .data("username", securityIdentity.principal.name)
    .data("apps", appManagement.listApps(securityIdentity.principal.name))

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
    return appManagement.createApp(name.trim(), description?.trim()?.takeIf { it.isNotBlank() }, securityIdentity.principal.name).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, appErrorMessage(error.code))).build() },
      ifRight = { app -> Response.ok(DeveloperApiResult(true, "App created.", "/ui/developer/apps/${app.id.value}")).build() },
    )
  }

  @GET
  @Path("/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appOverview(@PathParam("appId") appId: String): Response {
    return appManagement.getApp(appId, securityIdentity.principal.name).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/dashboard")).build() },
      ifRight = { app ->
        val versions = appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()
        val hasDraft = versions.any { it.status == AppVersionStatus.DRAFT }
        Response.ok(
          appOverviewTemplate
            .data("app", app)
            .data("versions", versions)
            .data("hasDraft", hasDraft),
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
    val appResult = appManagement.getApp(appId, securityIdentity.principal.name)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
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
    val appResult = appManagement.getApp(appId, securityIdentity.principal.name)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        val selectedEntity = version.entityDefinitions.find { it.id.value == entityId }
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
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
    val appResult = appManagement.getApp(appId, securityIdentity.principal.name)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val isDraft = version.status == AppVersionStatus.DRAFT
        val selectedReport = version.reports.find { it.id.value == reportId }
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("selectedEntity", null)
            .data("selectedReport", selectedReport),
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
      ifRight = { bump -> Response.ok(bump).build() },
    )
  }

  @POST
  @Path("/apps/{appId}/versions/publish")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun publishVersion(
    @PathParam("appId") appId: String,
    @FormParam("versionNumber") versionNumber: String,
  ): Response {
    if (versionNumber.isBlank()) {
      return Response.ok(DeveloperApiResult(false, "Version number is required.")).build()
    }
    return appVersionManagement.publishVersion(appId, versionNumber.trim()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, versionErrorMessage(error.code))).build() },
      ifRight = { version ->
        Response.ok(DeveloperApiResult(true, "Version published.", "/ui/developer/apps/$appId/versions/${version.id.value}")).build()
      },
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
      ifRight = { Response.ok(DeveloperApiResult(true, "Report saved.")).build() },
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

  private fun appErrorMessage(code: String): String = when (code) {
    AppError.BLANK_INPUT.code -> "App name is required."
    AppError.APP_NAME_ALREADY_EXISTS.code -> "An app with this name already exists."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun versionErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Version number is required."
    AppVersionError.INVALID_VERSION_NUMBER_FORMAT.code -> "Invalid version number. Must follow semantic versioning (e.g. 1.0.0)."
    AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.code -> "A draft version already exists. Publish or delete it before creating a new one."
    AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.code -> "A version with this number already exists."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun entityErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Name is required."
    AppVersionError.ENTITY_NAME_ALREADY_EXISTS.code -> "An entity with this name already exists."
    AppVersionError.ENTITY_NOT_FOUND.code -> "Entity not found."
    AppVersionError.PROPERTY_NAME_ALREADY_EXISTS.code -> "A property with this name already exists."
    AppVersionError.PROPERTY_NOT_FOUND.code -> "Property not found."
    AppVersionError.INVALID_PROPERTY_TYPE.code -> "Invalid property type."
    AppVersionError.VERSION_NOT_IN_DRAFT.code -> "Version is not in draft status."
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
