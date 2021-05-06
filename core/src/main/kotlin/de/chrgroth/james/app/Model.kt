package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.computeNext
import de.chrgroth.james.generateJsonSchema
import de.chrgroth.james.isBreakingTo
import de.chrgroth.james.parseJsonSchema
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
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion != null -> Maybe.Error(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
            else -> latestVersion?.let {
                Maybe.Result(copy(
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
                ))
            } ?: Maybe.Result(copy(developmentVersion = AppVersionDraft()))
        }

    internal fun updateDevelopmentVersionDatatype(datatype: AppDatatypeDraft) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            else -> developmentVersion.upsertDatatype(datatype).map { copy(developmentVersion = it) }
        }

    internal fun removeDevelopmentVersionDatatype(datatypeName: String) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            else -> developmentVersion.removeDatatype(datatypeName).map { copy(developmentVersion = it) }
        }

    internal fun updateDevelopmentVersionReport(report: AppReport) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            else -> developmentVersion.upsertReport(report).map { copy(developmentVersion = it) }
        }

    internal fun removeDevelopmentVersionReport(reportName: String) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            else -> developmentVersion.removeReport(reportName).map { copy(developmentVersion = it) }
        }

    internal fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            releaseNotes.note.isBlank() -> Maybe.Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_RELEASE_NOTES_BLANK)
            else -> {
                Maybe.Result(copy(
                    developmentVersion = null,
                    versions = versions.plus(
                        AppVersion(
                            version = releaseNotes.computeVersion(latestVersion, developmentVersion),
                            releaseNotes = releaseNotes,
                            datatypes = developmentVersion.createDatatypes(latestVersion),
                            reports = developmentVersion.reports.toSet(),
                        )
                    )))
            }
        }

    internal fun discontinue() =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            else -> Maybe.Result(copy(discontinued = true))
        }

    internal fun canBeDeleted() =
        when {
            status != AppStatus.DISCONTINUED -> Maybe.Error(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
            else -> Maybe.Result(Unit)
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
            datatype.name.isBlank() -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_BLANK)
            datatype.name.any { !it.isLetter() } -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_LETTERS_ONLY)
            else -> {
                datatype.generateJsonSchema().parseJsonSchema().map {
                    copy(datatypes = datatypes.upsert(datatype))
                }
            }
        }

    internal fun removeDatatype(datatypeName: String) =
        when {
            datatypes.none { it.name == datatypeName } -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_DATATYPE_NOT_FOUND)
            else -> Maybe.Result(copy(datatypes = datatypes.filterNot { it.name == datatypeName }.toSet()))
        }

    private fun Set<AppDatatypeDraft>.upsert(datatype: AppDatatypeDraft) =
        filterNot { it.name == datatype.name }.plus(datatype).toSet()

    internal fun upsertReport(report: AppReport) =
        when {
            report.name.isBlank() -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_REPORT_NAME_BLANK)
            else -> Maybe.Result(copy(reports = reports.upsert(report)))
        }

    internal fun removeReport(reportName: String) =
        when {
            reports.none { it.name == reportName } -> Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_REPORT_NOT_FOUND)
            else -> Maybe.Result(copy(reports = reports.filterNot { it.name == reportName }.toSet()))
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
            .filter { existingDatatype -> next.datatypes.any { it.name == existingDatatype.name } }
            .map { existingDatatype -> existingDatatype to next.datatypes.first { it.name == existingDatatype.name } }
            .filter { it.first.schemaContent != it.second.schemaContent }
            .map {
                it.first.generateJsonSchema().parseJsonSchema() to
                        it.second.generateJsonSchema().parseJsonSchema()
            }
            .filter { it.first is Maybe.Result && it.second is Maybe.Result }
            .map { (it.first as Maybe.Result).value to (it.second as Maybe.Result).value }
            .any { it.first.isBreakingTo(it.second) }
    }
}

data class AppDatatypeDraft(
    val name: String,
    val schemaContent: String? = null,
    val description: String? = null,
)

data class AppDatatype(
    val name: String,
    val version: Long,
    val schemaContent: String? = null,
    val description: String? = null,
)

data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String? = null,
)
