package de.chrgroth

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Routing
import net.swiftzer.semver.SemVer
import java.util.*

// route definitions
// TODO check when feature for nested classes is available
@Location("$API_PREFIX/apps")
object Apps

@Location("$API_PREFIX/apps/{appId}")
data class AppById(val appId: Long)

@Location("$API_PREFIX/apps/{appId}/versions")
data class AppVersions(val appId: Long)

@Location("${API_PREFIX}/apps/{appId}/versions/{appVersion}")
data class AppVersionBySemVer(val appId: Long, val appVersion: String)

// TODO complete methods: PUT, POST, DELETE
fun Routing.apps() {
    get<Apps> { AppsController.apps(call) }
    get<AppById> { AppsController.app(call, it) }
    get<AppVersions> { AppsController.versions(call, it) }
    get<AppVersionBySemVer> { AppsController.version(call, it) }
}

// TODO how to do error handling??
// this is the web controller
object AppsController {
    suspend fun apps(call: ApplicationCall) = call.respond(AppsInMemoryStorage.apps)

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

// this is a mock for 'the database'
object AppsInMemoryStorage {
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

    val apps = listOf(welcome, sports)
}

// the models
// TODO distinguish between API model and DB models
data class App(
    val id: Long,
    val name: Localized<String>,
    // TODO change back to semver val versions: Map<SemVer, AppVersion>
    val versions: Map<String, AppVersion>
)

data class AppVersion(
    val dummy: String
)
