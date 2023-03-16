package de.chrgroth.james.app

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import com.github.glwithu06.semver.Semver
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.compose
import de.chrgroth.james.app.jsonschema.computeCompatibility
import de.chrgroth.james.app.jsonschema.jsonObjectSchemaFor
import de.chrgroth.james.app.jsonschema.parseToObjectSchema
import de.chrgroth.james.computeNext
import de.chrgroth.james.notBlankParser
import de.chrgroth.james.notNegativeLongParser
import de.chrgroth.james.regexParer
import de.chrgroth.james.trimToNull
import org.everit.json.schema.ObjectSchema
import java.util.UUID
import de.chrgroth.james.Error
import de.chrgroth.james.reduceWithAllValues

// TODO #28 make return types explicit (check all files)

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
        private val nameParser = notBlankParser(
            AppErrorCodes.NAME_BLANK
        )

        @Suppress("LongParameterList")
        fun create(
            id: UUID = UUID.randomUUID(),
            name: String,
            developerId: UUID,
            description: String?,
            discontinued: Boolean = false,
            nextVersionDraft: AppVersionDraft = (AppVersionDraft.create() as Validated.Valid).value,
            releasedVersions: List<AppVersion> = emptyList(),
        ): ValidatedNel<Error, App> =
            nameParser.parse(name).map { validName ->
                App(
                    id = id,
                    nameField = validName,
                    developerId = developerId,
                    descriptionField = description.trimToNull(),
                    discontinued = discontinued,
                    nextVersionDraft = nextVersionDraft,
                    releasedVersions = releasedVersions,
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

    internal fun addNextVersionDraftDatatype(datatypeName: String): ValidatedNel<Error, App> = when {

        !status.allowsChanges -> Validated.invalidNel(
            Error(
                AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                null,
            )
        )

        nextVersionDraft.datatypes.any { it.name == datatypeName } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
                details = datatypeName,
            )
        )

        else -> AppDatatypeDraft.create(name = datatypeName, schemaContent = "", description = null).andThen { newDatatype ->
            nextVersionDraft.upsertDatatype(newDatatype.name, newDatatype).andThen {
                create(id, nameField, developerId, description, discontinued, it, releasedVersions)
            }
        }
    }

    internal fun changeNextVersionDraftDatatype(
        name: String,
        schemaContent: String,
        description: String?,
        newName: String,
    ): ValidatedNel<Error, App> = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        name != newName && nextVersionDraft.datatypes.any { it.name == newName } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
                details = newName,
            )
        )

        else -> when (val datatype = nextVersionDraft.datatypes.firstOrNull { it.name == name }) {
            null -> Validated.invalidNel(
                Error(
                    code = AppErrorCodes.DATATYPE_NOT_FOUND,
                    details = name,
                )
            )

            else -> {
                val nameToUse = if (name != newName) newName else name
                if (datatype.name == nameToUse && datatype.schemaContent == schemaContent && datatype.description == description) {
                    Validated.validNel(this)
                } else {
                    AppDatatypeDraft.create(name = nameToUse, schemaContent = schemaContent, description = description).andThen { updatedDatatype ->
                        nextVersionDraft.upsertDatatype(name, updatedDatatype).andThen {
                            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
                        }
                    }
                }
            }
        }
    }

    internal fun removeNextVersionDraftDatatype(datatypeName: String) = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        else -> nextVersionDraft.removeDatatype(datatypeName).andThen {
            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
        }
    }

    internal fun addNextVersionDraftReport(reportName: String): ValidatedNel<Error, App> = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        nextVersionDraft.reports.any { it.name == reportName } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.REPORT_NAME_DUPLICATE,
                details = reportName,
            )
        )

        else -> AppReport.create(name = reportName, source = "", description = null).andThen { newReport ->
            nextVersionDraft.upsertReport(newReport.name, newReport).andThen {
                create(id, nameField, developerId, description, discontinued, it, releasedVersions)
            }
        }
    }

    internal fun changeNextVersionDraftReport(
        name: String,
        source: String,
        description: String?,
        newName: String,
    ): ValidatedNel<Error, App> = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        name != newName && nextVersionDraft.reports.any { it.name == newName } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.REPORT_NAME_DUPLICATE,
                details = newName,
            )
        )

        else -> when (val report = nextVersionDraft.reports.firstOrNull { it.name == name }) {
            null -> Validated.invalidNel(
                Error(
                    code = AppErrorCodes.REPORT_NOT_FOUND,
                    details = name,
                )
            )

            else -> {
                val nameToUse = if (name != newName) newName else name
                if (report.name == nameToUse && report.source == source && report.description == description) {
                    Validated.Valid(this)
                } else {
                    AppReport.create(name = nameToUse, source = source, description = description).andThen { updatedReport ->
                        nextVersionDraft.upsertReport(name, updatedReport).andThen {
                            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
                        }
                    }
                }
            }
        }
    }

    internal fun removeNextVersionDraftReport(reportName: String) = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        else -> nextVersionDraft.removeReport(reportName).andThen {
            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
        }
    }

    // TODO #36 prevent release if no changes at all
    // TODO #36 add methods to get all new/changed datatypes/reports
    internal fun releaseNextVersionDraft(changeType: AppVersionChangeType, note: String) = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        else -> {
            AppVersionReleaseNotes.create(changeType, note).andThen { releaseNotes ->
                AppVersion.create(releaseNotes, nextVersionDraft, latestVersion).andThen { nextVersionRelease ->
                    createNextVersionDraft(nextVersionRelease).andThen { newNextVersionDraft ->
                        create(id, nameField, developerId, description, discontinued, newNextVersionDraft, listOf(nextVersionRelease) + releasedVersions)
                    }
                }
            }
        }
    }

    private fun createNextVersionDraft(latestVersion: AppVersion): ValidatedNel<Error, AppVersionDraft> =
        latestVersion.let {
            it.datatypes.map { datatype ->
                AppDatatypeDraft.create(datatype)
            }.reduceWithAllValues().andThen { datatypeDrafts ->
                AppVersionDraft.create(datatypes = datatypeDrafts.toSet(), reports = it.reports)
            }
        }

    internal fun changeReleaseNote(version: Semver, note: String): ValidatedNel<Error, App> = when {
        !status.allowsChanges -> {
            Validated.invalidNel(
                Error(
                    code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                    details = null,
                )
            )
        }

        releasedVersions.firstOrNull { it.version == version } == null -> {
            Validated.invalidNel(
                Error(
                    code = AppErrorCodes.RELEASE_VERSION_NOT_FOUND, details = version.toString()
                )
            )
        }

        else -> {
            releasedVersions.first { it.version == version }.changeReleaseNote(note).andThen { updatedVersion ->
                create(id, nameField, developerId, description, discontinued, nextVersionDraft, releasedVersions.map { currentVersion ->
                    if (currentVersion.version == version) updatedVersion else currentVersion
                })
            }
        }
    }

    internal fun discontinue(): ValidatedNel<Error, App> = when {
        !status.allowsChanges -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
                details = null,
            )
        )

        else -> create(id, nameField, developerId, description, true, nextVersionDraft, releasedVersions)
    }

    internal fun verifyDeletion(): ValidatedNel<Error, Unit> = when {
        status != AppStatus.DISCONTINUED -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
                details = null,
            )
        )

        else -> Validated.validNel(Unit)
    }
}

