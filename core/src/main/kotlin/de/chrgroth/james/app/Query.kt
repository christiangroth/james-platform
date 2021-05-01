package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import java.util.UUID

// TODO might return Maybes only?? This would allow to catch technical errors
interface AppQueryPort {
    fun getApp(id: UUID): App?
    fun getVersion(id: UUID, version: Semver): AppVersion?
    fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?>
    fun findApps(filter: (App) -> Boolean = { true }): Set<App>
}

internal class AppQueryAdapter(private val persistence: AppPersistencePort) : AppQueryPort {

    override fun getApp(id: UUID): App? {
        return persistence.get(id)
    }

    override fun getVersion(id: UUID, version: Semver): AppVersion? {
        return persistence.get(id)?.let { app ->
            app.versions.firstOrNull { it.version == version }
        }
    }

    override fun getNextVersionDraft(id: UUID): Maybe<AppVersionDraft?> {
        val app = persistence.get(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        return Maybe.Result(app?.developmentVersion)
    }

    override fun findApps(filter: (App) -> Boolean): Set<App> {
        return persistence.find().filter(filter).toSet()
    }
}
