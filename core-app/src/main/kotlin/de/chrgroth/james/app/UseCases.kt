package de.chrgroth.james.app

import arrow.core.Validated
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.shrink
import de.chrgroth.james.user.UserQueryPersistencePort
import java.util.UUID

interface AppLifecycleUseCases {
    fun create(name: String, developerId: UUID, description: String? = null): Maybe<App>
    fun changeReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>
}

interface AppVersionDevelopmentUseCases {
    fun addDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>
    fun changeDatatype(id: UUID, name: String, schemaContent: String, description: String?, newName: String?): Maybe<AppVersionDraft>
    fun removeDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    fun addReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): Maybe<AppVersionDraft>
    fun removeReport(id: UUID, reportName: String): Maybe<AppVersionDraft>

    fun release(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion>
}

internal class AppLifecycleUseCasesService(
    // TODO #32 remove dependency
    private val userQueryPersistence: UserQueryPersistencePort,
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
) : AppLifecycleUseCases {

    // TODO #22 verify developer is active
    override fun create(name: String, developerId: UUID, description: String?): Maybe<App> {
        // TODO #29 this is a hack
        return when (val r = userQueryPersistence.getOrError(developerId)) {
            is Validated.Invalid -> {
                Maybe.Errors(r.value.map {
                    Maybe.Error<App>(it.code, it.details)
                }).shrink()!!
            }

            is Validated.Valid -> {
                App.create(name = name, developerId = r.value.id, description = description).flatMap {
                    commandPersistence.upsert(it)
                }
            }

        }
    }

    override fun changeReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion> =
        queryPersistence.getOrError(id).flatMap {
            it.changeReleaseNote(version, note)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map { app ->
            app.releasedVersions.firstOrNull { it.version == version }!!
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

    override fun addDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.addNextVersionDraftDatatype(datatypeName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun changeDatatype(id: UUID, name: String, schemaContent: String, description: String?, newName: String?): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.changeNextVersionDraftDatatype(name, schemaContent, description, newName ?: name)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeDatatype(id: UUID, datatypeName: String) =
        queryPersistence.getOrError(id).flatMap {
            it.removeNextVersionDraftDatatype(datatypeName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun addReport(id: UUID, reportName: String): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.addNextVersionDraftReport(reportName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): Maybe<AppVersionDraft> =
        queryPersistence.getOrError(id).flatMap {
            it.changeNextVersionDraftReport(name, source, description, newName ?: name)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeReport(id: UUID, reportName: String) =
        queryPersistence.getOrError(id).flatMap {
            it.removeNextVersionDraftReport(reportName)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun release(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion> =
        queryPersistence.getOrError(id).flatMap {
            it.releaseNextVersionDraft(changeType, note)
        }.flatMap {
            commandPersistence.upsert(it)
        }.map {
            it.latestVersion!!
        }
}
