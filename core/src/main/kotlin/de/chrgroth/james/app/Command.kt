package de.chrgroth.james.app

import de.chrgroth.james.Maybe
import java.util.UUID

interface AppCommandPort {
    fun create(name: String, developerId: UUID, description: String? = null): Maybe<App>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>

    // TODO #25 fun create(Empty?)NextVersionDatatype(id: UUID, datatype: AppDatatypeDraft): Maybe<AppVersionDraft>
    fun upsertNextVersionDatatype(id: UUID, datatype: AppDatatypeDraft): Maybe<AppVersionDraft>
    fun removeNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    // TODO #25 fun create(Empty?)NextVersionReport(id: UUID, report: AppReport): Maybe<AppVersionDraft>
    fun upsertNextVersionReport(id: UUID, report: AppReport): Maybe<AppVersionDraft>
    fun removeNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion>
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

    override fun upsertNextVersionDatatype(id: UUID, datatype: AppDatatypeDraft) =
        id.loadAppAndInvoke({ it.upsertDevelopmentVersionDatatype(datatype) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun removeNextVersionDatatype(id: UUID, datatypeName: String) =
        id.loadAppAndInvoke({ it.removeDevelopmentVersionDatatype(datatypeName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun upsertNextVersionReport(id: UUID, report: AppReport) =
        id.loadAppAndInvoke({ it.upsertDevelopmentVersionReport(report) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun removeNextVersionReport(id: UUID, reportName: String) =
        id.loadAppAndInvoke({ it.removeDevelopmentVersionReport(reportName) }) { _, app ->
            commandPersistence.upsert(app).map { it.developmentVersion!! }
        }

    override fun releaseNextVersion(id: UUID, releaseNotes: AppVersionReleaseNotes) =
        id.loadAppAndInvoke({ it.releaseDevelopmentVersion(releaseNotes) }) { _, app ->
            commandPersistence.upsert(app).map { it.latestVersion!! }
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
