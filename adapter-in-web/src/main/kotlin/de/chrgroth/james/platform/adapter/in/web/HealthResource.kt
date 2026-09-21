package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.`in`.infra.HealthPort
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
  private lateinit var health: HealthPort

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun health(): TemplateInstance = httpResponseMetrics.timed("page.health.view") {
    Templates.health(health.getStats())
  }

  @GET
  @Path("/snippets/cronjobs")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetCronjobs(): TemplateInstance = httpResponseMetrics.timed("fragment.health.cronjobs") {
    Templates.`health$snippet_cronjobs`(health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-collections")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbCollections(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-collections") {
    Templates.`health$snippet_mongodb_collections`(health.getStats())
  }

  @GET
  @Path("/snippets/mongodb-queries")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetMongoDbQueries(): TemplateInstance = httpResponseMetrics.timed("fragment.health.mongodb-queries") {
    Templates.`health$snippet_mongodb_queries`(health.getStats())
  }

  @GET
  @Path("/snippets/http-responses")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetHttpResponses(): TemplateInstance = httpResponseMetrics.timed("fragment.health.http-responses") {
    Templates.`health$snippet_http_responses`(health.getStats())
  }

  @GET
  @Path("/snippets/scripting")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetScripting(): TemplateInstance = httpResponseMetrics.timed("fragment.health.scripting") {
    Templates.`health$snippet_scripting`(health.getStats())
  }

  @GET
  @Path("/snippets/import-cleanup")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun snippetImportCleanup(): TemplateInstance = httpResponseMetrics.timed("fragment.health.import-cleanup") {
    Templates.`health$snippet_import_cleanup`(health.getStats())
  }
}
