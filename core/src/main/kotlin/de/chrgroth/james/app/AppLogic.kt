package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.CrudRepository
import de.chrgroth.james.ErrorCodeProvider
import de.chrgroth.james.Maybe
import java.util.UUID

enum class AppErrorCodes : ErrorCodeProvider {
    NOT_FOUND,
    RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
    PREPARE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
    DISCONTINUE_STATUS_IS_DISCONTINUED,
    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}

interface AppQueryServicePort {
    fun getApp(id: UUID): App?
    fun getVersion(id: UUID, version: Semver): AppVersion?
    fun findApps(filter: (App) -> Boolean = { true }): Set<App>
}

interface AppCommandServicePort {
    fun createApp(name: String, description: String? = null): Maybe<App>
    fun getNextVersionDraft(id: UUID): AppVersionDraft?
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

interface AppPersistencePort : CrudRepository<App, UUID>

internal class AppQueryService(private val persistence: AppPersistencePort) : AppQueryServicePort {

    override fun getApp(id: UUID): App? {
        return persistence.get(id)
    }

    override fun getVersion(id: UUID, version: Semver): AppVersion? {
        return persistence.get(id)?.let { app ->
            app.versions.firstOrNull { it.version == version }
        }
    }

    override fun findApps(filter: (App) -> Boolean): Set<App> {
        return persistence.find().filter(filter).toSet()
    }
}

internal class AppCommandService : AppCommandServicePort {
    override fun createApp(name: String, description: String?): Maybe<App> {
        TODO()
    }

    override fun getNextVersionDraft(id: UUID): AppVersionDraft? {
        TODO()
    }

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> {
        // TODO change object and save to DB!
        //return getApp(id)?.prepareNewVersion() ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        TODO()
    }

    override fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft> {
        TODO()
    }

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion> {
        // TODO change object and save to DB!
        //return getApp(id)?.releaseNextVersion(releaseNotes) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        TODO()
    }

    // lifecycle
    override fun discontinue(id: UUID): Maybe<App> {
        // TODO change object and save to DB!
        //val app = getApp(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        //return if (app.status == AppStatus.ACTIVE) {
        //    Maybe.Result(app.descriptor)
        //} else {
        //    Maybe.Error(AppErrorCodes.DISCONTINUE_STATUS_IS_DISCONTINUED)
        //}
        TODO()
    }

    override fun delete(id: UUID): Maybe<Unit> {
        // TODO change object and save to DB!
        //val app = getApp(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        //return if (app.status != AppStatus.DISCONTINUED) {
        //    Maybe.Error(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
        //} else {
        //    // TODO check if installations and data exists
        //    return Maybe.Result(app.descriptor)
        //}
        TODO()
    }
}
