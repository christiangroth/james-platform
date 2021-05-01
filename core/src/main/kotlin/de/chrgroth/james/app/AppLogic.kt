package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.ErrorCodeProvider
import java.util.UUID

enum class AppErrorCodes() : ErrorCodeProvider {
    NOT_FOUND,
    RELEASE_NEW_VERSION_DRAFT_MISSING,
    PREPARE_NEW_VERSION_DRAFT_EXISTS,
    DISCONTINUE_STATUS_IS_DISCONTINUED,
    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}

// TODO define business methods

interface AppServicePort {

    // searching / displaying
    fun getApp(id: UUID): App?
    fun getVersion(id: UUID, version: Semver) = getApp(id)?.getVersion(version)
    fun getLatestVersion(id: UUID) = getApp(id)?.getLatestVersion()

    // TODO use some kind of AppDescription / AppVersionDescriptor for return types
    fun findApps(filter: (App) -> Boolean = { true }): Set<App>
    fun findLatestVersions(filter: (App) -> Boolean = { true }) =
        findApps()
            .map { it to it.getLatestVersion() }
            .toMap()

    // creating / managing
    fun createApp(name: String, description: String? = null): App

    // TODO change object and save to DB!
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> {
        return getApp(id)?.prepareNewVersion() ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
    }

    enum class NewVersionContent { BUGFIX, FEATURE }

    // TODO change object and save to DB!
    fun releaseNextVersion(id: UUID, newVersionContent: NewVersionContent, releaseNotes: AppVersionReleaseNotes?): Maybe<AppVersion> {
        return getApp(id)?.releaseNextVersion(newVersionContent, releaseNotes) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
    }

    // lifecycle
    // TODO change object and save to DB!
    fun discontinue(id: UUID): Maybe<Boolean> {
        val app = getApp(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        return if(app.status == AppStatus.ACTIVE) {
            Maybe.Result(true)
        } else {
            Maybe.Error(AppErrorCodes.DISCONTINUE_STATUS_IS_DISCONTINUED)
        }
    }

    // TODO change object and save to DB!
    fun delete(id: UUID): Maybe<Boolean> {
        val app = getApp(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        return if(app.status != AppStatus.DISCONTINUED) {
            Maybe.Error(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
        } else {
            // TODO check if installations and data exists
            return Maybe.Result(true)
        }
    }
}

internal class AppService : AppServicePort {
    override fun getApp(id: UUID): App? {
        TODO()
    }

    override fun findApps(filter: (App) -> Boolean): Set<App> {
        TODO()
    }

    override fun createApp(name: String, description: String?): App {
        TODO()
    }
}
