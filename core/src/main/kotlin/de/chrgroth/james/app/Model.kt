package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.jsonschema.computeCompatibility
import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor
import de.chrgroth.james.app.jsonschema.loadAsTopLevelObjectSchema
import de.chrgroth.james.computeNext
import de.chrgroth.james.foldErrors
import de.chrgroth.james.throwOnError
import de.chrgroth.james.trimToNull
import de.chrgroth.james.validateNotBlank
import de.chrgroth.james.validateNotNegative
import java.util.UUID

// TODO #25 make return types explicit (check all files)

// TODO #25 make copy calls explicit (this file only)

enum class AppStatus(val allowsChanges: Boolean) {
    DEVELOPMENT(true), ACTIVE(true), DISCONTINUED(false)
}

data class App private constructor(
    val id: UUID,
    private var nameField: String,
    val developer: UUID,
    private var descriptionField: String?,
    val discontinued: Boolean,
    val developmentVersion: AppVersionDraft?,
    val versions: List<AppVersion>,
) {

    // TODO #25 test and enhance testcases above
    companion object {
        private fun validateName(name: String) = validateNotBlank(name, AppErrorCodes.APP_NAME_BLANK)

        fun create(name: String, developerId: UUID, description: String?): Maybe<App> =
            validateName(name).map { validName ->
                App(
                    id = UUID.randomUUID(),
                    nameField = validName,
                    developer = developerId,
                    descriptionField = description.trimToNull(),
                    discontinued = false,
                    developmentVersion = AppVersionDraft.create(),
                    versions = emptyList(),
                )
            }
    }

    // TODO #25 test exception usecases
    init {
        nameField = nameField.trim()
        descriptionField = descriptionField.trimToNull()

        listOf(validateName(nameField)).foldErrors<AppReport>().throwOnError(javaClass.simpleName)
    }

    val name get() = nameField
    val description get() = descriptionField

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
            Result(copy(developmentVersion = AppVersionDraft.create(datatypes = it.datatypes.map { datatype ->
                AppDatatypeDraft.create(datatype)
            }.toSet(), reports = it.reports)))
        } ?: Result(copy(developmentVersion = AppVersionDraft.create()))
    }

    // TODO #25 finetuning
    // TODO #25 check name collision
    internal fun createDevelopmentVersionDatatype(datatypeName: String) =
        upsertDevelopmentVersionDatatype(AppDatatypeDraft.create(name = datatypeName, schemaContent = "", description = null))

    // TODO #25 check name collision, if changed
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
        else -> developmentVersion.removeDatatype(datatypeName).map { copy(developmentVersion = it) }
    }

    // TODO #25 finetuning
    // TODO #25 check name collision
    internal fun createDevelopmentVersionReport(reportName: String) =
        upsertDevelopmentVersionReport(AppReport.create(name = reportName, description = null, source = null))

    // TODO #25 check name collision, if changed
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
        else -> developmentVersion.removeReport(reportName).map { copy(developmentVersion = it) }
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
            val newAppVersion = AppVersion.create(releaseNotes, developmentVersion, latestVersion)
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

data class AppVersion private constructor(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<AppDatatype>,
    val reports: Set<AppReport>,
) {

    companion object {
        fun create(releaseNotes: AppVersionReleaseNotes, developmentVersion: AppVersionDraft, latestVersion: AppVersion?) =
            AppVersion(
                version = releaseNotes.computeVersion(latestVersion, developmentVersion),
                releaseNotes = releaseNotes,
                datatypes = developmentVersion.createDatatypes(latestVersion),
                reports = developmentVersion.reports.toSet(),
            )
    }
}

data class AppVersionDraft private constructor(
    val datatypes: Set<AppDatatypeDraft>,
    val reports: Set<AppReport>,
) {

    companion object {
        fun create() = create(datatypes = emptySet(), reports = emptySet())
        fun create(appVersion: AppVersion) = create(
            datatypes = appVersion.datatypes.map { AppDatatypeDraft.create(it) }.toSet(),
            reports = appVersion.reports
        )

        fun create(datatypes: Set<AppDatatypeDraft>, reports: Set<AppReport>) =
            AppVersionDraft(datatypes = datatypes, reports = reports)
    }

    internal fun createDatatypes(latest: AppVersion?) = datatypes.map { draft ->
        val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }
        val newVersion = when {
            existingDatatype == null -> 0
            existingDatatype.schemaContent == draft.schemaContent -> existingDatatype.version
            else -> existingDatatype.version + 1
        }

        AppDatatype.create(draft, newVersion)
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
        else -> Result(copy(reports = reports.filterNot { it.name == reportName }.toSet()))
    }

    private fun Set<AppReport>.upsert(report: AppReport) = filterNot { it.name == report.name }.plus(report).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

