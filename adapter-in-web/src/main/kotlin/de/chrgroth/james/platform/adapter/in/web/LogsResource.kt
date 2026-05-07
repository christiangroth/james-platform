package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/logs")
@ApplicationScoped
@Suppress("Unused")
class LogsResource(
  @param:Location("logs.html")
  private val logsTemplate: Template,
  private val logBuffer: UiLogBuffer,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun logs(): TemplateInstance = logsTemplate.data("entries", logBuffer.getRecent())
}
