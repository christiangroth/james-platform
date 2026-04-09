package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.infra.OutboxViewerPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/outbox-viewer")
@ApplicationScoped
@Suppress("Unused")
class OutboxViewerResource {

  @Inject
  @Location("outbox-viewer.html")
  private lateinit var template: Template

  @Inject
  private lateinit var outboxViewer: OutboxViewerPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun viewer(): TemplateInstance = template.data("partitions", outboxViewer.getPartitions())

  @GET
  @Path("/snippets/tasks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetTasks(): TemplateInstance =
    template.getFragment("snippet_tasks").data("partitions", outboxViewer.getPartitions())
}
