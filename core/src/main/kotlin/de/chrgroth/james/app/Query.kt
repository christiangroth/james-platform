package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import java.util.UUID

interface AppQueryPort {
    fun getApp(id: UUID): Maybe<App?>
    fun getVersion(id: UUID, version: Semver): Maybe<AppVersion?>
    fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?>
    fun findApps(filter: (App) -> Boolean = { true }): Maybe<Set<App>>
}

internal class AppQueryAdapter(private val persistence: AppPersistencePort) : AppQueryPort {

    override fun getApp(id: UUID): Maybe<App?> {
        return persistence.get(id)
    }

    override fun getVersion(id: UUID, version: Semver) =
        persistence.get(id).transform { app ->
            if (app == null) {
                Maybe.Result(null)
            } else {
                Maybe.Result(app.versions.firstOrNull { it.version == version })
            }
        }

    override fun getNextVersionDraft(id: UUID) =
        persistence.get(id).transform { app ->
            if (app == null) {
                Maybe.Error(AppErrorCodes.NOT_FOUND)
            } else {
                Maybe.Result(app.developmentVersion)
            }
        }

    override fun findApps(filter: (App) -> Boolean) =
        persistence.find().map {
            it.filter(filter).toSet()
        }
}