data class AppVersion private constructor(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<AppDatatype>,
    val reports: Set<AppReport>,
) {

    companion object {

        fun create(
            releaseNotes: AppVersionReleaseNotes,
            nextVersionDraft: AppVersionDraft,
            latestVersion: AppVersion?,
        ): ValidatedNel<Error, AppVersion> =
            nextVersionDraft.createDatatypes(latestVersion).map { convertedDatatypes ->
                AppVersion(
                    version = releaseNotes.computeVersion(latestVersion, nextVersionDraft),
                    releaseNotes = releaseNotes,
                    datatypes = convertedDatatypes,
                    reports = nextVersionDraft.reports.toSet(),
                )
            }

        private fun create(base: AppVersion, note: String): ValidatedNel<Error, AppVersion> =
            base.releaseNotes.changeNote(note).map {
                AppVersion(
                    version = base.version,
                    releaseNotes = it,
                    datatypes = base.datatypes,
                    reports = base.reports,
                )
            }
    }

    internal fun changeReleaseNote(note: String): ValidatedNel<Error, AppVersion> = create(this, note)
}

// TODO #36 add release notes draft?
data class AppVersionDraft private constructor(
    val datatypes: Set<AppDatatypeDraft>,
    val reports: Set<AppReport>,
) {

    companion object {
        fun create() = create(datatypes = emptySet(), reports = emptySet())

        fun create(appVersion: AppVersion): ValidatedNel<Error, AppVersionDraft> =
            appVersion.datatypes.map { AppDatatypeDraft.create(it) }.reduceWithAllValues().andThen { datatypeDrafts ->
                create(datatypes = datatypeDrafts.toSet(), reports = appVersion.reports)
            }

        fun create(datatypes: Set<AppDatatypeDraft>, reports: Set<AppReport>): ValidatedNel<Error, AppVersionDraft> =
            Validated.validNel(AppVersionDraft(datatypes = datatypes, reports = reports))
    }

    internal fun createDatatypes(latest: AppVersion?): ValidatedNel<Error, Set<AppDatatype>> =
        datatypes.map { draft ->
            val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }
            val newVersion = when {
                existingDatatype == null -> 1
                existingDatatype.schemaContent == draft.schemaContent -> existingDatatype.version
                else -> existingDatatype.version + 1
            }

            AppDatatype.create(draft, newVersion)
        }.reduceWithAllValues().map { datatypes ->
            datatypes.toSet()
        }

    internal fun upsertDatatype(name: String, datatype: AppDatatypeDraft): ValidatedNel<Error, AppVersionDraft> =
        datatype.validateJsonSchema().andThen {
            create(datatypes.upsert(name, datatype), reports)
        }

    internal fun removeDatatype(datatypeName: String): ValidatedNel<Error, AppVersionDraft> = when {
        datatypes.none { it.name == datatypeName.trim() } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_NOT_FOUND,
                details = datatypeName,
            )
        )

        else -> create(datatypes.filterNot { it.name == datatypeName.trim() }.toSet(), reports)
    }

    private fun Set<AppDatatypeDraft>.upsert(removeName: String, addDatatype: AppDatatypeDraft): Set<AppDatatypeDraft> =
        filterNot { it.name == removeName }.plus(addDatatype).toSet()

    internal fun upsertReport(name: String, report: AppReport): ValidatedNel<Error, AppVersionDraft> =
        create(datatypes, reports.upsert(name, report))

    internal fun removeReport(reportName: String): ValidatedNel<Error, AppVersionDraft> = when {
        reports.none { it.name == reportName.trim() } -> Validated.invalidNel(
            Error(
                code = AppErrorCodes.REPORT_NOT_FOUND,
                details = reportName,
            )
        )

        else -> create(datatypes, reports.filterNot { it.name == reportName }.toSet())
    }

    private fun Set<AppReport>.upsert(removeName: String, addReport: AppReport) =
        filterNot { it.name == removeName }.plus(addReport).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

