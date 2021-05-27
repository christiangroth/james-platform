package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

interface AppQueryPort {
    fun getApp(id: UUID): Maybe<App?>
    fun getVersion(id: UUID, version: Semver): Maybe<AppVersion?>
    fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?>
    fun findApps(filter: (App) -> Boolean = { true }): Maybe<Set<App>>
}

internal class AppQueryAdapter(private val queryPersistence: AppQueryPersistencePort) : AppQueryPort {

    override fun getApp(id: UUID): Maybe<App?> {
        return queryPersistence.get(id)
    }

    override fun getVersion(id: UUID, version: Semver) =
        queryPersistence.get(id).map { app ->
            app?.versions?.firstOrNull { it.version == version }
        }

    override fun getNextVersionDraft(id: UUID) =
        queryPersistence.get(id).transform { app ->
            if (app == null) {
                Error(AppErrorCodes.NOT_FOUND)
            } else {
                Result(app.developmentVersion)
            }
        }

    override fun findApps(filter: (App) -> Boolean) =
        queryPersistence.find().map {
            it.filter(filter).toSet()
        }
}
