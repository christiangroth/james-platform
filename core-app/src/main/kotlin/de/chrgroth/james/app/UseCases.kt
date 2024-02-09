package de.chrgroth.james.app

import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import de.chrgroth.james.createValidation
import de.chrgroth.james.typesystem.DataobjectFieldSpecFormat
import java.util.UUID

interface AppLifecycleUseCases {
    fun create(name: String, developerId: UUID, description: String? = null): ValidatedNel<DomainError, App>
    fun changeReleaseNoteTitle(id: UUID, version: Semver, title: String): ValidatedNel<DomainError, AppVersion>
    fun changeReleaseNoteNotes(id: UUID, version: Semver, notes: String): ValidatedNel<DomainError, AppVersion>
    fun changeReleaseNoteFeatures(id: UUID, version: Semver, features: List<String>): ValidatedNel<DomainError, AppVersion>
    fun changeReleaseNoteBugfixes(id: UUID, version: Semver, bugfixes: List<String>): ValidatedNel<DomainError, AppVersion>
    fun changeReleaseNoteMisc(id: UUID, version: Semver, misc: List<String>): ValidatedNel<DomainError, AppVersion>
    fun discontinue(id: UUID): ValidatedNel<DomainError, App>
    fun delete(id: UUID): ValidatedNel<DomainError, Unit>
}

interface AppVersionDevelopmentUseCases {
    fun changeReleaseNoteTitle(id: UUID, title: String): ValidatedNel<DomainError, AppVersionDraft>
    fun changeReleaseNoteNotes(id: UUID, notes: String): ValidatedNel<DomainError, AppVersionDraft>
    fun changeReleaseNoteFeatures(id: UUID, features: List<String>): ValidatedNel<DomainError, AppVersionDraft>
    fun changeReleaseNoteBugfixes(id: UUID, bugfixes: List<String>): ValidatedNel<DomainError, AppVersionDraft>
    fun changeReleaseNoteMisc(id: UUID, misc: List<String>): ValidatedNel<DomainError, AppVersionDraft>

    fun addDatatype(id: UUID, datatypeName: String): ValidatedNel<DomainError, AppVersionDraft>
    @Suppress("LongParameterList")
    fun changeDatatype(
        id: UUID,
        name: String,
        newName: String?,
        displayName: String,
        content: String,
        format: DataobjectFieldSpecFormat,
        description: String?,
    ): ValidatedNel<DomainError, AppVersionDraft>
    fun removeDatatype(id: UUID, datatypeName: String): ValidatedNel<DomainError, AppVersionDraft>

    fun addReport(id: UUID, reportName: String): ValidatedNel<DomainError, AppVersionDraft>
    fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): ValidatedNel<DomainError, AppVersionDraft>
    fun removeReport(id: UUID, reportName: String): ValidatedNel<DomainError, AppVersionDraft>

    fun release(id: UUID): ValidatedNel<DomainError, AppVersion>
}

internal class AppLifecycleUseCasesService(
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
    private val activeUsersCache: ActiveUsersCache,
) : AppLifecycleUseCases {

    override fun create(name: String, developerId: UUID, description: String?): ValidatedNel<DomainError, App> =
        createValidation(
            errorCondition = !activeUsersCache.contains(developerId),
            domainErrorCode = AppDomainErrorCodes.APP_DEVELOPER_UNKNOWN,
            errorMessage = null,
        ) {}.andThen {
            App.create(name = name, developerId = developerId, description = description).andThen {
                commandPersistence.upsert(it)
            }
        }

    override fun changeReleaseNoteTitle(id: UUID, version: Semver, title: String): ValidatedNel<DomainError, AppVersion> =
        changeReleaseNoteAspect(id, version) { it.changeReleaseNoteTitle(version, title) }

    override fun changeReleaseNoteNotes(id: UUID, version: Semver, notes: String): ValidatedNel<DomainError, AppVersion> =
        changeReleaseNoteAspect(id, version) { it.changeReleaseNoteNotes(version, notes) }

    override fun changeReleaseNoteFeatures(id: UUID, version: Semver, features: List<String>): ValidatedNel<DomainError, AppVersion> =
        changeReleaseNoteAspect(id, version) { it.changeReleaseNoteFeatures(version, features) }

    override fun changeReleaseNoteBugfixes(id: UUID, version: Semver, bugfixes: List<String>): ValidatedNel<DomainError, AppVersion> =
        changeReleaseNoteAspect(id, version) { it.changeReleaseNoteBugfixes(version, bugfixes) }

    override fun changeReleaseNoteMisc(id: UUID, version: Semver, misc: List<String>): ValidatedNel<DomainError, AppVersion> =
        changeReleaseNoteAspect(id, version) { it.changeReleaseNoteMisc(version, misc) }

    private fun changeReleaseNoteAspect(id: UUID, version: Semver, block: (App) -> ValidatedNel<DomainError, App>): ValidatedNel<DomainError, AppVersion> =
        queryPersistence.getOrError(id).andThen {
            block(it)
        }.andThen {
            commandPersistence.upsert(it)
        }.map { app ->
            app.releasedVersions.firstOrNull { it.version == version }!!
        }

    override fun discontinue(id: UUID): ValidatedNel<DomainError, App> =
        queryPersistence.getOrError(id).andThen {
            it.discontinue()
        }.andThen {
            commandPersistence.upsert(it)
        }

    override fun delete(id: UUID): ValidatedNel<DomainError, Unit> =
        queryPersistence.getOrError(id).andThen {
            it.verifyDeletion()
        }.andThen {
            commandPersistence.delete(id)
        }
}

