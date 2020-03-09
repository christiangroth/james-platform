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

// TODO add AUTH somehow!

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    // allows the @Location path definitions
    install(Locations) { }

    // TODO need to configure logback.xml for testing / production
    install(CallLogging) {
        level = if (testing) Level.INFO else Level.DEBUG
        filter { call -> call.request.path().startsWith("/") }
    }

    // TODO configure all extensions below this comment

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(512) // condition
        }
    }

    install(ConditionalHeaders)

    install(DataConversion)

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    routing {
        trace { application.log.trace(it.buildText()) }

        // apply james routes
        apps()

        install(StatusPages) {
            exception<AuthenticationException> { cause ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { cause ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }

        // TODO test?
        get("/json/jackson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

