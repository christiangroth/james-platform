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
        level = if (testing) Level.INFO else Level.DEBUG
        filter { call -> call.request.path().startsWith("/") }
    }

    // Enable payload compression
    // https://ktor.io/servers/features/compression.html
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(512) // condition
        }
    }

    // Jackson configuration
    // https://ktor.io/servers/features/content-negotiation.html
    install(ContentNegotiation) {
        jackson {

            // handler for semver
            registerModule(IdJacksonModule())

            // TODO enable validation somehow?
            // TODO maybe configure date format??

            if (testing) {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
    }

    routing {
        trace { application.log.debug(it.buildText()) }

        // apply routes
{routingCalls}

        // exception handler
        install(StatusPages) {
            exception<JsonProcessingException> { cause ->
                call.fail(
                        code = HttpStatusCode.BadRequest,
                    message = "Unable to parse payload: ${'$'}{cause.javaClass.name}",
                    details = cause.message
                )
            }
            exception<Throwable> { cause ->
                call.fail(
                        code = HttpStatusCode.InternalServerError,
                    message = "Caught unexpected error: ${'$'}{cause.javaClass.name}",
                    details = cause.message
                )
            }
        }
    }
}
