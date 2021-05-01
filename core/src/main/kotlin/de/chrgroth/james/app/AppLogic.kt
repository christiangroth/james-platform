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

// TODO might return Maybes only?? This would allow to catch technical errors
interface AppQueryServicePort {
    fun getApp(id: UUID): App?
    fun getVersion(id: UUID, version: Semver): AppVersion?
    fun findApps(filter: (App) -> Boolean = { true }): Set<App>
}

interface AppCommandServicePort {
    fun createApp(name: String, description: String? = null): Maybe<App>
    fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

// TODO split query and command??
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

internal class AppCommandService(private val persistence: AppPersistencePort) : AppCommandServicePort {

    override fun createApp(name: String, description: String?): Maybe<App> {
        val app = App(
            id = UUID.randomUUID(),
            name = name,
            description = description
        )

        return persistence.create(app)
    }

    override fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        return Maybe.Result(app?.developmentVersion)
    }

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        val newDevelopmentVersion = when (val it = app.createDevelopmentVersion()) {
            is Maybe.Error -> return it
            is Maybe.Result -> it.result
        }
        val updatedApp = app.copy(developmentVersion = newDevelopmentVersion)
        // TODO we should be able to guarantee that development version it not null here, otherwise update should return an error
        return persistence.update(updatedApp).map { it.developmentVersion!! }
    }

    override fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        val newDevelopmentVersion = AppVersionDraft(models = models, reports = reports)
        val updatedApp = app.copy(developmentVersion = newDevelopmentVersion)
        // TODO we should be able to guarantee that development version it not null here, otherwise update should return an error
        return persistence.update(updatedApp).map { it.developmentVersion!! }
    }

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        val newVersion = when (val it = app.releaseDevelopmentVersion(releaseNotes)) {
            is Maybe.Error -> return it
            is Maybe.Result -> it.result
        }
        val updatedApp = app.copy(developmentVersion = null, versions = app.versions.plus(newVersion))
        // TODO may throw ... how to solve this??
        return persistence.update(updatedApp).map { app -> app.versions.first { it.version == newVersion.version } }
    }

    override fun discontinue(id: UUID): Maybe<App> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        if (app.status == AppStatus.DISCONTINUED) {
            return Maybe.Error(AppErrorCodes.DISCONTINUE_STATUS_IS_DISCONTINUED)
        }

        val updatedApp = app.copy(discontinued = true)
        return persistence.update(updatedApp)
    }

    override fun delete(id: UUID): Maybe<Unit> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        if (app.status != AppStatus.DISCONTINUED) {
            return Maybe.Error(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
        }

        // TODO check if still data exists

        return persistence.delete(app.id)
    }
}
