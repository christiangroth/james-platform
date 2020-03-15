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
import java.util.*

object AppsController: GenericCrudController<App, Long>(App::class, "apps") {

    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toLong() ?: null

    override suspend fun list() = AppsInMemoryStorage.list()
    override suspend fun get(id: Long?) = AppsInMemoryStorage.list().firstOrNull { it.id == id }
    override suspend fun getId(item: App) = item.id
    override suspend fun createCopyWithId(item: App, id: Long) = item.copy(id = id)
    override suspend fun upsert(item: App)= AppsInMemoryStorage.add(item)
    override suspend fun remove(item: App) = AppsInMemoryStorage.remove(item)
}

// route definitions
sealed class AppRoute

@Location("$API_PREFIX/apps/{appId}/versions")
data class AppVersions(val appId: Long) : AppRoute()

@Location("${API_PREFIX}/apps/{appId}/versions/{appVersion}")
data class AppVersionBySemVer(val appId: Long, val appVersion: String) : AppRoute()

// TODO returns 404 instead of 405 if route exist but with different method
fun Routing.apps() {
    get<AppVersions> { AppVersionsController.versions(call, it) }

    get<AppVersionBySemVer> { AppVersionsController.version(call, it) }
    put<AppVersionBySemVer> { AppVersionsController.upsertAppVersion(call, it) }
    delete<AppVersionBySemVer> { AppVersionsController.deleteAppVersion(call, it) }
}

// this is the web controller
// TODO refactor to generic CRUD controller
object AppVersionsController {

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

    suspend fun versions(call: ApplicationCall, params: AppVersions) =
        call.respond(loadApp(params.appId)?.versions ?: mapOf<SemVer, AppVersion>())

    suspend fun version(call: ApplicationCall, params: AppVersionBySemVer) =
        call.respondOrNotFound(appVersion(params))

    private fun appVersion(params: AppVersionBySemVer): AppVersion? {
        val versions = loadApp(params.appId)?.versions
        return versions?.get(params.appVersion)
    }

    private fun loadApp(appId: Long?) = if(appId == null) null else AppsInMemoryStorage.list().firstOrNull { it.id == appId }

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

data class Localized<T>(val values: Map<Locale, T>)

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
