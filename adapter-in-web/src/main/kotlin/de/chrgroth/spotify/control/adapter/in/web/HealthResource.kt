package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.infra.HealthPort
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

@Path("/health")
@ApplicationScoped
@Suppress("Unused")
class HealthResource {

  @Inject
  @Location("health.html")
  private lateinit var healthTemplate: Template

  @Inject
  private lateinit var health: HealthPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun health(): TemplateInstance = healthTemplate.data("stats", health.getStats())

  @GET
  @Path("/snippets/predicates")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetPredicates(): TemplateInstance =
    healthTemplate.getFragment("snippet_predicates").data("stats", health.getStats())

  @GET
  @Path("/snippets/cronjobs")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCronjobs(): TemplateInstance =
    healthTemplate.getFragment("snippet_cronjobs").data("stats", health.getStats())

  @GET
  @Path("/snippets/outgoing-http-calls")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutgoingHttpCalls(): TemplateInstance =
    healthTemplate.getFragment("snippet_outgoing_http_calls").data("stats", health.getStats())

  @GET
  @Path("/snippets/outbox-partitions")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetOutboxPartitions(): TemplateInstance =
    healthTemplate.getFragment("snippet_outbox_partitions").data("stats", health.getStats())

  @GET
  @Path("/snippets/mongodb-collections")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbCollections(): TemplateInstance =
    healthTemplate.getFragment("snippet_mongodb_collections").data("stats", health.getStats())

  @GET
  @Path("/snippets/mongodb-queries")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbQueries(): TemplateInstance =
    healthTemplate.getFragment("snippet_mongodb_queries").data("stats", health.getStats())

  @GET
  @Path("/snippets/navbar-outbox-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarOutboxStatus(): TemplateInstance =
    healthTemplate.getFragment("snippet_navbar_outbox_status").data("stats", health.getStats())

  @GET
  @Path("/snippets/navbar-playback-status")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetNavbarPlaybackStatus(): TemplateInstance =
    healthTemplate.getFragment("snippet_navbar_playback_status").data("stats", health.getStats())
}
