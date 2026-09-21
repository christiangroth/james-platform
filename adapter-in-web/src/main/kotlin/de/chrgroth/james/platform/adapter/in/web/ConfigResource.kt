package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.out.infra.ConfigurationInfoPort
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/config")
@ApplicationScoped
@Suppress("Unused")
class ConfigResource {

  @Inject
  private lateinit var configurationInfo: ConfigurationInfoPort

  @Inject
  private lateinit var httpResponseMetrics: HttpResponseMetrics

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun config(): TemplateInstance = httpResponseMetrics.timed("page.config.view") {
    Templates.config(configurationInfo.getConfigurationStats())
  }
}