// TODO #35 split up note field to title and optional description?
data class AppVersionReleaseNotes private constructor(
    val changeType: AppVersionChangeType,
    private var noteField: String,
) {

    companion object {
        private val noteParser = notBlankParser(
            AppErrorCodes.VERSION_RELEASE_NOTE_BLANK
        )

        fun create(changeType: AppVersionChangeType, note: String): ValidatedNel<Error, AppVersionReleaseNotes> =
            noteParser.parse(note).map { validNote ->
                AppVersionReleaseNotes(
                    changeType = changeType,
                    noteField = validNote,
                )
            }
    }

    val note get() = noteField

    internal fun changeNote(note: String) = create(changeType, note)

    internal fun computeVersion(latest: AppVersion?, next: AppVersionDraft): Semver {
        return if (latest == null) {

            // it's the very first version
            Semver(major = 0, minor = 1, patch = 0)
        } else {
            val isBreaking = isBreaking(latest, next)
            latest.version.computeNext(isBreaking, this.changeType)
        }
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
        it.first.validateJsonSchema() to it.second.validateJsonSchema()
    }.mapNotNull {
        if (it.first is Validated.Valid && it.second is Validated.Valid) {
            (it.first as Validated.Valid).value to (it.second as Validated.Valid).value
        } else {
            null
        }
    }.any { it.first.computeCompatibility(it.second) is Validated.Invalid }
}

