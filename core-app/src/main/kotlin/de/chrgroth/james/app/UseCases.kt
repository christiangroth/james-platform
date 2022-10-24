package de.chrgroth.james.app

import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Error
import de.chrgroth.james.user.UserQueryPersistencePort
import java.util.UUID

interface AppLifecycleUseCases {
    fun create(name: String, developerId: UUID, description: String? = null): ValidatedNel<Error, App>
    fun changeReleaseNote(id: UUID, version: Semver, note: String): ValidatedNel<Error, AppVersion>
    fun discontinue(id: UUID): ValidatedNel<Error, App>
    fun delete(id: UUID): ValidatedNel<Error, Unit>
}

interface AppVersionDevelopmentUseCases {
    fun addDatatype(id: UUID, datatypeName: String): ValidatedNel<Error, AppVersionDraft>
    fun changeDatatype(id: UUID, name: String, schemaContent: String, description: String?, newName: String?): ValidatedNel<Error, AppVersionDraft>
    fun removeDatatype(id: UUID, datatypeName: String): ValidatedNel<Error, AppVersionDraft>

    fun addReport(id: UUID, reportName: String): ValidatedNel<Error, AppVersionDraft>
    fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): ValidatedNel<Error, AppVersionDraft>
    fun removeReport(id: UUID, reportName: String): ValidatedNel<Error, AppVersionDraft>

    fun release(id: UUID, changeType: AppVersionChangeType, note: String): ValidatedNel<Error, AppVersion>
}

internal class AppLifecycleUseCasesService(
    // TODO #32 remove dependency
    private val userQueryPersistence: UserQueryPersistencePort,
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppLifecycleUseCases {

    // TODO #22 verify developer is active
    override fun create(name: String, developerId: UUID, description: String?): ValidatedNel<Error, App> =
        userQueryPersistence.getOrError(developerId).andThen {
            App.create(name = name, developerId = it.id, description = description).andThen {
                commandPersistence.upsert(it)
            }
        }

    override fun changeReleaseNote(id: UUID, version: Semver, note: String): ValidatedNel<Error, AppVersion> =
        queryPersistence.getOrError(id).andThen {
            it.changeReleaseNote(version, note)
        }.andThen {
            commandPersistence.upsert(it)
        }.map { app ->
            app.releasedVersions.firstOrNull { it.version == version }!!
        }

    // TODO #5 check if user data is still present
    override fun discontinue(id: UUID): ValidatedNel<Error, App> =
        queryPersistence.getOrError(id).andThen {
            it.discontinue()
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun delete(id: UUID): ValidatedNel<Error, Unit> =
        queryPersistence.getOrError(id).andThen {
            it.verifyDeletion()
        }.andThen {
            commandPersistence.delete(id)
        }
}

internal class AppVersionDevelopmentUseCasesService(
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppVersionDevelopmentUseCases {

    override fun addDatatype(id: UUID, datatypeName: String): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.addNextVersionDraftDatatype(datatypeName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun changeDatatype(id: UUID, name: String, schemaContent: String, description: String?, newName: String?): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.changeNextVersionDraftDatatype(name, schemaContent, description, newName ?: name)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeDatatype(id: UUID, datatypeName: String): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.removeNextVersionDraftDatatype(datatypeName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun addReport(id: UUID, reportName: String): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.addNextVersionDraftReport(reportName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.changeNextVersionDraftReport(name, source, description, newName ?: name)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeReport(id: UUID, reportName: String): ValidatedNel<Error, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.removeNextVersionDraftReport(reportName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun release(id: UUID, changeType: AppVersionChangeType, note: String): ValidatedNel<Error, AppVersion> =
        queryPersistence.getOrError(id).andThen {
            it.releaseNextVersionDraft(changeType, note)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.latestVersion!!
        }
}
