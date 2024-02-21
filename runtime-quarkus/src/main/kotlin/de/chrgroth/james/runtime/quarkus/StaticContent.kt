package de.chrgroth.james.runtime.quarkus

import io.quarkus.runtime.StartupEvent
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.FileSystemAccess
import io.vertx.ext.web.handler.StaticHandler.create
import jakarta.enterprise.event.Observes
import mu.KLogging


private const val path = "/*"
private const val folder = "ui/"

@Suppress("unused")
class StaticResources {

    fun installRoute(@Observes startupEvent: StartupEvent?, router: Router) {
        logger.info { "serving static content at $path" }
        router.route().path(path).handler(create(FileSystemAccess.RELATIVE, folder))
    }

    companion object : KLogging()
}
