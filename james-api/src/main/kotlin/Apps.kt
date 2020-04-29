package de.chrgroth

import io.ktor.application.ApplicationCall
import net.swiftzer.semver.SemVer
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.id.toId
import java.util.*

object AppsController : GenericCrudController<App, Id<App>>(App::class, "apps") {

    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toAppId()

    override suspend fun list(call: ApplicationCall) = MongoDB.listApps()
    override suspend fun get(call: ApplicationCall, id: Id<App>?) = if (id != null) MongoDB.get(id) else null
    override suspend fun getId(item: App) = item._id
    override suspend fun createCopyWithId(item: App, id: Id<App>) = item.copy(_id = id)
    override suspend fun upsert(call: ApplicationCall, item: App) = MongoDB.upsert(item)
    override suspend fun remove(call: ApplicationCall, item: App) = MongoDB.delete(item)
}

object AppVersionsController : GenericCrudController<AppVersion, SemVer>(AppVersion::class, "apps/{appId}/versions") {

    override fun convertItemIdParameter(paramValue: String?) = paramValue?.toSemVer()

    override suspend fun list(call: ApplicationCall) = call.loadApp()?.versions?.toList() ?: listOf()
    override suspend fun get(call: ApplicationCall, id: SemVer?): AppVersion? =
        call.loadApp()?.versions?.firstOrNull { it.version == id } ?: null

    override suspend fun getId(item: AppVersion) = item.version
    override suspend fun createCopyWithId(item: AppVersion, id: SemVer) = item.copy(version = id)
    override suspend fun upsert(call: ApplicationCall, item: AppVersion): AppVersion? {
        val app = call.loadApp()

        // TODO how provide app not found message?!
        return if (app == null) {
            null
        } else {
            val updatedApp =
                MongoDB.upsert(app.copy(versions = app.versions.filterNot { it.version == item.version }.plus(item)))
            if (updatedApp != null) item else null
        }
    }

    override suspend fun remove(call: ApplicationCall, item: AppVersion): Boolean {
        val app = call.loadApp()
        return if (app == null) {
            true
        } else {
            val updatedApp = MongoDB.upsert(app.copy(versions = app.versions.filterNot { it.version == item.version }))
            updatedApp != null
        }
    }

    private fun ApplicationCall.loadApp(): App? {
        val objectId = parameters["appId"]?.toAppId()
        return if (objectId != null) MongoDB.get(objectId) else null
    }
}

private fun String.toAppId(): Id<App>? {
    return try {
        ObjectId(this).toId<App>()
    } catch (e: IllegalArgumentException) {
        // TODO logging
        println("Failed to convert $this to App._id: ${e.message}")
        null
    }
}

// the models
// TODO distinguish between API model and DB models

data class Localized<T>(val values: Map<Locale, T>)

data class App(
    val _id: Id<App>?,
    val name: Localized<String>,
    val versions: List<AppVersion>
) {

    fun versionBySemVer(version: SemVer) = versions.firstOrNull { it.version == version }
}

data class AppVersion(
    val version: SemVer,
    val dummy: String
)
