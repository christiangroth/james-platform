package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import java.util.UUID

interface AppCommandPort {
    fun create(name: String, developerId: UUID, description: String? = null): Maybe<App>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>

    fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>
    fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun upsertNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun upsertNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>

    fun releaseNextVersion(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion>
    fun changeVersionReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

internal class AppCommandAdapter(
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppCommandPort {

    override fun create(name: String, developerId: UUID, description: String?) =
        App.create(name, developerId, description).flatMap {
            commandPersistence.upsert(it)
        }

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke(App::createDevelopmentVersion) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.createDevelopmentVersionDatatype(datatypeName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.renameDevelopmentVersionDatatype(oldName, newName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun upsertNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.upsertDevelopmentVersionDatatype(name, schemaContent, description) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun removeNextVersionDatatype(id: UUID, datatypeName: String) =
        id.loadAppAndInvoke({ it.removeDevelopmentVersionDatatype(datatypeName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.createDevelopmentVersionReport(reportName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.renameDevelopmentVersionReport(oldName, newName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun upsertNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft> =
        id.loadAppAndInvoke({ it.upsertDevelopmentVersionReport(name, source, description) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun removeNextVersionReport(id: UUID, reportName: String) =
        id.loadAppAndInvoke({ it.removeDevelopmentVersionReport(reportName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun releaseNextVersion(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion> =
        id.loadAppAndInvoke({ it.releaseDevelopmentVersion(changeType, note) }) { _, app ->
            commandPersistence.upsert(app).map { it.latestVersion!! }
        }

    override fun changeVersionReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion> =
        id.loadAppAndInvoke({ it.changeVersionReleaseNote(version, note) }) { _, app ->
            commandPersistence.upsert(app).map { updatedApp ->
                updatedApp.versions.firstOrNull { it.version == version }!!
            }
        }

    // TODO #5 check if user data is still present
    override fun discontinue(id: UUID) =
        id.loadAppAndInvoke(App::discontinue) { _, app ->
            commandPersistence.upsert(app)
        }

    override fun delete(id: UUID) =
        id.loadAppAndInvoke(App::verifyDeletion) { app, _ ->
            commandPersistence.delete(app.id)
        }

    private fun <R, S> UUID.loadAppAndInvoke(
        appOperation: (App) -> Maybe<R>,
        persistenceOperation: (App, R) -> Maybe<S>,
    ) =
        queryPersistence.getOrError(this).flatMap { app ->
            appOperation(app).flatMap { persistenceOperation(app, it) }
        }
}
