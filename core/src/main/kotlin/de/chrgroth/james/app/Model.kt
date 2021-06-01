package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.computeNext
import de.chrgroth.james.app.jsonschema.computeCompatibility
import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor
import de.chrgroth.james.app.jsonschema.loadAsTopLevelObjectSchema
import java.util.UUID

enum class AppStatus(val allowsChanges: Boolean) {
    DEVELOPMENT(true), ACTIVE(true), DISCONTINUED(false)
}

data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val discontinued: Boolean = false,
    val developmentVersion: AppVersionDraft? = null,
    val versions: Set<AppVersion> = emptySet(),
) {
    val status by lazy {
        when {
            discontinued -> AppStatus.DISCONTINUED
            versions.isEmpty() -> AppStatus.DEVELOPMENT
            else -> AppStatus.ACTIVE
        }
    }

    internal val latestVersion by lazy {
        versions.maxByOrNull { it.version }
    }

    internal fun createDevelopmentVersion() =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion != null -> Error(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS, null)
            else -> latestVersion?.let {
                Result(
                    copy(
                        developmentVersion = AppVersionDraft(
                            datatypes = it.datatypes.map { datatype ->
                                AppDatatypeDraft(
                                    name = datatype.name,
                                    schemaContent = datatype.schemaContent,
                                    description = datatype.description
                                )
                            }.toSet(),
                            reports = it.reports.toSet()
                        )
                    )
                )
            } ?: Result(copy(developmentVersion = AppVersionDraft()))
        }

    internal fun updateDevelopmentVersionDatatype(datatype: AppDatatypeDraft) =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion == null -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING, null)
            else -> developmentVersion.upsertDatatype(datatype).map { copy(developmentVersion = it) }
        }

    internal fun removeDevelopmentVersionDatatype(datatypeName: String) =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion == null -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING, null)
            else -> developmentVersion.removeDatatype(datatypeName).map { copy(developmentVersion = it) }
        }

    internal fun updateDevelopmentVersionReport(report: AppReport) =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion == null -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING, null)
            else -> developmentVersion.upsertReport(report).map { copy(developmentVersion = it) }
        }

    internal fun removeDevelopmentVersionReport(reportName: String) =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion == null -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING, null)
            else -> developmentVersion.removeReport(reportName).map { copy(developmentVersion = it) }
        }

    internal fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            developmentVersion == null -> Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING, null)
            releaseNotes.note.isBlank() -> Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_RELEASE_NOTES_BLANK, null)
            else -> {
                Result(
                    copy(
                        developmentVersion = null,
                        versions = versions.plus(
                            AppVersion(
                                version = releaseNotes.computeVersion(latestVersion, developmentVersion),
                                releaseNotes = releaseNotes,
                                datatypes = developmentVersion.createDatatypes(latestVersion),
                                reports = developmentVersion.reports.toSet(),
                            )
                        )
                    )
                )
            }
        }

    internal fun discontinue() =
        when {
            !status.allowsChanges -> Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED, null)
            else -> Result(copy(discontinued = true))
        }

    internal fun canBeDeleted() =
        when {
            status != AppStatus.DISCONTINUED -> Error(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED, null)
            else -> Result(Unit)
        }
}

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<AppDatatype> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
)

data class AppVersionDraft(
    val datatypes: Set<AppDatatypeDraft> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
) {

    fun createDatatypes(latest: AppVersion?) =
        datatypes.map { draft ->
            val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }

            AppDatatype(
                name = draft.name,
                version = when {
                    existingDatatype == null -> 0
                    existingDatatype.schemaContent == draft.schemaContent -> existingDatatype.version
                    else -> existingDatatype.version + 1
                },
                schemaContent = draft.schemaContent,
                description = draft.description
            )
        }.toSet()

    internal fun upsertDatatype(datatype: AppDatatypeDraft) =
        when {
            datatype.name.isBlank() -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_BLANK, null)
            datatype.name.any { !it.isLetter() } -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_LETTERS_ONLY, null)
            else -> {
                datatype.generateJsonSchema().loadAsTopLevelObjectSchema().map {
                    copy(datatypes = datatypes.upsert(datatype))
                }
            }
        }

    internal fun removeDatatype(datatypeName: String) =
        when {
            datatypes.none { it.name == datatypeName } -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_DATATYPE_NOT_FOUND, null)
            else -> Result(copy(datatypes = datatypes.filterNot { it.name == datatypeName }.toSet()))
        }

    private fun Set<AppDatatypeDraft>.upsert(datatype: AppDatatypeDraft) =
        filterNot { it.name == datatype.name }.plus(datatype).toSet()

    internal fun upsertReport(report: AppReport) =
        when {
            report.name.isBlank() -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_REPORT_NAME_BLANK, null)
            else -> Result(copy(reports = reports.upsert(report)))
        }

    internal fun removeReport(reportName: String) =
        when {
            reports.none { it.name == reportName } -> Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_REPORT_NOT_FOUND, null)
            else -> Result(copy(reports = reports.filterNot { it.name == reportName }.toSet()))
        }

    private fun Set<AppReport>.upsert(report: AppReport) =
        filterNot { it.name == report.name }.plus(report).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

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

        return latest.datatypes.asSequence()
            .mapNotNull { existingDatatype ->
                val nextDatatype = next.datatypes.firstOrNull { it.name == existingDatatype.name }
                if(nextDatatype != null && existingDatatype.schemaContent != nextDatatype.schemaContent) {
                    existingDatatype to nextDatatype
                } else {
                    null
                }
            }
            .map {
                it.first.generateJsonSchemaContent().loadAsTopLevelObjectSchema() to
                        it.second.generateJsonSchema().loadAsTopLevelObjectSchema()
            }
            .mapNotNull {
                if(it.first is Result && it.second is Result) {
                    (it.first as Result).value to (it.second as Result).value
                } else {
                    null
                }
            }
            .any { it.first.computeCompatibility(it.second) !is Result }
    }
}

data class AppDatatypeDraft(
    val name: String,
    val schemaContent: String,
    val description: String? = null,
) {
    fun generateJsonSchema(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = null,
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

data class AppDatatype(
    val name: String,
    val version: Long,
    val schemaContent: String,
    val description: String? = null,
) {
    fun generateJsonSchemaContent(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = version.toString(),
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String? = null,
)
