package de.chrgroth

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import net.swiftzer.semver.SemVer
import java.util.*

// route definitions
// TODO check when feature for nested classes is available
sealed class Route

@Location("$API_PREFIX/apps")
object Apps : Route()

@Location("$API_PREFIX/apps/{appId}")
data class AppById(val appId: Long) : Route()

@Location("$API_PREFIX/apps/{appId}/versions")
data class AppVersions(val appId: Long) : Route()

@Location("${API_PREFIX}/apps/{appId}/versions/{appVersion}")
data class AppVersionBySemVer(val appId: Long, val appVersion: String) : Route()

// TODO refactor to generic CRUD controller
// TODO complete methods: PUT/DELETE app, POST/PUT/DELETE version,
fun Routing.apps() {
    get<Apps> { AppsController.apps(call) }
    post<Apps> { AppsController.createApp(call) }
    get<AppById> { AppsController.app(call, it) }
    // PUT
    // DELETE
    get<AppVersions> { AppsController.versions(call, it) }
    // POST
    get<AppVersionBySemVer> { AppsController.version(call, it) }
    // PUT
    // DELETE
}

// this is the web controller
object AppsController {
    suspend fun apps(call: ApplicationCall) = call.respond(AppsInMemoryStorage.apps)

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    suspend fun createApp(call: ApplicationCall) {
        try {
            val app = call.receiveOrNull<App>()
            if (app == null) {
                call.fail(HttpStatusCode.BadRequest, "No app given!")
            } else {
                val storedApp = AppsInMemoryStorage.add(app)
                if (storedApp != null) {
                    call.respond(HttpStatusCode.Created, storedApp)
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Unable to store given app!")
                }
            }
        } catch (e: JsonProcessingException) {
            call.fail(HttpStatusCode.BadRequest, "Unable to deserialize app: ${e.javaClass.simpleName}", e.message)
        }
    }

    suspend fun app(call: ApplicationCall, params: AppById) =
        call.respondOrNotFound(loadApp(params.appId))

    suspend fun versions(call: ApplicationCall, params: AppVersions) =
        call.respond(loadApp(params.appId)?.versions ?: mapOf<SemVer, AppVersion>())

    suspend fun version(call: ApplicationCall, params: AppVersionBySemVer) =
        call.respondOrNotFound(appVersion(params))

    private fun appVersion(params: AppVersionBySemVer): AppVersion? {
        val versions = loadApp(params.appId)?.versions
        return versions?.get(params.appVersion)
    }

    private fun loadApp(appId: Long) = AppsInMemoryStorage.apps.firstOrNull { it.id == appId }

    private suspend fun ApplicationCall.respondOrNotFound(item: Any?) =
        if (item != null) {
            respond(item)
        } else {
            respond(HttpStatusCode.NotFound)
        }
}

// TODO is there some kind of service type?? need a new instance per test / TestApplicationEngine, maybe use DI for this: https://github.com/ktorio/ktor-samples/tree/master/other/di-kodein
// this is a mock for 'the database'
object AppsInMemoryStorage {

    init {
        println("INITIALIZED!!!")
    }

    private var appIds = 0L
    private val welcome = App(
        appIds++,
        Localized(mapOf(Locale.GERMAN to "Willkommen", Locale.ENGLISH to "Welcome")),
        mapOf(
            SemVer.parse("0.1").toString() to AppVersion("foo"),
            SemVer.parse("0.1.1").toString() to AppVersion("bar"),
            SemVer.parse("0.2").toString() to AppVersion("baz")
        )
    )

    private val sports = App(
        appIds++,
        Localized(mapOf(Locale.GERMAN to "Sport", Locale.ENGLISH to "Sports")),
        mapOf(
            SemVer.parse("0.1").toString() to AppVersion("Running only!")
        )
    )

    fun add(app: App): App? {
        val storedApp = app.copy(id = appIds++)
        val stored = apps.add(storedApp)
        return if (stored) storedApp else null
    }

    val apps = mutableListOf(welcome, sports)
}

// the models
// TODO distinguish between API model and DB models
data class App(
    val id: Long,
    val name: Localized<String>,
    // TODO change back to semver val versions: Map<SemVer, AppVersion>
    // TODO maybe also register jackson serializer/deserializer for Semver!
    val versions: Map<String, AppVersion>
)

data class AppVersion(
    val dummy: String
)
