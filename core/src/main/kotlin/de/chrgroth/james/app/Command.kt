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

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.createDevelopmentVersion()) {
            is Maybe.Error -> it.convert()
            is Maybe.Result -> persistence.update(it.value).map { it.developmentVersion!! }
        }
    }

    override fun updateNextVersionDraft(id: UUID, datatype: AppDatatype): Maybe<AppVersionDraft> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.updateDevelopmentVersion(datatype)) {
            is Maybe.Error -> it.convert()
            is Maybe.Result -> persistence.update(it.value).map { it.developmentVersion!! }
        }
    }

    override fun updateNextVersionDraft(id: UUID, report: AppReport): Maybe<AppVersionDraft> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.updateDevelopmentVersion(report)) {
            is Maybe.Error -> it.convert()
            is Maybe.Result -> persistence.update(it.value).map { it.developmentVersion!! }
        }
    }

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.releaseDevelopmentVersion(releaseNotes)) {
            is Maybe.Error -> it.convert()
            is Maybe.Result -> persistence.update(it.value).map { app -> app.latestVersion!! }
        }
    }

    override fun discontinue(id: UUID): Maybe<App> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.discontinue()) {
            is Maybe.Error -> it
            is Maybe.Result -> persistence.update(it.value)
        }
    }

    override fun delete(id: UUID): Maybe<Unit> {
        val app = when (val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        } ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)

        return when (val it = app.canBeDeleted()) {
            is Maybe.Error -> it.convert()
            is Maybe.Result -> persistence.delete(app.id)
        }
    }
}
