package de.chrgroth.james.runtime.ktor

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.app() {
    routing {
        staticResources(
            remotePath = "/app",
            basePackage = "app",
        )
    }
}