data class AppDatatypeDraft private constructor(
    private var nameField: String,
    private var schemaContentField: String,
    private var descriptionField: String?,
) {

    companion object {
        private val nameParser = regexParer(
            AppErrorCodes.DATATYPE_NAME_BLANK,
            Regex("([A-Z][a-z]*)+"),
            AppErrorCodes.DATATYPE_NAME_INVALID,
        )

        fun create(datatype: AppDatatype): ValidatedNel<Error, AppDatatypeDraft> = create(
            name = datatype.name,
            schemaContent = datatype.schemaContent,
            description = datatype.description,
        )

        fun create(name: String, schemaContent: String, description: String?): ValidatedNel<Error, AppDatatypeDraft> =
            nameParser.parse(name).map { validName ->
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

    fun validateJsonSchema(): ValidatedNel<Error, ObjectSchema> = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = null,
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    ).parseToObjectSchema()
}

data class AppDatatype private constructor(
    private var nameField: String,
    private var versionField: Long,
    private var schemaContentField: String,
    private var descriptionField: String?,
) {

    companion object {
        private val nameParser = notBlankParser(
            AppErrorCodes.DATATYPE_NAME_BLANK
        )

        private val versionParser = notNegativeLongParser(
            AppErrorCodes.DATATYPE_VERSION_NEGATIVE
        )

        private data class AppDatatypeParserInput(val name: String, val version: Long)

        fun create(draft: AppDatatypeDraft, version: Long): ValidatedNel<Error, AppDatatype> {

            val datatypeParser: Parser<AppDatatypeParserInput, AppDatatype, Error> = Parser
                .compose(
                    nameParser.contramap { it.name },
                    versionParser.contramap { it.version.toString() },
                ) { validName, validVersion ->
                    AppDatatype(
                        nameField = validName, versionField = validVersion, schemaContentField = draft.schemaContent, descriptionField = draft.description
                    )
                }

            return datatypeParser.parse(AppDatatypeParserInput(draft.name, version))
        }
    }

    val name get() = nameField
    val version get() = versionField
    val schemaContent get() = schemaContentField
    val description get() = descriptionField

    fun validateJsonSchema(): ValidatedNel<Error, ObjectSchema> = jsonObjectSchemaFor(
        // TODO #19 appId = appId,
        // TODO #19 version = version.toString(),
        name = name,
        description = description ?: "",
        schemaContent = schemaContent,
    ).parseToObjectSchema()
}

data class AppReport private constructor(
    private var nameField: String,
    private var sourceField: String,
    private var descriptionField: String?,
) {

    companion object {
        private val nameParser = notBlankParser(
            AppErrorCodes.REPORT_NAME_BLANK
        )

        fun create(name: String, description: String?, source: String): ValidatedNel<Error, AppReport> =
            nameParser.parse(name).map { validName ->
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
