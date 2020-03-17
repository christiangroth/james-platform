package de.chrgroth

import io.ktor.application.ApplicationCall
import net.swiftzer.semver.SemVer
import java.util.*

object AppsController : GenericCrudController<App, Long>(App::class, "apps") {

    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toLong() ?: null

    override suspend fun list(call: ApplicationCall) = AppsInMemoryStorage.list()
    override suspend fun get(call: ApplicationCall, id: Long?) = AppsInMemoryStorage.list().firstOrNull { it.id == id }
    override suspend fun getId(item: App) = item.id
    override suspend fun createCopyWithId(item: App, id: Long) = item.copy(id = id)
    override suspend fun upsert(call: ApplicationCall, item: App) = AppsInMemoryStorage.add(item)
    override suspend fun remove(call: ApplicationCall, item: App) = AppsInMemoryStorage.remove(item)
}

object AppVersionsController : GenericCrudController<AppVersion, SemVer>(AppVersion::class, "apps/{appId}/versions") {

    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toSemVer()

    override suspend fun list(call: ApplicationCall) = call.loadApp()?.versions?.toList() ?: listOf()
    override suspend fun get(call: ApplicationCall, id: SemVer?): AppVersion? =
        call.loadApp()?.versions?.firstOrNull { it.id == id } ?: null

    override suspend fun getId(item: AppVersion) = item.id
    override suspend fun createCopyWithId(item: AppVersion, id: SemVer) = item.copy(id = id)
    override suspend fun upsert(call: ApplicationCall, item: AppVersion): AppVersion? {
        val app = call.loadApp()

        // TODO how provide app not found message?!
        return if (app == null) {
            null
        } else {
            AppsInMemoryStorage.addVersion(app, item)
        }
    }

    override suspend fun remove(call: ApplicationCall, item: AppVersion): Boolean {
        val app = call.loadApp()
        return if (app == null) {
            true
        } else {
            AppsInMemoryStorage.removeVersion(app, item)
        }
    }

    private fun ApplicationCall.loadApp() = AppsInMemoryStorage.list().firstOrNull { it.id == appId() }
    private fun ApplicationCall.appId() = parameters["appId"]?.toLong() ?: null
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

    fun addVersion(app: App, version: AppVersion): AppVersion? {
        val updatedVersions = app.versions.plus(version)
        val updatedApp = app.copy(versions = updatedVersions)
        appsInternal.removeIf { it.id == updatedApp.id }
        val stored = appsInternal.add(updatedApp)
        return if (stored) version else null
    }

    fun removeVersion(app: App, version: AppVersion): Boolean {
        println(app.versions)
        val updatedVersions = app.versions.filter { it != version }
        println(updatedVersions)
        val updatedApp = app.copy(versions = updatedVersions)
        println(updatedApp)
        appsInternal.removeIf { it.id == updatedApp.id }
        appsInternal.add(updatedApp)
        return true
    }
}

// the models
// TODO distinguish between API model and DB models

data class Localized<T>(val values: Map<Locale, T>)

data class App(
    val id: Long?,
    val name: Localized<String>,
    val versions: List<AppVersion>
) {

    fun versionBySemVer(version: SemVer) = versions.firstOrNull { it.id == version }
}

data class AppVersion(
    val id: SemVer,
    val dummy: String
)
