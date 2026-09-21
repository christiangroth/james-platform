package de.chrgroth.james.platform.adapter.`in`.web

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@ApplicationScoped
@Suppress("Unused")
class GlobalExceptionMapper {

  @field:ConfigProperty(name = "app.error.show-details", defaultValue = "false")
  private var showDetails: Boolean = false

  @ServerExceptionMapper
  fun mapThrowable(e: Throwable): Response {
    val statusCode = if (e is WebApplicationException) e.response.status else INTERNAL_SERVER_ERROR
    return Response.status(statusCode)
      .entity(Templates.error(statusCode, e.javaClass.name, e.message, if (showDetails) e.stackTraceToString() else null))
      .type(MediaType.TEXT_HTML)
      .build()
  }

  companion object {
    private const val INTERNAL_SERVER_ERROR = 500
  }
}
