package de.chrgroth.james.app

import de.chrgroth.james.Maybe
import java.util.UUID

interface AppCommandPort {
    fun createApp(name: String, description: String? = null): Maybe<App>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

// TODO check app status
// TODO move more logic to app object?

internal class AppCommandAdapter(private val persistence: AppPersistencePort) : AppCommandPort {

    override fun createApp(name: String, description: String?): Maybe<App> {
        val app = App(
            id = UUID.randomUUID(),
            name = name,
            description = description
        )

        return persistence.create(app)
    }

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        val newDevelopmentVersion = when (val it = app.createDevelopmentVersion()) {
            is Maybe.Error -> return it
            is Maybe.Result -> it.value
        }
        val updatedApp = app.copy(developmentVersion = newDevelopmentVersion)
        // TODO we should be able to guarantee that development version it not null here, otherwise update should return an error
        return persistence.update(updatedApp).map { it.developmentVersion!! }
    }

    override fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft> {

        // TODO validate the models, i.e. the JSON schemas

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
            is Maybe.Result -> it.value
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
