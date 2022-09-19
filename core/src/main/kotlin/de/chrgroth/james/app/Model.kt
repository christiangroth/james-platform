package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.jsonschema.computeCompatibility
import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor
import de.chrgroth.james.app.jsonschema.loadAsTopLevelObjectSchema
import de.chrgroth.james.collectResults
import de.chrgroth.james.computeNext
import de.chrgroth.james.foldErrors
import de.chrgroth.james.shrink
import de.chrgroth.james.trimToNull
import de.chrgroth.james.validateMatches
import de.chrgroth.james.validateNotBlank
import de.chrgroth.james.validateNotNegative
import java.util.UUID

// TODO #25 check/forbid all copy invocations
// TODO #25 add tests for isBreaking and JSON schema generation

// TODO #28 make return types explicit (check all files)
// TODO #28 make copy calls explicit (this file only)

enum class AppStatus(val allowsChanges: Boolean) {
    DEVELOPMENT(true), ACTIVE(true), DISCONTINUED(false)
}

data class App private constructor(
    val id: UUID,
    private var nameField: String,
    val developerId: UUID,
    private var descriptionField: String?,
    val discontinued: Boolean,
    val nextVersionDraft: AppVersionDraft,
    val releasedVersions: List<AppVersion>,
) {

    companion object {
        private fun validateName(name: String) = validateNotBlank(name, AppErrorCodes.NAME_BLANK)

        fun create(name: String, developerId: UUID, description: String?): Maybe<App> = validateName(name).map { validName ->
            App(
                id = UUID.randomUUID(),
                nameField = validName,
                developerId = developerId,
                descriptionField = description.trimToNull(),
                discontinued = false,
                nextVersionDraft = (AppVersionDraft.create() as Result).value,
                releasedVersions = emptyList(),
            )
        }
    }

    val name get() = nameField
    val description get() = descriptionField

    // TODO #22 set to disabled/discontinued if developer is not active
    val status by lazy {
        when {
            discontinued -> AppStatus.DISCONTINUED
            releasedVersions.isEmpty() -> AppStatus.DEVELOPMENT
            else -> AppStatus.ACTIVE
        }
    }

    internal val latestVersion by lazy {
        releasedVersions.firstOrNull()
    }

    internal fun addNextVersionDraftDatatype(datatypeName: String): Maybe<App> = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        nextVersionDraft.datatypes.any { it.name == datatypeName } -> Error(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = datatypeName,
        )

        else -> AppDatatypeDraft.create(name = datatypeName, schemaContent = "", description = null).flatMap { newDatatype ->
            nextVersionDraft.upsertDatatype(newDatatype).map { copy(nextVersionDraft = it) }
        }
    }

    internal fun changeNextVersionDraftDatatype(name: String, schemaContent: String, description: String?, newName: String): Maybe<App> = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        name != newName && nextVersionDraft.datatypes.any { it.name == newName } -> Error(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = newName,
        )

        else -> when (val datatype = nextVersionDraft.datatypes.firstOrNull { it.name == name }) {
            null -> Error(
                code = AppErrorCodes.DATATYPE_NOT_FOUND,
                details = name,
            )

            else -> {
                val nameToUse = if (name != newName) newName else name
                if (datatype.name == nameToUse && datatype.schemaContent == schemaContent && datatype.description == description) {
                    Result(this)
                } else {
                    AppDatatypeDraft.create(name = nameToUse, schemaContent = schemaContent, description = description).flatMap { updatedDatatype ->
                        nextVersionDraft.replaceDatatype(name, updatedDatatype).map { copy(nextVersionDraft = it) }
                    }
                }
            }
        }
    }

    internal fun removeNextVersionDraftDatatype(datatypeName: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        else -> nextVersionDraft.removeDatatype(datatypeName).map { copy(nextVersionDraft = it) }
    }

    internal fun addNextVersionDraftReport(reportName: String): Maybe<App> = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        nextVersionDraft.reports.any { it.name == reportName } -> Error(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = reportName,
        )

        else -> AppReport.create(name = reportName, source = "", description = null).flatMap { newReport ->
            nextVersionDraft.upsertReport(newReport).map { copy(nextVersionDraft = it) }
        }
    }

    internal fun changeNextVersionDraftReport(name: String, source: String, description: String?, newName: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        name != newName && nextVersionDraft.reports.any { it.name == newName } -> Error(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = newName,
        )

        else -> when (val report = nextVersionDraft.reports.firstOrNull { it.name == name }) {
            null -> Error(
                code = AppErrorCodes.REPORT_NOT_FOUND,
                details = name,
            )

            else -> {
                val nameToUse = if (name != newName) newName else name
                if (report.name == nameToUse && report.source == source && report.description == description) {
                    Result(this)
                } else {
                    AppReport.create(name = nameToUse, source = source, description = description).flatMap { updatedReport ->
                        nextVersionDraft.replaceReport(name, updatedReport).map { copy(nextVersionDraft = it) }
                    }
                }
            }
        }
    }

    internal fun removeNextVersionDraftReport(reportName: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        else -> nextVersionDraft.removeReport(reportName).map { copy(nextVersionDraft = it) }
    }

    // TODO #25 add test / implement more explicit check for schema correctness before next version release (loadAsTopLevelObjectSchema)
    // TODO #25 make schema checking more explicit??
    // TODO #25 prevent release if no changes at all
    internal fun releaseNextVersionDraft(changeType: AppVersionChangeType, note: String) = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )

        else -> {
            AppVersionReleaseNotes.create(changeType, note).flatMap { releaseNotes ->
                AppVersion.create(releaseNotes, nextVersionDraft, latestVersion).flatMap { nextVersionRelease ->
                    createNextVersionDraft(nextVersionRelease).map { newNextVersionDraft ->
                        copy(nextVersionDraft = newNextVersionDraft, releasedVersions = listOf(nextVersionRelease) + releasedVersions)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createNextVersionDraft(latestVersion: AppVersion): Maybe<AppVersionDraft> =
        latestVersion.let {
            val convertedDatatypes = it.datatypes.map { datatype -> AppDatatypeDraft.create(datatype) }
            val datatypeConversionErrors = convertedDatatypes.foldErrors<Set<AppDatatypeDraft>>().shrink()
            return when {
                datatypeConversionErrors != null -> datatypeConversionErrors as Maybe<AppVersionDraft>
                else -> {
                    val datatypeDrafts = convertedDatatypes.collectResults<AppDatatypeDraft>().map { it.value }.toSet()
                    AppVersionDraft.create(datatypes = datatypeDrafts, reports = it.reports)
                }
            }
        }

    internal fun changeReleaseNote(version: Semver, note: String): Maybe<App> = when {
        !status.allowsChanges -> {
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        }

        releasedVersions.firstOrNull { it.version == version } == null -> {
            Error(
                code = AppErrorCodes.RELEASE_VERSION_NOT_FOUND, details = version.toString()
            )
        }

        else -> {
            releasedVersions.first { it.version == version }.changeReleaseNote(note).map { updatedVersion ->
                copy(releasedVersions = releasedVersions.map { currentVersion ->
                    if (currentVersion.version == version) updatedVersion else currentVersion
                })
            }
        }
    }

    internal fun discontinue() = when {
        !status.allowsChanges -> Error(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
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

        @Suppress("UNCHECKED_CAST")
        fun create(releaseNotes: AppVersionReleaseNotes, nextVersionDraft: AppVersionDraft, latestVersion: AppVersion?): Maybe<AppVersion> {
            return when (val convertedDatatypes = nextVersionDraft.createDatatypes(latestVersion)) {
                is Result -> Result(
                    AppVersion(
                        version = releaseNotes.computeVersion(latestVersion, nextVersionDraft),
                        releaseNotes = releaseNotes,
                        datatypes = convertedDatatypes.value,
                        reports = nextVersionDraft.reports.toSet(),
                    )
                )

                is Error -> convertedDatatypes as Error<AppVersion>
                is Errors -> convertedDatatypes as Errors<AppVersion>
            }
        }
    }

    internal fun changeReleaseNote(note: String): Maybe<AppVersion> = releaseNotes.changeNote(note).map {
        copy(releaseNotes = it)
    }
}

data class AppVersionDraft private constructor(
    val datatypes: Set<AppDatatypeDraft>,
    val reports: Set<AppReport>,
) {

    companion object {
        fun create() = create(datatypes = emptySet(), reports = emptySet())

        @Suppress("UNCHECKED_CAST")
        fun create(appVersion: AppVersion): Maybe<AppVersionDraft> {
            val convertedDatatypes = appVersion.datatypes.map { AppDatatypeDraft.create(it) }
            val conversionErrors = convertedDatatypes.foldErrors<Set<AppDatatypeDraft>>().shrink()
            return when {
                conversionErrors != null -> conversionErrors as Maybe<AppVersionDraft>
                else -> create(datatypes = convertedDatatypes.collectResults<AppDatatypeDraft>().map { it.value }.toSet(), reports = appVersion.reports)
            }
        }

        fun create(datatypes: Set<AppDatatypeDraft>, reports: Set<AppReport>): Maybe<AppVersionDraft> =
            Result(AppVersionDraft(datatypes = datatypes, reports = reports))
    }

    internal fun createDatatypes(latest: AppVersion?): Maybe<Set<AppDatatype>> {
        val convertedDatatypes = datatypes.map { draft ->
            val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }
            val newVersion = when {
                existingDatatype == null -> 1
                existingDatatype.schemaContent == draft.schemaContent -> existingDatatype.version
                else -> existingDatatype.version + 1
            }

            AppDatatype.create(draft, newVersion)
        }

        val conversionErrors = convertedDatatypes.foldErrors<Set<AppDatatype>>().shrink()
        return when {
            conversionErrors != null -> conversionErrors
            else -> Result(convertedDatatypes.collectResults<AppDatatype>().map { it.value }.toSet())
        }
    }

    internal fun replaceDatatype(name: String, datatype: AppDatatypeDraft) = datatype.generateJsonSchema().loadAsTopLevelObjectSchema().map {
        copy(datatypes = datatypes.upsert(name, datatype))
    }

    internal fun upsertDatatype(datatype: AppDatatypeDraft) = datatype.generateJsonSchema().loadAsTopLevelObjectSchema().map {
        copy(datatypes = datatypes.upsert(datatype.name, datatype))
    }

    internal fun removeDatatype(datatypeName: String) = when {
        datatypes.none { it.name == datatypeName.trim() } -> Error(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = datatypeName,
        )

        else -> Result(copy(datatypes = datatypes.filterNot { it.name == datatypeName.trim() }.toSet()))
    }

    private fun Set<AppDatatypeDraft>.upsert(removeName: String, addDatatype: AppDatatypeDraft) =
        filterNot { it.name == removeName }.plus(addDatatype).toSet()

    internal fun replaceReport(name: String, report: AppReport) = Result(copy(reports = reports.upsert(name, report)))

    internal fun upsertReport(report: AppReport) = Result(copy(reports = reports.upsert(report.name, report)))

    internal fun removeReport(reportName: String) = when {
        reports.none { it.name == reportName.trim() } -> Error(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = reportName,
        )

        else -> Result(copy(reports = reports.filterNot { it.name == reportName }.toSet()))
    }

    private fun Set<AppReport>.upsert(removeName: String, addReport: AppReport) =
        filterNot { it.name == removeName }.plus(addReport).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

data class AppVersionReleaseNotes private constructor(
    val changeType: AppVersionChangeType,
    private var noteField: String,
) {

    companion object {
        private fun validateNote(note: String) = validateNotBlank(note, AppErrorCodes.VERSION_RELEASE_NOTE_BLANK)

        fun create(changeType: AppVersionChangeType, note: String): Maybe<AppVersionReleaseNotes> = validateNote(note).map { validNote ->
            AppVersionReleaseNotes(
                changeType = changeType,
                noteField = validNote,
            )
        }
    }

    val note get() = noteField

    internal fun changeNote(note: String): Maybe<AppVersionReleaseNotes> = validateNote(note).map { validNote ->
        copy(noteField = validNote)
    }

    internal fun computeVersion(latest: AppVersion?, next: AppVersionDraft): Semver {
        return if (latest == null) {

            // it's the very first version
            Semver(major = 0, minor = 1, patch = 0)
        } else {
            val isBreaking = isBreaking(latest, next)
            latest.version.computeNext(isBreaking, this.changeType)
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

    companion object {
        private val simpleNamePatern = Regex("([A-Z][a-z]*)+")

        private fun validateName(name: String) = validateMatches(
            value = name,
            pattern = simpleNamePatern,
            codeBlank = AppErrorCodes.DATATYPE_NAME_BLANK,
            codeNoMatch = AppErrorCodes.DATATYPE_NAME_INVALID,
        )

        fun create(datatype: AppDatatype): Maybe<AppDatatypeDraft> = create(
            name = datatype.name,
            schemaContent = datatype.schemaContent,
            description = datatype.description,
        )

        fun create(name: String, schemaContent: String, description: String?): Maybe<AppDatatypeDraft> = validateName(name).map { validName ->
            AppDatatypeDraft(
                nameField = validName,
                schemaContentField = schemaContent,
                descriptionField = description,
            )
        }
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

    companion object {
        private fun validateName(name: String) = validateNotBlank(name, AppErrorCodes.DATATYPE_NAME_BLANK)

        private fun validateVersion(version: Long) = validateNotNegative(version, AppErrorCodes.DATATYPE_VERSION_NEGATIVE)

        fun create(draft: AppDatatypeDraft, version: Long): Maybe<AppDatatype> = validateName(draft.name).flatMap { validName ->
            validateVersion(version).map { validVersion ->
                AppDatatype(
                    nameField = validName, versionField = validVersion, schemaContentField = draft.schemaContent, descriptionField = draft.description
                )
            }
        }
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
    private var sourceField: String,
    private var descriptionField: String?,
) {

    companion object {
        private fun validateName(name: String) = validateNotBlank(name, AppErrorCodes.REPORT_NAME_BLANK)

        fun create(name: String, description: String?, source: String): Maybe<AppReport> = validateName(name).map { validName ->
            AppReport(
                nameField = validName,
                sourceField = source,
                descriptionField = description,
            )
        }
    }

    val name get() = nameField
    val source get() = sourceField
    val description get() = descriptionField
}
