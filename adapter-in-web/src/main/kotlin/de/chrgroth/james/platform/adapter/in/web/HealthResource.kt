package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.`in`.infra.HealthPort
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

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun health(): TemplateInstance = httpResponseMetrics.timed("page.health.view") {
    healthTemplate.data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/cronjobs")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCronjobs(): TemplateInstance = httpResponseMetrics.timed("fragment.health.cronjobs") {
    healthTemplate.getFragment("snippet_cronjobs").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-collections")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbCollections(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-collections") {
    healthTemplate.getFragment("snippet_mongodb_collections").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-queries")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbQueries(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-queries") {
    healthTemplate.getFragment("snippet_mongodb_queries").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/http-responses")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetHttpResponses(): TemplateInstance = httpResponseMetrics.timed("fragment.health.http-responses") {
    healthTemplate.getFragment("snippet_http_responses").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/scripting")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetScripting(): TemplateInstance = httpResponseMetrics.timed("fragment.health.scripting") {
    healthTemplate.getFragment("snippet_scripting").data("stats", health.getStats())
  }

  @GET
  @Path("/snippets/import-cleanup")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetImportCleanup(): TemplateInstance = httpResponseMetrics.timed("fragment.health.import-cleanup") {
    healthTemplate.getFragment("snippet_import_cleanup").data("stats", health.getStats())
  }
}
