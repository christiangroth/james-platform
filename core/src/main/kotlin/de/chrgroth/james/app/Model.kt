package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.jsonschema.computeCompatibility
import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor
import de.chrgroth.james.app.jsonschema.loadAsTopLevelObjectSchema
import de.chrgroth.james.computeNext
import de.chrgroth.james.trimToNull
import java.util.UUID

enum class AppStatus(val allowsChanges: Boolean) {
    DEVELOPMENT(true), ACTIVE(true), DISCONTINUED(false)
}

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class App(
    val id: UUID,
    val name: String,
    val developer: UUID,
    val description: String?,
    val discontinued: Boolean,
    val developmentVersion: AppVersionDraft?,
    val versions: List<AppVersion>,
) {

    companion object {
        internal fun validateName(name: String): Maybe<String> {
            if (name.isBlank()) {
                return Error(
                    code = AppErrorCodes.APP_NAME_BLANK,
                    details = null,
                )
            }

            return Result(name.trim())
        }

        internal fun create(name: String, developerId: UUID, description: String?): Maybe<App> =
            validateName(name).map { validName ->
                App(
                    id = UUID.randomUUID(),
                    name = validName,
                    developer = developerId,
                    description = description.trimToNull(),
                    discontinued = false,
                    developmentVersion = AppVersionDraft(
                        datatypes = emptySet(),
                        reports = emptySet(),
                    ),
                    versions = emptyList(),
                )
            }
    }

    val status by lazy {
        when {
            discontinued -> AppStatus.DISCONTINUED
            versions.isEmpty() -> AppStatus.DEVELOPMENT
            else -> AppStatus.ACTIVE
        }
    }

    internal val latestVersion by lazy {
        versions.firstOrNull()
    }

    internal fun createDevelopmentVersion() = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion != null -> Error(
            code = AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
            details = null,
        )
        else -> latestVersion?.let {
            Result(copy(developmentVersion = AppVersionDraft(datatypes = it.datatypes.map { datatype ->
                AppDatatypeDraft(name = datatype.name, schemaContent = datatype.schemaContent, description = datatype.description)
            }.toSet(), reports = it.reports.toSet())))
        } ?: Result(copy(developmentVersion = AppVersionDraft(
            datatypes = emptySet(),
            reports = emptySet(),
        )))
    }

    internal fun upsertDevelopmentVersionDatatype(datatype: AppDatatypeDraft) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion == null -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        else -> developmentVersion.upsertDatatype(datatype).map { copy(developmentVersion = it) }
    }

    internal fun removeDevelopmentVersionDatatype(datatypeName: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion == null -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        else -> developmentVersion.removeDatatype(datatypeName.trim()).map { copy(developmentVersion = it) }
    }

    internal fun upsertDevelopmentVersionReport(report: AppReport) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion == null -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        else -> developmentVersion.upsertReport(report).map { copy(developmentVersion = it) }
    }

    internal fun removeDevelopmentVersionReport(reportName: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion == null -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        else -> developmentVersion.removeReport(reportName.trim()).map { copy(developmentVersion = it) }
    }

    internal fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        developmentVersion == null -> Error(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        releaseNotes.note.isBlank() -> Error(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_RELEASE_NOTES_BLANK,
            details = null,
        )
        else -> {
            val newAppVersion = AppVersion(
                version = releaseNotes.computeVersion(latestVersion, developmentVersion),
                releaseNotes = releaseNotes,
                datatypes = developmentVersion.createDatatypes(latestVersion),
                reports = developmentVersion.reports.toSet(),
            )
            Result(copy(developmentVersion = null, versions = listOf(newAppVersion) + versions))
        }
    }

    internal fun discontinue() = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        else -> Result(copy(discontinued = true))
    }

    internal fun verifyDeletion() = when {
        status != AppStatus.DISCONTINUED -> Error(
            code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
            details = null,
        )
        else -> Result(Unit)
    }
}

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<AppDatatype>,
    val reports: Set<AppReport>,
)

