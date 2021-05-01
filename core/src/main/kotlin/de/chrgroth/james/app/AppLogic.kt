package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.ErrorCodeProvider
import de.chrgroth.james.Maybe
import java.util.UUID

enum class AppErrorCodes : ErrorCodeProvider {
    NOT_FOUND,
    RELEASE_NEW_VERSION_DRAFT_MISSING,
    PREPARE_NEW_VERSION_DRAFT_EXISTS,
    DISCONTINUE_STATUS_IS_DISCONTINUED,
    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}

interface AppQueryServicePort {
    fun getApp(id: UUID): AppDescriptor?
    fun getVersion(id: UUID, version: Semver): AppVersionDescriptor?
    fun findApps(filter: (AppDescriptor) -> Boolean = { true }): Set<AppDescriptor>
}

interface AppCommandServicePort {
    fun createApp(name: String, description: String? = null): Maybe<AppDescriptor>
    fun getNextVersionDraft(id: UUID): AppVersionDraft?
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun updateNextVersionDraft(id: UUID, models: Set<AppModel>, reports: Set<AppReport>): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersionDescriptor>
    fun discontinue(id: UUID): Maybe<AppDescriptor>

    // TODO not sure about the return type here
    fun delete(id: UUID): Maybe<AppDescriptor>
}

internal class AppQueryService : AppQueryServicePort {
    override fun getApp(id: UUID): AppDescriptor? {
        TODO()
    }

    override fun getVersion(id: UUID, version: Semver): AppVersionDescriptor? {
        TODO()
    }

    override fun findApps(filter: (AppDescriptor) -> Boolean): Set<AppDescriptor> {
        TODO()
    }
}

internal class AppCommandService : AppCommandServicePort {
    override fun createApp(name: String, description: String?): Maybe<AppDescriptor> {
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

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersionDescriptor> {
        // TODO change object and save to DB!
        //return getApp(id)?.releaseNextVersion(releaseNotes) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        TODO()
    }

    // lifecycle
    override fun discontinue(id: UUID): Maybe<AppDescriptor> {
        // TODO change object and save to DB!
        //val app = getApp(id) ?: return Maybe.Error(AppErrorCodes.NOT_FOUND)
        //return if (app.status == AppStatus.ACTIVE) {
        //    Maybe.Result(app.descriptor)
        //} else {
        //    Maybe.Error(AppErrorCodes.DISCONTINUE_STATUS_IS_DISCONTINUED)
        //}
        TODO()
    }

    override fun delete(id: UUID): Maybe<AppDescriptor> {
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
