package de.chrgroth.james.platform.adapter.`in`.web

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@ApplicationScoped
@Suppress("Unused")
class GlobalExceptionMapper {

  @field:ConfigProperty(name = "app.error.show-details", defaultValue = "false")
  private var showDetails: Boolean = false

  @ServerExceptionMapper
  fun mapThrowable(e: Throwable, uriInfo: UriInfo): Response {
    val statusCode = if (e is WebApplicationException) e.response.status else INTERNAL_SERVER_ERROR
    // the personal access token API is consumed by scripts and agents: answer with JSON, never with the HTML error page
    if (("/" + uriInfo.path.trimStart('/')).startsWith(ApiTokenAccessFilter.API_PATH_PREFIX)) {
      return Response.status(statusCode)
        .entity(ApiErrorResponse(if (statusCode == INTERNAL_SERVER_ERROR) "INTERNAL_ERROR" else "HTTP_$statusCode", e.message ?: e.javaClass.simpleName))
        .type(MediaType.APPLICATION_JSON)
        .build()
    }
    return Response.status(statusCode)
      .entity(Templates.error(statusCode, e.javaClass.name, e.message, if (showDetails) e.stackTraceToString() else null))
      .type(MediaType.TEXT_HTML)
      .build()
  }

  companion object {
    private const val INTERNAL_SERVER_ERROR = 500
  }
}