data class AppVersionDraft(
    val datatypes: Set<AppDatatypeDraft>,
    val reports: Set<AppReport>,
) {

    internal fun createDatatypes(latest: AppVersion?) = datatypes.map { draft ->
        val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }

        AppDatatype(name = draft.name, version = when {
            existingDatatype == null -> 0
            existingDatatype.schemaContent == draft.schemaContent -> existingDatatype.version
            else -> existingDatatype.version + 1
        }, schemaContent = draft.schemaContent, description = draft.description)
    }.toSet()

    internal fun upsertDatatype(datatype: AppDatatypeDraft) = when {
        datatype.name.isBlank() -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_BLANK,
            details = null,
        )
        datatype.name.any { !it.isLetter() } -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_LETTERS_ONLY,
            details = null,
        )
        else -> {
            datatype.generateJsonSchema().loadAsTopLevelObjectSchema().map {
                copy(datatypes = datatypes.upsert(datatype))
            }
        }
    }

    internal fun removeDatatype(datatypeName: String) = when {
        datatypes.none { it.name == datatypeName.trim() } -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_DATATYPE_NOT_FOUND,
            details = null,
        )
        else -> Result(copy(datatypes = datatypes.filterNot { it.name == datatypeName.trim() }.toSet()))
    }

    private fun Set<AppDatatypeDraft>.upsert(datatype: AppDatatypeDraft) = filterNot { it.name == datatype.name }.plus(datatype).toSet()

    internal fun upsertReport(report: AppReport) = when {
        report.name.isBlank() -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_REPORT_NAME_BLANK,
            details = null,
        )
        else -> Result(copy(reports = reports.upsert(report)))
    }

    internal fun removeReport(reportName: String) = when {
        reports.none { it.name == reportName.trim() } -> Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_REPORT_NOT_FOUND,
            details = null,
        )
        else -> Result(copy(reports = reports.filterNot { it.name == reportName.trim() }.toSet()))
    }

    private fun Set<AppReport>.upsert(report: AppReport) = filterNot { it.name == report.name }.plus(report).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class AppVersionReleaseNotes(
    val changeType: AppVersionChangeType,
    val note: String,
) {
    internal fun computeVersion(latest: AppVersion?, next: AppVersionDraft): Semver {
        return if (latest == null) {

            // it's the very first version
            Semver(major = 0, minor = 1, patch = 0)
        } else {
            val isBreaking = isBreaking(latest, next)
            latest.version.computeNext(isBreaking, this)
        }
    }

    internal fun isBreaking(latest: AppVersion, next: AppVersionDraft): Boolean {
        val modelRenamedOrDeleted = latest.datatypes.any { existingDatatype ->
            next.datatypes.none { it.name == existingDatatype.name }
        }
        if (modelRenamedOrDeleted) {
            return true
        }

        return latest.datatypes.asSequence().mapNotNull { existingDatatype ->
            val nextDatatype = next.datatypes.firstOrNull { it.name == existingDatatype.name }
            if (nextDatatype != null && existingDatatype.schemaContent != nextDatatype.schemaContent) {
                existingDatatype to nextDatatype
            } else {
                null
            }
        }.map {
            it.first.generateJsonSchemaContent().loadAsTopLevelObjectSchema() to it.second.generateJsonSchema().loadAsTopLevelObjectSchema()
        }.mapNotNull {
            if (it.first is Result && it.second is Result) {
                (it.first as Result).value to (it.second as Result).value
            } else {
                null
            }
        }.any { it.first.computeCompatibility(it.second) !is Result }
    }
}

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class AppDatatypeDraft(
    val name: String,
    val schemaContent: String,
    val description: String?,
) {
    fun generateJsonSchema(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = null,
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class AppDatatype(
    val name: String,
    val version: Long,
    val schemaContent: String,
    val description: String?,
) {
    fun generateJsonSchemaContent(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = version.toString(),
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class AppReport(
    val name: String,
    val description: String?,
    val source: String?,
)
