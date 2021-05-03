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

    override fun getVersion(id: UUID, version: Semver): Maybe<AppVersion?> {
        val app = when(val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        }

        return if(app != null) {
            Maybe.Result(app.versions.firstOrNull { it.version == version })
        } else {
            Maybe.Result(null)
        }
    }

    override fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?> {
        val app = when(val result = persistence.get(id)) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        }

        return if(app != null) {
            Maybe.Result(app?.developmentVersion)
        } else {
            Maybe.Error(AppErrorCodes.NOT_FOUND)
        }
    }

    override fun findApps(filter: (App) -> Boolean): Maybe<Set<App>> {
        val apps = when(val result = persistence.find()) {
            is Maybe.Error -> return result.convert()
            is Maybe.Result -> result.value
        }

        return Maybe.Result(apps.filter(filter).toSet())
    }
}
