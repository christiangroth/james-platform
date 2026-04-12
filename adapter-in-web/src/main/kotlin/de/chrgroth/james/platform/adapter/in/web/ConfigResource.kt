package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.port.out.infra.ConfigurationInfoPort
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

@Path("/config")
@ApplicationScoped
@Suppress("Unused")
class ConfigResource {

  @Inject
  @Location("config.html")
  private lateinit var configTemplate: Template

  @Inject
  private lateinit var configurationInfo: ConfigurationInfoPort

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun config(): TemplateInstance = configTemplate.data("stats", configurationInfo.getConfigurationStats())
}
