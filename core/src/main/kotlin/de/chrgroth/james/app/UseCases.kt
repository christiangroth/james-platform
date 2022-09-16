package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.user.UserQueryPersistencePort
import java.util.UUID

// TODO #25 verify developer exists and is active

interface AppLifecycleUseCases {
    fun create(name: String, developerId: UUID, description: String? = null): Maybe<App>
    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion>
    fun changeVersionReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

interface AppVersionDevelopmentUseCases {
    fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>
    fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
}

internal class AppLifecycleUseCasesService(
    private val userQueryPersistence: UserQueryPersistencePort,
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppLifecycleUseCases {

    override fun create(name: String, developerId: UUID, description: String?) =
        userQueryPersistence.getOrError(developerId).flatMap { developer ->
            App.create(name, developer.id, description)
        }.flatMap {
            commandPersistence.upsert(it)
        }

    override fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.createDevelopmentVersion()
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun releaseNextVersion(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion> =
        queryPersistence.getOrError(id).flatMap {
            it.releaseDevelopmentVersion(changeType, note)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.latestVersion!!
        }

    override fun changeVersionReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion> =
        queryPersistence.getOrError(id).flatMap {
            it.changeVersionReleaseNote(version, note)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map { app ->
            app.versions.firstOrNull { it.version == version }!!
        }

    // TODO #5 check if user data is still present
    override fun discontinue(id: UUID): Maybe<App> =
        queryPersistence.getOrError(id).flatMap {
            it.discontinue()
        }.flatMap {
            commandPersistence.upsert(it)
        }

    override fun delete(id: UUID) =
        queryPersistence.getOrError(id).flatMap {
            it.verifyDeletion()
        }.flatMap {
            commandPersistence.delete(id)
        }
}

internal class AppVersionDevelopmentUseCasesService(
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppVersionDevelopmentUseCases {

    override fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.createDevelopmentVersionDatatype(datatypeName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.renameDevelopmentVersionDatatype(oldName, newName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun updateNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.updateDevelopmentVersionDatatype(name, schemaContent, description)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun removeNextVersionDatatype(id: UUID, datatypeName: String) =
        queryPersistence.getOrError(id).flatMap {
            it.removeDevelopmentVersionDatatype(datatypeName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.createDevelopmentVersionReport(reportName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.renameDevelopmentVersionReport(oldName, newName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun updateNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.updateDevelopmentVersionReport(name, source, description)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }

    override fun removeNextVersionReport(id: UUID, reportName: String) =
        queryPersistence.getOrError(id).flatMap {
            it.removeDevelopmentVersionReport(reportName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.developmentVersion!!
        }
}
