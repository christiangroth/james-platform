package de.chrgroth

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.features.*
import org.slf4j.event.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

// TODO move somewhere applicable
data class ResponseError(val code: Int, val message: String, val details: String? = null)
suspend fun ApplicationCall.fail(code: HttpStatusCode, message: String, details: String? = null) =
    respond(code, ResponseError(code.value, message, details))

// TODO add metrics
// TODO add AUTH somehow!

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    // Allows type-safe routing using @Location
    // https://ktor.io/servers/features/locations.html
    install(Locations) { }

    // TODO need to configure logback.xml for testing / production: https://ktor.io/servers/features/call-logging.html
    install(CallLogging) {
        level = if (testing) Level.INFO else Level.DEBUG
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
            enable(SerializationFeature.INDENT_OUTPUT)
            // TODO this breaks java.util.Local de/serialization... WTF??!?
            // enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        }
    }

    routing {
        trace { application.log.trace(it.buildText()) }

        // apply james routes
        apps()

        install(StatusPages) {
            exception<Throwable> { cause ->
                call.fail(HttpStatusCode.InternalServerError, "Caught unexpected error: ${cause.javaClass.name}", cause.message)
            }
        }
    }
}

