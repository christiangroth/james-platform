package de.chrgroth.james.runtime.http4k

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
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import java.util.UUID


// TODO create server class?
fun main() {

    val releasenotesService = ReleasenotesService()

    val authService = AuthService(
        userRepository = object : UserRepository {
            override fun exists(id: String): Boolean =
                listOf("admin", "developer", "user").contains(id)
        },
        jwtService = JwtServiceServiceImpl(UUID.randomUUID().toString(), "james-platform")
    )
    val templateAuthFilter = TemplateAuthFilter(authService)
    val apiAuthService = ApiAuthFilter(authService)

    // TODO change to Classpath for production
    val templates = HandlebarsTemplates().HotReload("runtime-http4k/src/main/resources")
    val routes = routes(
        // TODO api versioning using path or content-type header?
        "api" bind routes(
            authService.createRoues(templates),
        ),

        templateAuthFilter.then(
            routes(
                "/" bind Method.GET to { Response(Status.OK).body(templates(WorkspaceViewModel())) },
                "/development" bind Method.GET to { Response(Status.OK).body(templates(DevelopmentViewModel())) },
                "/releasenotes" bind Method.GET to {
                    Response(Status.OK).body(
                        templates(
                            ReleasenotesViewModel(
                                releasenotesService.releasenotes
                            )
                        )
                    )
                },
                "/apps" bind Method.GET to { Response(Status.OK).body(templates(AppsViewModel())) },
                "/app" bind Method.GET to { Response(Status.OK).body(templates(AppViewModel())) },
            )
        ),

        // TODO change to Classpath for production
        static(ResourceLoader.Directory("runtime-http4k/src/main/resources/public")),
    )

    // TODO only in dev mode
    DebuggingFilters.PrintRequestAndResponse().then(
        ServerFilters.CatchLensFailure().then(routes)
    ).asServer(Undertow(8080)).start().block()
}

object ServerConfig {
    private val DEFAULT_PORT = "8080"
    private val DEFAULT_DATABASE_URL = "jdbc:h2:file:./example"
    private val DEFAULT_DATABASE_USERNAME = "sa"
    private val DEFAULT_DATABASE_PASSWORD = ""
    private val DEFAULT_JWT_SECRET = "secret"

    val port: Int
        get() = System.getProperty("server.port", DEFAULT_PORT).toInt()

    val dbUrl: String
        get() = System.getProperty("server.db.url", DEFAULT_DATABASE_URL)

    val dbUsername: String
        get() = System.getProperty("server.db.username", DEFAULT_DATABASE_USERNAME)

    val dbPassword: String
        get() = System.getProperty("server.db.password", DEFAULT_DATABASE_PASSWORD)

    val jwtSecret: String
        get() = System.getProperty("server.jwt.secret", DEFAULT_JWT_SECRET)
}
