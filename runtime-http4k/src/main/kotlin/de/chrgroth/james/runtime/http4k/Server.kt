package de.chrgroth.james.runtime.http4k

import mu.KLogging
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.ServerConfig.StopMode
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import java.time.Duration
import java.util.UUID

private const val ENV_SERVER_CONFIG_PORT = "server.port"
private const val ENV_SERVER_CONFIG_SHUTDOWN_TIMEOUT_MILLIS = "server.shutdownTimeoutMillis"
private const val ENV_SERVER_CONFIG_JWT_SECRET = "server.jwt.secret"

data class JamesPlatformServerConfig(
    val port: Int,
    val shutdownTimeoutMillis: Long,
    val jwtSecret: String,
) {
    companion object : KLogging() {
        fun createFromEnvWithDefaults(
            port: Int,
            shutdownTimeoutMillis: Long,
            jwtSecret: String,
        ): JamesPlatformServerConfig = JamesPlatformServerConfig(
            ENV_SERVER_CONFIG_PORT.load()?.toInt() ?: port,
            ENV_SERVER_CONFIG_SHUTDOWN_TIMEOUT_MILLIS.load()?.toLong() ?: shutdownTimeoutMillis,
            ENV_SERVER_CONFIG_JWT_SECRET.load() ?: jwtSecret,
        )

        private fun String.load(): String? = System.getProperty(this)
    }
}

class JamesPlatformServer(private val config: JamesPlatformServerConfig) {

    private val releasenotesService = ReleasenotesService()

    private val authService = AuthService(
        userRepository = object : UserRepository {
            override fun exists(id: String): Boolean =
                listOf("admin", "developer", "user").contains(id)
        },
        jwtService = JwtServiceServiceImpl(UUID.randomUUID().toString(), "james-platform")
    )

    private val templateAuthFilter = TemplateAuthFilter(authService)
    private val apiAuthService = ApiAuthFilter(authService)

    // TODO change to Classpath for production
    private val templates = HandlebarsTemplates().HotReload("runtime-http4k/src/main/resources")

    private val routes = routes(
        // TODO api versioning using path or content-type header?
        "api" bind routes(
            authService.createRoues(templates),
        ),

        templateAuthFilter.then(
            routes(
                "/" bind Method.GET to { Response(Status.OK).body(templates(WorkspaceViewModel())) },
                "/development" bind Method.GET to { Response(Status.OK).body(templates(DevelopmentViewModel())) },
                releasenotesService.createRoutes(templates),
                "/apps" bind Method.GET to { Response(Status.OK).body(templates(AppsViewModel())) },
                "/app" bind Method.GET to { Response(Status.OK).body(templates(AppViewModel())) },
            )
        ),

        // TODO change to Classpath for production
        static(ResourceLoader.Directory("runtime-http4k/src/main/resources/public")),
    )

    fun start() {
        logger.info { "Starting server..." }

        // TODO DebuggingFilters only in dev mode
        val routingHandler = DebuggingFilters.PrintRequestAndResponse().then(
            ServerFilters.CatchLensFailure().then(
                routes
            )
        )

        routingHandler.asServer(
            config = Undertow(
                port = config.port,
                enableHttp2 = true,
                stopMode = StopMode.Graceful(
                    timeout = Duration.ofMillis(config.shutdownTimeoutMillis)
                ),
            )
        ).start().block()
    }

    companion object : KLogging()
}
