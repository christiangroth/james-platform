package de.chrgroth.james.app

import de.chrgroth.james.Maybe
import java.util.UUID

interface AppCommandPort {
    fun createApp(name: String, description: String? = null): Maybe<App>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, datatype: AppDatatype): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, report: AppReport): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

internal class AppCommandAdapter(private val persistence: AppPersistencePort) : AppCommandPort {

    override fun createApp(name: String, description: String?) =
        persistence.create(App(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            developmentVersion = AppVersionDraft(),
        ))

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke(App::createDevelopmentVersion) { _, app ->
            persistence.update(app).map { it.developmentVersion!! }
        }

    override fun updateNextVersionDraft(id: UUID, datatype: AppDatatype) =
        id.loadAppAndInvoke({ it.updateDevelopmentVersion(datatype) }) { _, app ->
            persistence.update(app).map { it.developmentVersion!! }
        }

    override fun updateNextVersionDraft(id: UUID, report: AppReport) =
        id.loadAppAndInvoke({ it.updateDevelopmentVersion(report) }) { _, app ->
            persistence.update(app).map { it.developmentVersion!! }
        }

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes) =
        id.loadAppAndInvoke({ it.releaseDevelopmentVersion(releaseNotes) }) { _, app ->
            persistence.update(app).map { it.latestVersion!! }
        }

    // TODO check if user data is still present
    override fun discontinue(id: UUID) =
        id.loadAppAndInvoke(App::discontinue) { _, app ->
            persistence.update(app)
        }

    override fun delete(id: UUID) =
        id.loadAppAndInvoke(App::canBeDeleted) { app, _ ->
            persistence.delete(app.id)
        }

    private fun <R, S> UUID.loadAppAndInvoke(
        appOperation: (App) -> Maybe<R>,
        persistenceOperation: (App, R) -> Maybe<S>,
    ) =
        persistence.get(this).transform { app ->
            if (app == null) {
                Maybe.Error(AppErrorCodes.NOT_FOUND)
            } else {
                when (val result = appOperation(app)) {
                    is Maybe.Error -> result.convert()
                    is Maybe.Result -> persistenceOperation(app, result.value)
                }
            }
        }
}
