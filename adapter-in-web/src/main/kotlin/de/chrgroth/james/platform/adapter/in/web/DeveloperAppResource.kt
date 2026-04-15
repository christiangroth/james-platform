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
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/ui/developer")
@ApplicationScoped
@Authenticated
@Suppress("Unused")
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
  fun developerDashboard(@QueryParam("error") error: String?) = developerDashboardTemplate
    .data("username", securityIdentity.principal.name)
    .data("apps", appManagement.listApps())
    .data("errorMessage", error?.let { developerDashboardErrorMessage(it) })

  @POST
  @Path("/apps")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun createApp(
    @FormParam("name") name: String?,
    @FormParam("description") description: String?,
  ): Response {
    if (name.isNullOrBlank()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard?error=${AppError.BLANK_INPUT.code}")).build()
    }
    return appManagement.createApp(name.trim(), description?.trim()?.takeIf { it.isNotBlank() }).fold(
      ifLeft = { error -> Response.seeOther(URI.create("/ui/developer/dashboard?error=${error.code}")).build() },
      ifRight = { app -> Response.seeOther(URI.create("/ui/developer/apps/${app.id.value}")).build() },
    )
  }

  @GET
  @Path("/apps/{appId}")
  @Produces(MediaType.TEXT_HTML)
  fun appOverview(
    @PathParam("appId") appId: String,
    @QueryParam("error") error: String?,
  ): Response {
    return appManagement.getApp(appId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/dashboard")).build() },
      ifRight = { app ->
        val versions = appVersionManagement.listVersions(appId).getOrNull() ?: emptyList()
        val hasDraft = versions.any { it.status == AppVersionStatus.DRAFT }
        Response.ok(
          appOverviewTemplate
            .data("app", app)
            .data("versions", versions)
            .data("hasDraft", hasDraft)
            .data("errorMessage", error?.let { appOverviewErrorMessage(it) }),
        ).build()
      },
    )
  }

  @POST
  @Path("/apps/{appId}/versions")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  fun createVersion(
    @PathParam("appId") appId: String,
    @FormParam("versionNumber") versionNumber: String?,
  ): Response {
    if (versionNumber.isNullOrBlank()) {
      return Response.seeOther(URI.create("/ui/developer/apps/$appId?error=${AppVersionError.BLANK_INPUT.code}")).build()
    }
    return appVersionManagement.createVersion(appId, versionNumber.trim()).fold(
      ifLeft = { error -> Response.seeOther(URI.create("/ui/developer/apps/$appId?error=${error.code}")).build() },
      ifRight = { version -> Response.seeOther(URI.create("/ui/developer/apps/$appId/versions/${version.id.value}")).build() },
    )
  }

  @GET
  @Path("/apps/{appId}/versions/{versionId}")
  @Produces(MediaType.TEXT_HTML)
  fun versionEditor(
    @PathParam("appId") appId: String,
    @PathParam("versionId") versionId: String,
    @QueryParam("entity") entityId: String?,
    @QueryParam("report") reportId: String?,
  ): Response {
    val appResult = appManagement.getApp(appId)
    if (appResult.isLeft()) {
      return Response.seeOther(URI.create("/ui/developer/dashboard")).build()
    }
    val app = appResult.getOrNull()!!
    return appVersionManagement.getVersion(appId, versionId).fold(
      ifLeft = { Response.seeOther(URI.create("/ui/developer/apps/$appId")).build() },
      ifRight = { version ->
        val selectedEntity = if (entityId != null) version.entityDefinitions.find { it.id.value == entityId } else null
        val selectedReport = if (reportId != null) version.reports.find { it.id.value == reportId } else null
        val isDraft = version.status == AppVersionStatus.DRAFT
        Response.ok(
          versionEditorTemplate
            .data("app", app)
            .data("version", version)
            .data("isDraft", isDraft)
            .data("selectedEntity", selectedEntity)
            .data("selectedReport", selectedReport),
        ).build()
      },
    )
  }

  private fun developerDashboardErrorMessage(code: String): String = when (code) {
    AppError.BLANK_INPUT.code -> "App name is required."
    AppError.APP_NAME_ALREADY_EXISTS.code -> "An app with this name already exists."
    else -> "An unexpected error occurred. Please try again."
  }

  private fun appOverviewErrorMessage(code: String): String = when (code) {
    AppVersionError.BLANK_INPUT.code -> "Version number is required."
    AppVersionError.INVALID_VERSION_NUMBER_FORMAT.code -> "Invalid version number. Must follow semantic versioning (e.g. 1.0.0)."
    AppVersionError.DRAFT_VERSION_ALREADY_EXISTS.code -> "A draft version already exists. Publish or delete it before creating a new one."
    AppVersionError.VERSION_NUMBER_ALREADY_EXISTS.code -> "A version with this number already exists."
    else -> "An unexpected error occurred. Please try again."
  }
}
