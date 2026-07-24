package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.security.identity.SecurityIdentity
import jakarta.annotation.security.RolesAllowed
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

data class ImportDocumentRow(
  val id: String,
  val statusLabel: String,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

@Path("/ui/user/apps/{installedAppId}/imports")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportResource {

  @Inject
  @Location("ui/user/app-imports.html")
  private lateinit var importsTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var userAppStore: UserAppStorePort

  @Inject
  private lateinit var importPort: ImportPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun imports(@PathParam("installedAppId") installedAppId: String): Response {
    val userId = securityIdentity.principal.name
    val info = userAppStore.getInstalledApp(userId, installedAppId).fold(
      ifLeft = { return Response.seeOther(URI.create("/ui/user/dashboard")).build() },
      ifRight = { it },
    )
    return Response.ok(
      importsTemplate
        .data("info", info)
        .data("documents", loadRows(userId, installedAppId)),
    ).build()
  }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun importsTable(@PathParam("installedAppId") installedAppId: String): Any {
    val userId = securityIdentity.principal.name
    return importsTemplate.getFragment("imports_table")
      .data("documents", loadRows(userId, installedAppId))
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun triggerImport(
    @PathParam("installedAppId") installedAppId: String,
    @FormParam("sourceUrl") sourceUrl: String?,
    @FormParam("bearerToken") bearerToken: String?,
  ): Response {
    val userId = securityIdentity.principal.name
    if (sourceUrl.isNullOrBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportUrlRequiredError())).build()
    }
    if (bearerToken.isNullOrBlank()) {
      return Response.ok(DeveloperApiResult(false, userMsg.userImportTokenRequiredError())).build()
    }
    return importPort.triggerImport(userId, installedAppId, sourceUrl, bearerToken).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportCreatedMessage())).build() },
    )
  }

  @POST
  @Path("/{importDocumentId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteImport(
    @PathParam("installedAppId") installedAppId: String,
    @PathParam("importDocumentId") importDocumentId: String,
  ): Response {
    val userId = securityIdentity.principal.name
    return importPort.deleteImportDocument(userId, installedAppId, importDocumentId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, importErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportDeletedMessage())).build() },
    )
  }

  private fun loadRows(userId: String, installedAppId: String): List<ImportDocumentRow> =
    importPort.listImportDocuments(userId, installedAppId).getOrNull().orEmpty().map { it.toRow() }

  private fun ImportDocument.toRow() = ImportDocumentRow(
    id = id.value,
    statusLabel = statusLabel(status),
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun statusLabel(status: ImportStatus): String = when (status) {
    ImportStatus.DOWNLOADED -> userMsg.userImportStatusDownloaded()
  }

  private fun importErrorMessage(code: String): String = when (code) {
    ImportError.INSTALLED_APP_NOT_FOUND.code -> userMsg.userInstalledAppNotFoundError()
    ImportError.BLANK_URL.code -> userMsg.userImportUrlRequiredError()
    ImportError.BLANK_BEARER_TOKEN.code -> userMsg.userImportTokenRequiredError()
    ImportError.INVALID_URL.code -> userMsg.userImportInvalidUrlError()
    ImportError.FETCH_FAILED.code -> userMsg.userImportFetchFailedError()
    ImportError.INVALID_JSON_RESPONSE.code -> userMsg.userImportInvalidJsonError()
    ImportError.NOT_A_JSON_OBJECT.code -> userMsg.userImportNotJsonObjectError()
    ImportError.RESPONSE_TOO_LARGE.code -> userMsg.userImportResponseTooLargeError()
    ImportError.IMPORT_DOCUMENT_NOT_FOUND.code -> userMsg.userImportDocumentNotFoundError()
    else -> msg.commonUnexpectedError()
  }
}
