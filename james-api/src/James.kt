package de.chrgroth

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.routing.routing
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

// TODO add metrics
// TODO add AUTH somehow!

@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    // Allows type-safe routing using @Location
    // https://ktor.io/servers/features/locations.html
    install(Locations) { }

    // Logs application calls
    // https://ktor.io/servers/features/call-logging.html
    install(CallLogging) {
        level = if (testing) Level.INFO else Level.TRACE
        filter { call -> call.request.path().startsWith("/") }
    }

    // TODO check configuration: https://ktor.io/servers/features/compression.html
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(512) // condition
        }
    }

    // TODO check configuration: https://ktor.io/servers/features/conditional-headers.html
    install(ConditionalHeaders)

    // TODO check configuration: https://ktor.io/servers/features/data-conversion.html
    install(DataConversion)

    // TODO check configuration: https://ktor.io/servers/features/content-negotiation.html
    install(ContentNegotiation) {
        jackson {

            // TODO handler for semver?

            // TODO enable validation somehow?
            println("VALIDATION: $polymorphicTypeValidator")

            enable(SerializationFeature.INDENT_OUTPUT)
            // TODO this breaks java.util.Local de/serialization... WTF??!?
            // enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        }
    }

    routing {
        trace { application.log.debug(it.buildText()) }

        // apply james routes
        AppsController.routes(this)
        AppVersionsController.routes(this)

        // exception handler
        install(StatusPages) {
            exception<JsonProcessingException> { cause ->
                call.fail(
                    code = HttpStatusCode.BadRequest,
                    message = "Unable to parse payload: ${cause.javaClass.name}",
                    details = cause.message
                )
            }
            exception<Throwable> { cause ->
                call.fail(
                    code = HttpStatusCode.InternalServerError,
                    message = "Caught unexpected error: ${cause.javaClass.name}",
                    details = cause.message
                )
            }
        }
    }
}

