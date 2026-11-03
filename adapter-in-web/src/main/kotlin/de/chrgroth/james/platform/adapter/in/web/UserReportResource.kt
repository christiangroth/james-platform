package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.error.InvalidReportDefinitionError
import de.chrgroth.james.platform.domain.port.`in`.app.ReportPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import io.quarkus.qute.RawString
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import java.net.URI

/**
 * The report page of an installed App (see arc42 "Reports"): the report is executed server-side for this installation only and its
 * renderer-neutral panel spec is embedded into the page, where report-renderer.js renders tabs, rows and panels. Variable selections travel as
 * query parameters `var.<name>`; the renderer reloads the page with the new selection when a variable control changes.
 */
@Path("/ui/user/apps/{installedAppId}/reports")
@ApplicationScoped
@Authenticated
@BlockAdminAccess
@Suppress("Unused")
class UserReportResource(
  private val securityIdentity: SecurityIdentity,
  private val userAppStore: UserAppStorePort,
  private val reports: ReportPort,
  private val reportTexts: ReportTexts,
  private val httpResponseMetrics: HttpResponseMetrics,
) {

  @GET
  @Path("/{reportId}")
  @Produces(MediaType.TEXT_HTML)
  fun report(@PathParam("installedAppId") installedAppId: String, @PathParam("reportId") reportId: String, @Context uriInfo: UriInfo): Response =
    httpResponseMetrics.timed("page.user-report.report") {
      val userId = securityIdentity.principal.name
      val info = userAppStore.getInstalledApp(userId, installedAppId).getOrNull()
        ?: return@timed Response.seeOther(URI.create("/ui/user/dashboard")).build()
      val report = info.installedVersion.reports.find { it.id.value == reportId }
        ?: return@timed Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
      val selections = ApiReportDtoMapper.variableSelections(uriInfo.queryParameters)
      reports.renderReport(userId, installedAppId, reportId, selections).fold(
        ifLeft = { error ->
          val errors = (error as? InvalidReportDefinitionError)?.let { reportTexts.errors(it.errors) }
            ?: return@timed Response.seeOther(URI.create("/ui/user/apps/$installedAppId")).build()
          Response.ok(UserTemplates.`app-report`(info, report.name, RawString("null"), errors)).build()
        },
        ifRight = { view ->
          Response.ok(UserTemplates.`app-report`(info, report.name, reportTexts.json(ApiReportDtoMapper.view(view)), emptyList())).build()
        },
      )
    }
}