internal class AppVersionDevelopmentUseCasesService(
    private val queryPersistence: AppQueryPersistencePort,
    private val commandPersistence: AppCommandPersistencePort,
    private val eventBus: EventBus,
) : AppVersionDevelopmentUseCases {

    override fun changeReleaseNoteTitle(id: UUID, title: String): ValidatedNel<DomainError, AppVersionDraft> =
        changeReleaseNoteAspect(id) { it.changeNextVersionReleaseNoteTitle(title) }

    override fun changeReleaseNoteNotes(id: UUID, notes: String): ValidatedNel<DomainError, AppVersionDraft> =
        changeReleaseNoteAspect(id) { it.changeNextVersionReleaseNoteNotes(notes) }

    override fun changeReleaseNoteFeatures(id: UUID, features: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        changeReleaseNoteAspect(id) { it.changeNextVersionReleaseNoteFeatures(features) }

    override fun changeReleaseNoteBugfixes(id: UUID, bugfixes: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        changeReleaseNoteAspect(id) { it.changeNextVersionReleaseNoteBugfixes(bugfixes) }

    override fun changeReleaseNoteMisc(id: UUID, misc: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        changeReleaseNoteAspect(id) { it.changeNextVersionReleaseNoteMisc(misc) }

    private fun changeReleaseNoteAspect(id: UUID, block: (App) -> ValidatedNel<DomainError, App>): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            block(it)
        }.andThen {
            commandPersistence.upsert(it)
        }.map { app ->
            app.nextVersionDraft
        }

    override fun addDatatype(id: UUID, datatypeName: String): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.addNextVersionDraftDatatype(datatypeName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    @Suppress("LongParameterList")
    override fun changeDatatype(
        id: UUID,
        name: String,
        newName: String?,
        displayName: String,
        content: String,
        format: DataobjectFieldSpecFormat,
        description: String?,
    ): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.changeNextVersionDraftDatatype(name, newName ?: name, displayName, content, format, description)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeDatatype(id: UUID, datatypeName: String): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.removeNextVersionDraftDatatype(datatypeName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun addReport(id: UUID, reportName: String): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.addNextVersionDraftReport(reportName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun changeReport(id: UUID, name: String, source: String, description: String?, newName: String?): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.changeNextVersionDraftReport(name, source, description, newName ?: name)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun removeReport(id: UUID, reportName: String): ValidatedNel<DomainError, AppVersionDraft> =
        queryPersistence.getOrError(id).andThen {
            it.removeNextVersionDraftReport(reportName)
        }.andThen {
            commandPersistence.upsert(it)
        }.map {
            it.nextVersionDraft
        }

    override fun release(id: UUID): ValidatedNel<DomainError, AppVersion> =
        queryPersistence.getOrError(id).andThen {
            it.releaseNextVersionDraft()
        }.andThen { app ->
            commandPersistence.upsert(app).also {
                // TODO #2 unable to reference Datatye in eventing module and parsing from YAML lacks metadata written to comments only
                eventBus.publish(DomainEvent.AppVersionReleased(
                    appId = id,
                    version = app.latestVersion!!.version,
                    datatypesYamlContent = app.latestVersion!!.datatypes.associate { datatype -> datatype.name to datatype.dump(DataobjectFieldSpecFormat.YAML) }
                ))
            }
        }.map {
            it.latestVersion!!
        }
}
