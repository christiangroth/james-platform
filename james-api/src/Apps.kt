package de.chrgroth

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.*
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import net.swiftzer.semver.SemVer

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
// TODO returns 404 instead of 405 if route exist but with different method
fun Routing.apps() {
    get<Apps> { AppsController.apps(call) }
    post<Apps> { AppsController.createApp(call) }

    get<AppById> { AppsController.app(call, it) }
    put<AppById> { AppsController.upsertApp(call, it) }
    delete<AppById> { AppsController.deleteApp(call, it) }

    get<AppVersions> { AppsController.versions(call, it) }

    get<AppVersionBySemVer> { AppsController.version(call, it) }
    put<AppVersionBySemVer> { AppsController.upsertAppVersion(call, it) }
    delete<AppVersionBySemVer> { AppsController.deleteAppVersion(call, it) }
}

// this is the web controller
object AppsController {
    suspend fun apps(call: ApplicationCall) = call.respond(AppsInMemoryStorage.list())

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
                    // TODO not sure if this should be a 5xx
                    call.respond(HttpStatusCode.BadRequest, "Unable to store given app!")
                }
            }
        } catch (e: JsonProcessingException) {
            call.fail(HttpStatusCode.BadRequest, "Unable to deserialize app: ${e.javaClass.simpleName}", e.message)
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    suspend fun upsertApp(call: ApplicationCall, params: AppById) {
        try {
            val existingApp = loadApp(params.appId)
            val app = call.receiveOrNull<App>()
            if (app == null) {
                call.fail(HttpStatusCode.BadRequest, "No app given!")
            } else if (existingApp != null && existingApp.id != app.id) {
                call.fail(HttpStatusCode.BadRequest, "App.id does not match!")
            } else {
                val updatedApp = AppsInMemoryStorage.add(
                    if (app.id == null)
                        app.copy(id = params.appId)
                    else
                        app
                )
                if (updatedApp != null) {
                    if (existingApp != null) {
                        call.respond(HttpStatusCode.OK, updatedApp)
                    } else {
                        call.respond(HttpStatusCode.Created, updatedApp)
                    }
                } else {
                    // TODO not sure if this should be a 5xx
                    call.respond(HttpStatusCode.BadRequest, "Unable to store given app version!")
                }
            }
        } catch (e: JsonProcessingException) {
            call.fail(
                HttpStatusCode.BadRequest,
                "Unable to deserialize app version: ${e.javaClass.simpleName}",
                e.message
            )
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    suspend fun deleteApp(call: ApplicationCall, params: AppById) {
        val app = loadApp(params.appId)
        if (app == null) {
            // TODO fail or return NoContent?
            call.respond(HttpStatusCode.NotFound, "App not found!")
            return
        }

        val deleted = AppsInMemoryStorage.remove(app)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            // TODO not sure if this should be a 5xx
            call.respond(HttpStatusCode.BadRequest, "Unable to remove app!")
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    suspend fun upsertAppVersion(call: ApplicationCall, params: AppVersionBySemVer) {
        try {
            val app = loadApp(params.appId)
            if (app == null) {
                call.fail(HttpStatusCode.NotFound, "App not found!")
                return
            }

            val appVersion = call.receiveOrNull<AppVersion>()
            if (appVersion == null) {
                call.fail(HttpStatusCode.BadRequest, "No app version given!")
            } else {
                val updatedApp = AppsInMemoryStorage.addVersion(app, params.appVersion, appVersion)
                if (updatedApp != null) {
                    call.respond(HttpStatusCode.Created, updatedApp)
                } else {
                    // TODO not sure if this should be a 5xx
                    call.respond(HttpStatusCode.BadRequest, "Unable to store given app version!")
                }
            }
        } catch (e: JsonProcessingException) {
            call.fail(
                HttpStatusCode.BadRequest,
                "Unable to deserialize app version: ${e.javaClass.simpleName}",
                e.message
            )
        }
    }

    // TODO 415 Unsupported Media Type (RFC 7231) on invalid app?
    suspend fun deleteAppVersion(call: ApplicationCall, params: AppVersionBySemVer) {
        val app = loadApp(params.appId)
        if (app == null) {
            // TODO fail or return NoContent?
            call.fail(HttpStatusCode.NotFound, "App not found!")
            return
        }

        if (app.versions.containsKey(params.appVersion)) {
            // TODO return the updated app in this case?
            val updatedApp = AppsInMemoryStorage.removeVersion(app, params.appVersion)
            if (updatedApp != null) {
                call.respond(HttpStatusCode.NoContent, updatedApp)
            } else {
                // TODO not sure if this should be a 5xx
                call.respond(HttpStatusCode.BadRequest, "Unable to remove app version!")
            }
        } else {
            // TODO return the app in this case?
            call.respond(HttpStatusCode.NoContent, app)
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

    private fun loadApp(appId: Long) = AppsInMemoryStorage.list().firstOrNull { it.id == appId }

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

    private var appIds = 0L
    private val appsInternal = mutableListOf<App>()

    fun list() = appsInternal.toList()

    fun add(app: App): App? {
        val storedApp = if (app.id == null) app.copy(id = appIds++) else app
        appsInternal.removeIf { it.id == storedApp.id }
        val stored = appsInternal.add(storedApp)
        return if (stored) storedApp else null
    }

    fun remove(app: App) = appsInternal.remove(app)

    fun addVersion(app: App, versionId: String, version: AppVersion): App? {
        val updatedVersion = app.versions.plus(versionId to version)
        val updatedApp = app.copy(versions = updatedVersion)
        appsInternal.removeIf { it.id == updatedApp.id }
        val stored = appsInternal.add(updatedApp)
        return if (stored) updatedApp else null
    }

    fun removeVersion(app: App, versionId: String): App? {
        val updatedVersion = app.versions.filter { it.key != versionId }
        val updatedApp = app.copy(versions = updatedVersion)
        appsInternal.removeIf { it.id == updatedApp.id }
        val stored = appsInternal.add(updatedApp)
        return if (stored) updatedApp else null
    }
}

// the models
// TODO distinguish between API model and DB models
data class App(
    val id: Long?,
    val name: Localized<String>,
    // TODO change back to semver val versions: Map<SemVer, AppVersion>
    // TODO maybe also register jackson serializer/deserializer for Semver!
    val versions: Map<String, AppVersion>
)

data class AppVersion(
    val dummy: String
)
