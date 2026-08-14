package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.AppMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.UserMessages
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportConnectionError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.ImportFetchFailedError
import de.chrgroth.james.platform.domain.error.ImportInvalidUrlError
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionPort
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
import java.time.Instant

data class ImportConnectionRow(
  val id: String,
  val name: String,
  val url: String,
  val hasBearerToken: Boolean,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

@Path("/ui/user/import-connections")
@ApplicationScoped
@BlockAdminAccess
@RolesAllowed("DATA_IMPORT")
@Suppress("Unused")
class UserImportConnectionResource {

  @Inject
  @Location("ui/user/import-connections.html")
  private lateinit var connectionsTemplate: Template

  @Inject
  private lateinit var securityIdentity: SecurityIdentity

  @Inject
  private lateinit var importConnectionPort: ImportConnectionPort

  @Inject
  private lateinit var msg: AppMessages

  @Inject
  private lateinit var userMsg: UserMessages

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Produces(MediaType.TEXT_HTML)
  fun connections(): Response = httpResponseMetrics.timed("page.user-import-connection.list") {
    val userId = securityIdentity.principal.name
    Response.ok(
      connectionsTemplate.data("connections", loadRows(userId)),
    ).build()
  }

  @GET
  @Path("/table")
  @Produces(MediaType.TEXT_HTML)
  fun connectionsTable(): Any = httpResponseMetrics.timed("fragment.user-import-connection.table") {
    val userId = securityIdentity.principal.name
    connectionsTemplate.getFragment("connections_table")
      .data("connections", loadRows(userId))
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun createConnection(
    @FormParam("name") name: String?,
    @FormParam("url") url: String?,
    @FormParam("bearerToken") bearerToken: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import-connection.create") {
    val userId = securityIdentity.principal.name
    if (name.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportConnectionNameRequiredError())).build()
    }
    if (url.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportUrlRequiredError())).build()
    }
    importConnectionPort.createConnection(userId, name, url, bearerToken).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, connectionErrorMessage(error.code), errorDetails = connectionErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportConnectionCreatedMessage())).build() },
    )
  }

  @POST
  @Path("/{connectionId}")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  fun updateConnection(
    @PathParam("connectionId") connectionId: String,
    @FormParam("name") name: String?,
    @FormParam("url") url: String?,
    @FormParam("bearerToken") bearerToken: String?,
    @FormParam("clearBearerToken") clearBearerToken: String?,
  ): Response = httpResponseMetrics.timed("rest.user-import-connection.update") {
    val userId = securityIdentity.principal.name
    if (name.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportConnectionNameRequiredError())).build()
    }
    if (url.isNullOrBlank()) {
      return@timed Response.ok(DeveloperApiResult(false, userMsg.userImportUrlRequiredError())).build()
    }
    importConnectionPort.updateConnection(userId, connectionId, name, url, bearerToken, clearBearerToken = !clearBearerToken.isNullOrBlank()).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, connectionErrorMessage(error.code), errorDetails = connectionErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportConnectionUpdatedMessage())).build() },
    )
  }

  @POST
  @Path("/{connectionId}/test")
  @Produces(MediaType.APPLICATION_JSON)
  fun testConnection(@PathParam("connectionId") connectionId: String): Response = httpResponseMetrics.timed("rest.user-import-connection.test") {
    val userId = securityIdentity.principal.name
    importConnectionPort.testConnection(userId, connectionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, connectionErrorMessage(error.code), errorDetails = connectionErrorDetails(error))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportConnectionTestSucceededMessage())).build() },
    )
  }

  @POST
  @Path("/{connectionId}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  fun deleteConnection(@PathParam("connectionId") connectionId: String): Response = httpResponseMetrics.timed("rest.user-import-connection.delete") {
    val userId = securityIdentity.principal.name
    importConnectionPort.deleteConnection(userId, connectionId).fold(
      ifLeft = { error -> Response.ok(DeveloperApiResult(false, connectionErrorMessage(error.code))).build() },
      ifRight = { Response.ok(DeveloperApiResult(true, userMsg.userImportConnectionDeletedMessage())).build() },
    )
  }

  private fun loadRows(userId: String): List<ImportConnectionRow> =
    importConnectionPort.listConnections(userId).getOrNull().orEmpty().map { it.toRow() }

  private fun ImportConnection.toRow() = ImportConnectionRow(
    id = id.value,
    name = name,
    url = url,
    hasBearerToken = encryptedBearerToken != null,
    createdAt = createdAt,
    lastChangedAt = lastChangedAt,
  )

  private fun connectionErrorMessage(code: String): String = when (code) {
    ImportConnectionError.CONNECTION_NOT_FOUND.code -> userMsg.userImportConnectionNotFoundError()
    ImportConnectionError.BLANK_NAME.code -> userMsg.userImportConnectionNameRequiredError()
    ImportConnectionError.BLANK_URL.code -> userMsg.userImportUrlRequiredError()
    ImportError.INVALID_URL.code -> userMsg.userImportInvalidUrlError()
    ImportError.FETCH_FAILED.code -> userMsg.userImportFetchFailedError()
    ImportError.RESPONSE_TOO_LARGE.code -> userMsg.userImportResponseTooLargeError()
    else -> msg.commonUnexpectedError()
  }

  private fun connectionErrorDetails(error: DomainError): List<String>? = when (error) {
    is ImportInvalidUrlError -> listOf(error.detail)
    is ImportFetchFailedError -> listOf(error.detail)
    else -> null
  }
}