// TODO #25 what about fixing typos in note?
data class AppVersionReleaseNotes private constructor(
    val changeType: AppVersionChangeType,
    private var noteField: String,
) {

    // TODO #25 add validation
    // TODO #25 test and enhance testcases above
    companion object {
        private fun validateNote(note: String) =
            validateNotBlank(note, AppErrorCodes.APP_VERSION_RELEASE_NOTE_BLANK)

        fun create(changeType: AppVersionChangeType, note: String) = AppVersionReleaseNotes(
            changeType = changeType,
            noteField = note,
        )
    }

    // TODO #25 test exception usecases
    init {
        noteField = noteField.trim()

        listOf(validateNote(noteField)).foldErrors<AppReport>().throwOnError(javaClass.simpleName)
    }

    val note get() = noteField

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

data class AppDatatypeDraft private constructor(
    private var nameField: String,
    private var schemaContentField: String,
    private var descriptionField: String?,
) {

    // TODO #25 add validation
    // TODO #25 test and enhance testcases above
    companion object {
        private fun validateName(name: String) =
            validateNotBlank(name, AppErrorCodes.APP_DATATYPE_NAME_BLANK)

        fun create(name: String, schemaContent: String, description: String?): AppDatatypeDraft =
            AppDatatypeDraft(
                nameField = name,
                schemaContentField = schemaContent,
                descriptionField = description,
            )

        fun create(datatype: AppDatatype): AppDatatypeDraft =
            AppDatatypeDraft(
                nameField = datatype.name,
                schemaContentField = datatype.schemaContent,
                descriptionField = datatype.description,
            )
    }

    // TODO #25 test exception usecases
    init {
        nameField = nameField.trim()
        schemaContentField = schemaContentField.trim()
        descriptionField = descriptionField.trimToNull()

        // schemaContent is allowed to be blank in a draft
        listOf(validateName(nameField)).foldErrors<AppReport>().throwOnError(javaClass.simpleName)
    }

    val name get() = nameField
    val schemaContent get() = schemaContentField
    val description get() = descriptionField

    fun generateJsonSchema(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = null,
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

data class AppDatatype private constructor(
    private var nameField: String,
    private var versionField: Long,
    private var schemaContentField: String,
    private var descriptionField: String?,
) {

    // TODO #25 add validation
    // TODO #25 test and enhance testcases above
    companion object {
        private fun validateName(name: String) =
            validateNotBlank(name, AppErrorCodes.APP_DATATYPE_NAME_BLANK)

        private fun validateVersion(version: Long) =
            validateNotNegative(version, AppErrorCodes.APP_DATATYPE_VERSION_NEGATIVE)

        private fun validateSchemaContent(schemaContent: String) =
            validateNotBlank(schemaContent, AppErrorCodes.APP_DATATYPE_SCHEMA_CONTENT_BLANK)

        fun create(draft: AppDatatypeDraft, version: Long) =
            AppDatatype(
                nameField = draft.name,
                versionField = version,
                schemaContentField = draft.schemaContent,
                descriptionField = draft.description
            )
    }

    // TODO #25 test exception usecases
    init {
        nameField = nameField.trim()
        schemaContentField = schemaContentField.trim()
        descriptionField = descriptionField.trimToNull()

        listOf(validateName(nameField), validateVersion(versionField), validateSchemaContent(schemaContentField))
            .foldErrors<AppReport>().throwOnError(javaClass.simpleName)
    }

    val name get() = nameField
    val version get() = versionField
    val schemaContent get() = schemaContentField
    val description get() = descriptionField

    fun generateJsonSchemaContent(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = version.toString(),
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    )
}

data class AppReport private constructor(
    private var nameField: String,
    private var descriptionField: String?,
    private var sourceField: String?,
) {

    // TODO #25 add validation
    // TODO #25 test and enhance testcases above
    companion object {
        private fun validateName(name: String) =
            validateNotBlank(name, AppErrorCodes.APP_REPORT_NAME_BLANK)

        fun create(name: String, description: String?, source: String?) = AppReport(
            nameField = name,
            descriptionField = description,
            sourceField = source,
        )
    }

    // TODO #25 test exception usecases
    init {
        nameField = nameField.trim()
        descriptionField = descriptionField.trimToNull()
        sourceField = sourceField.trimToNull()

        listOf(validateName(nameField)).foldErrors<AppReport>().throwOnError(javaClass.simpleName)
    }

    val name get() = nameField
    val description get() = descriptionField
    val source get() = sourceField
}
