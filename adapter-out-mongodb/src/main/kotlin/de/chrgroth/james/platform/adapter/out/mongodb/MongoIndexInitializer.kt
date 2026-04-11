package de.chrgroth.james.platform.adapter.out.mongodb

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

@ApplicationScoped
@Suppress("UnusedParameter")
class MongoIndexInitializer {

  fun onStartup(@Observes event: StartupEvent) {
    logger.info { "MongoDB indexes ready." }
  }

  companion object : KLogging()
}
