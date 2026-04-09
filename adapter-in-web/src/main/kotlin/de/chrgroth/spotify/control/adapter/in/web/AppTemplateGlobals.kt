package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.EngineBuilder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class AppTemplateGlobals {

  @field:ConfigProperty(name = "quarkus.application.version")
  lateinit var version: String

  fun onEngineBuilder(@Observes builder: EngineBuilder) {
    builder.addTemplateInstanceInitializer { instance ->
      instance.data("appBuildVersion", version)
    }
  }
}
