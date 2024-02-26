package de.chrgroth.james.runtime.quarkus

import io.quarkus.runtime.StartupEvent
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.FileSystemAccess
import io.vertx.ext.web.handler.StaticHandler.create
import jakarta.enterprise.event.Observes
import mu.KLogging


private const val PATH = "/*"
private const val FOLDER = "frontend/"

@Suppress("unused")
class FrontendResources {

    fun installRoute(@Observes startupEvent: StartupEvent?, router: Router) {
        logger.info { "serving static content at $PATH" }
        router.route(PATH).handler(create(FileSystemAccess.RELATIVE, FOLDER))
    }

    companion object : KLogging()
}
