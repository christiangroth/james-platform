package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Either
import java.util.UUID

// TODO define business methods
// TODO Set vs List

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
    // TODO use some more solid error handling. maybe error codes enum or something alike
    fun prepareNextVersion(id: UUID): Either<AppVersionDraft, Exception> {
        return getApp(id)?.prepareNewVersion() ?: return Either.Right(IllegalStateException("App with id $id not found!"))
    }

    enum class NewVersionContent { BUGFIX, FEATURE }

    // TODO change object and save to DB!
    // TODO use some more solid error handling. maybe error codes enum or something alike
    fun releaseNextVersion(id: UUID, newVersionContent: NewVersionContent, releaseNotes: AppVersionReleaseNotes?): Either<AppVersion, Exception> {
        return getApp(id)?.releaseNextVersion(newVersionContent, releaseNotes) ?: return Either.Right(IllegalStateException("App with id $id not found!"))
    }

    // lifecycle
    // TODO use some more solid error handling. maybe error codes enum or something alike
    fun discontinue(id: UUID): Either<Boolean, Exception> {
        val app = getApp(id) ?: return Either.Right(IllegalStateException("App with id $id not found!"))
        return if(app.status == AppStatus.ACTIVE) {
            // TODO change object and save to DB!
            Either.Left(true)
        } else {
            Either.Right(IllegalStateException("App is already discontinued!"))
        }
    }

    // TODO use some more solid error handling. maybe error codes enum or something alike
    fun delete(id: UUID): Either<Boolean, Exception> {
        val app = getApp(id) ?: return Either.Right(IllegalStateException("App with id $id not found!"))
        return if(app.status == AppStatus.ACTIVE) {
            Either.Right(IllegalStateException("App in active status cannot be deleted!"))
        } else {
            // TODO check if installations and data exists
            // TODO change object and save to DB!
            return Either.Left(true)
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
