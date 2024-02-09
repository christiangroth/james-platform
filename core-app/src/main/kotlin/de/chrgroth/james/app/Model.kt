package de.chrgroth.james.app

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.validNel
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.computeNext
import de.chrgroth.james.notBlankParser
import de.chrgroth.james.trimToNull
import java.util.UUID
import de.chrgroth.james.DomainError
import de.chrgroth.james.notEmptyListParser
import de.chrgroth.james.typesystem.Datatype
import de.chrgroth.james.typesystem.DataobjectFieldSpecFormat

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
            AppDomainErrorCodes.NAME_BLANK
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
        ): ValidatedNel<DomainError, App> =
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

    // TODO #6 set to disabled/discontinued if developer is not active
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

    internal fun changeNextVersionReleaseNoteTitle(title: String): ValidatedNel<DomainError, App> =
        changeNextVersionReleaseNoteAspect { it.changeReleaseNoteTitle(title) }

    internal fun changeNextVersionReleaseNoteNotes(notes: String): ValidatedNel<DomainError, App> =
        changeNextVersionReleaseNoteAspect { it.changeReleaseNoteNotes(notes) }

    internal fun changeNextVersionReleaseNoteFeatures(features: List<String>): ValidatedNel<DomainError, App> =
        changeNextVersionReleaseNoteAspect { it.changeReleaseNoteFeatures(features) }

    internal fun changeNextVersionReleaseNoteBugfixes(bugfixes: List<String>): ValidatedNel<DomainError, App> =
        changeNextVersionReleaseNoteAspect { it.changeReleaseNoteBugfixes(bugfixes) }

    internal fun changeNextVersionReleaseNoteMisc(misc: List<String>): ValidatedNel<DomainError, App> =
        changeNextVersionReleaseNoteAspect { it.changeReleaseNoteMisc(misc) }

    private fun changeNextVersionReleaseNoteAspect(block: (AppVersionDraft) -> ValidatedNel<DomainError, AppVersionDraft>): ValidatedNel<DomainError, App> =
        when {
            !status.allowsChanges -> {
                Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))
            }

            else -> {
                block(nextVersionDraft).andThen {
                    create(id, nameField, developerId, description, discontinued, it, releasedVersions)
                }
            }
        }

    internal fun addNextVersionDraftDatatype(datatypeName: String): ValidatedNel<DomainError, App> = when {

        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        nextVersionDraft.datatypes.any { it.name == datatypeName } -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_DUPLICATE)
        )

        else -> {
            val newDatatype = Datatype.create(
                name = datatypeName,
                displayName = datatypeName,
            )

            nextVersionDraft.upsertDatatype(newDatatype.name, newDatatype).andThen {
                create(id, nameField, developerId, description, discontinued, it, releasedVersions)
            }
        }
    }

    @Suppress("LongParameterList")
    internal fun changeNextVersionDraftDatatype(
        name: String,
        newName: String,
        displayName: String,
        content: String,
        format: DataobjectFieldSpecFormat,
        description: String?,
    ): ValidatedNel<DomainError, App> = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        name != newName && nextVersionDraft.datatypes.any { it.name == newName } -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_DUPLICATE)
        )

        else -> when (val datatype = nextVersionDraft.datatypes.firstOrNull { it.name == name }) {
            null -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DATATYPE_NOT_FOUND))

            else -> {
                val updatedDatatype = Datatype.parse(
                    name = newName,
                    displayName = displayName,
                    versionMajor = datatype.versionMajor,
                    versionMinor = datatype.versionMinor,
                    content = content,
                    format = format,
                    description = description,
                )

                nextVersionDraft.upsertDatatype(name, updatedDatatype).andThen {
                    create(id, nameField, developerId, description, discontinued, it, releasedVersions)
                }
            }
        }
    }

    internal fun removeNextVersionDraftDatatype(datatypeName: String) = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        else -> nextVersionDraft.removeDatatype(datatypeName).andThen {
            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
        }
    }

    internal fun addNextVersionDraftReport(reportName: String): ValidatedNel<DomainError, App> = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        nextVersionDraft.reports.any { it.name == reportName } -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.REPORT_NAME_DUPLICATE))

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
    ): ValidatedNel<DomainError, App> = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        name != newName && nextVersionDraft.reports.any { it.name == newName } -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.REPORT_NAME_DUPLICATE))

        else -> when (val report = nextVersionDraft.reports.firstOrNull { it.name == name }) {
            null -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.REPORT_NOT_FOUND))

            else -> {
                val nameToUse = if (name != newName) newName else name
                if (report.name == nameToUse && report.source == source && report.description == description) {
                    Validated.Valid(this)
                } else {
                    AppReport.create(name = nameToUse, source = source, description = description)
                        .andThen { updatedReport ->
                            nextVersionDraft.upsertReport(name, updatedReport).andThen {
                                create(id, nameField, developerId, description, discontinued, it, releasedVersions)
                            }
                        }
                }
            }
        }
    }

    internal fun removeNextVersionDraftReport(reportName: String) = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        else -> nextVersionDraft.removeReport(reportName).andThen {
            create(id, nameField, developerId, description, discontinued, it, releasedVersions)
        }
    }

    internal fun releaseNextVersionDraft() = when {
        !status.allowsChanges -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))

        !detectIfNextVersionDraftHasChanges() -> Validated.invalidNel(DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_NO_CHANGES))

        else -> {
            AppVersionReleaseNotes.create(nextVersionDraft.releaseNotes).andThen { releaseNotes ->
                AppVersion.create(releaseNotes, nextVersionDraft, latestVersion).andThen { nextVersionRelease ->
                    createNextVersionDraft(nextVersionRelease).andThen { newNextVersionDraft ->
                        create(
                            id,
                            nameField,
                            developerId,
                            description,
                            discontinued,
                            newNextVersionDraft,
                            listOf(nextVersionRelease) + releasedVersions
                        )
                    }
                }
            }
        }
    }

    private fun detectIfNextVersionDraftHasChanges(): Boolean {
        val currentDatatypes = latestVersion?.datatypes?.associate { it.name to it.dump(DataobjectFieldSpecFormat.YAML) } ?: emptyMap()
        val nextDatatypes = nextVersionDraft.datatypes.associate { it.name to it.dump(DataobjectFieldSpecFormat.YAML) }

        val currentReports = latestVersion?.reports?.associate { it.name to it.source } ?: emptyMap()
        val nextReports = nextVersionDraft.reports.associate { it.name to it.source }

        return !(currentDatatypes == nextDatatypes && currentReports == nextReports)
    }

    private fun createNextVersionDraft(latestVersion: AppVersion): ValidatedNel<DomainError, AppVersionDraft> =
        latestVersion.let {
            val datatypesCopies = it.datatypes.map { datatype ->
                datatype.copy()
            }.toSet()

            AppVersionDraft.create(
                datatypes = datatypesCopies,
                reports = it.reports
            )
        }

    internal fun changeReleaseNoteTitle(version: Semver, title: String): ValidatedNel<DomainError, App> =
        changeReleaseNoteAspect(version) { it.changeReleaseNoteTitle(title) }

    internal fun changeReleaseNoteNotes(version: Semver, notes: String): ValidatedNel<DomainError, App> =
        changeReleaseNoteAspect(version) { it.changeReleaseNoteNotes(notes) }

    internal fun changeReleaseNoteFeatures(version: Semver, features: List<String>): ValidatedNel<DomainError, App> =
        changeReleaseNoteAspect(version) { it.changeReleaseNoteFeatures(features) }

    internal fun changeReleaseNoteBugfixes(version: Semver, bugfixes: List<String>): ValidatedNel<DomainError, App> =
        changeReleaseNoteAspect(version) { it.changeReleaseNoteBugfixes(bugfixes) }

    internal fun changeReleaseNoteMisc(version: Semver, misc: List<String>): ValidatedNel<DomainError, App> =
        changeReleaseNoteAspect(version) { it.changeReleaseNoteMisc(misc) }

    private fun changeReleaseNoteAspect(
        version: Semver,
        block: (AppVersion) -> ValidatedNel<DomainError, AppVersion>
    ): ValidatedNel<DomainError, App> = when {
        !status.allowsChanges -> {
            Validated.invalidNel(DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED))
        }

        releasedVersions.firstOrNull { it.version == version } == null -> {
            Validated.invalidNel(DomainError(code = AppDomainErrorCodes.RELEASE_VERSION_NOT_FOUND))
        }

        else -> {
            block(releasedVersions.first { it.version == version }).andThen { updatedVersion ->
                create(
                    id,
                    nameField,
                    developerId,
                    description,
                    discontinued,
                    nextVersionDraft,
                    releasedVersions.map { currentVersion ->
                        if (currentVersion.version == version) updatedVersion else currentVersion
                    })
            }
        }
    }

    internal fun discontinue(): ValidatedNel<DomainError, App> = when {
        !status.allowsChanges -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
        )

        else -> create(id, nameField, developerId, description, true, nextVersionDraft, releasedVersions)
    }

    internal fun verifyDeletion(): ValidatedNel<DomainError, Unit> = when {
        status != AppStatus.DISCONTINUED -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
        )

        else -> Validated.validNel(Unit)
    }
}

data class AppVersion private constructor(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<Datatype>,
    val reports: Set<AppReport>,
) {

    companion object {

        fun create(
            releaseNotes: AppVersionReleaseNotes,
            nextVersionDraft: AppVersionDraft,
            latestVersion: AppVersion?,
        ): ValidatedNel<DomainError, AppVersion> =
            nextVersionDraft.createDatatypes(latestVersion).map { convertedDatatypes ->
                AppVersion(
                    version = releaseNotes.computeVersion(latestVersion, nextVersionDraft),
                    releaseNotes = releaseNotes,
                    datatypes = convertedDatatypes,
                    reports = nextVersionDraft.reports.toSet(),
                )
            }

        private fun create(
            base: AppVersion,
            releaseNotes: AppVersionReleaseNotes
        ): ValidatedNel<DomainError, AppVersion> =
            Validated.validNel(
                AppVersion(
                    version = base.version,
                    releaseNotes = releaseNotes,
                    datatypes = base.datatypes,
                    reports = base.reports,
                )
            )
    }

    internal fun changeReleaseNoteTitle(title: String): ValidatedNel<DomainError, AppVersion> =
        releaseNotes.changeTitle(title).andThen { create(this, it) }

    internal fun changeReleaseNoteNotes(notes: String): ValidatedNel<DomainError, AppVersion> =
        releaseNotes.changeNotes(notes).andThen { create(this, it) }

    internal fun changeReleaseNoteFeatures(features: List<String>): ValidatedNel<DomainError, AppVersion> =
        releaseNotes.changeFeatures(features).andThen { create(this, it) }

    internal fun changeReleaseNoteBugfixes(bugfixes: List<String>): ValidatedNel<DomainError, AppVersion> =
        releaseNotes.changeBugfixes(bugfixes).andThen { create(this, it) }

    internal fun changeReleaseNoteMisc(misc: List<String>): ValidatedNel<DomainError, AppVersion> =
        releaseNotes.changeMisc(misc).andThen { create(this, it) }
}

data class AppVersionDraft private constructor(
    val releaseNotes: AppVersionReleaseNotesDraft,
    val datatypes: Set<Datatype>,
    val reports: Set<AppReport>,
) {

    companion object {
        fun create() = create(
            releaseNotes = defaultEmptyReleaseNotesDraft(),
            datatypes = emptySet(),
            reports = emptySet(),
        )

        fun create(datatypes: Set<Datatype>, reports: Set<AppReport>): ValidatedNel<DomainError, AppVersionDraft> =
            create(defaultEmptyReleaseNotesDraft(), datatypes, reports)

        fun create(
            releaseNotes: AppVersionReleaseNotesDraft,
            datatypes: Set<Datatype>,
            reports: Set<AppReport>
        ): ValidatedNel<DomainError, AppVersionDraft> =
            Validated.validNel(AppVersionDraft(releaseNotes = releaseNotes, datatypes = datatypes, reports = reports))

        private fun defaultEmptyReleaseNotesDraft() = (AppVersionReleaseNotesDraft.create() as Validated.Valid).value
    }

    internal fun changeReleaseNoteTitle(title: String): ValidatedNel<DomainError, AppVersionDraft> =
        releaseNotes.changeTitle(title).andThen { create(it, datatypes, reports) }

    internal fun changeReleaseNoteNotes(notes: String): ValidatedNel<DomainError, AppVersionDraft> =
        releaseNotes.changeNotes(notes).andThen { create(it, datatypes, reports) }

    internal fun changeReleaseNoteFeatures(features: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        releaseNotes.changeFeatures(features).andThen { create(it, datatypes, reports) }

    internal fun changeReleaseNoteBugfixes(bugfixes: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        releaseNotes.changeBugfixes(bugfixes).andThen { create(it, datatypes, reports) }

    internal fun changeReleaseNoteMisc(misc: List<String>): ValidatedNel<DomainError, AppVersionDraft> =
        releaseNotes.changeMisc(misc).andThen { create(it, datatypes, reports) }

    internal fun createDatatypes(latest: AppVersion?): ValidatedNel<DomainError, Set<Datatype>> =
        datatypes.map { draft ->

            val existingDatatype = latest?.datatypes?.firstOrNull { it.name == draft.name }
            val (newVersionMajor, newVersionMinor) = if(existingDatatype == null) {
                1.toULong() to 0.toULong()
            } else {
                val versionMajor = existingDatatype.versionMajor
                val versionMinor = existingDatatype.versionMinor

                val breakingChanges = existingDatatype.computeBreakingChanges(draft)
                if(breakingChanges.isNotEmpty()) {
                    versionMajor.inc() to 0.toULong()
                } else {
                    versionMajor to versionMinor.inc()
                }
            }

            draft.copy(
                versionMajor = newVersionMajor,
                versionMinor = newVersionMinor,
            )
        }.toSet().validNel()

    internal fun upsertDatatype(name: String, datatype: Datatype): ValidatedNel<DomainError, AppVersionDraft> =
        create(releaseNotes, datatypes.upsert(name, datatype), reports)

    internal fun removeDatatype(datatypeName: String): ValidatedNel<DomainError, AppVersionDraft> = when {
        datatypes.none { it.name == datatypeName.trim() } -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.DATATYPE_NOT_FOUND)
        )

        else -> create(releaseNotes, datatypes.filterNot { it.name == datatypeName.trim() }.toSet(), reports)
    }

    private fun Set<Datatype>.upsert(removeName: String, addDatatype: Datatype): Set<Datatype> =
        filterNot { it.name == removeName }.plus(addDatatype).toSet()

    internal fun upsertReport(name: String, report: AppReport): ValidatedNel<DomainError, AppVersionDraft> =
        create(releaseNotes, datatypes, reports.upsert(name, report))

    internal fun removeReport(reportName: String): ValidatedNel<DomainError, AppVersionDraft> = when {
        reports.none { it.name == reportName.trim() } -> Validated.invalidNel(
            DomainError(code = AppDomainErrorCodes.REPORT_NOT_FOUND)
        )

        else -> create(releaseNotes, datatypes, reports.filterNot { it.name == reportName }.toSet())
    }

    private fun Set<AppReport>.upsert(removeName: String, addReport: AppReport) =
        filterNot { it.name == removeName }.plus(addReport).toSet()
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

data class AppVersionReleaseNotesDraft private constructor(
    private var titleField: String?,
    private var notesField: String?,
    private var featuresField: List<String>,
    private var bugfixesField: List<String>,
    private var miscField: List<String>,
) {

    companion object {
        fun create() = create(
            title = null,
            notes = null,
            features = emptyList(),
            bugfixes = emptyList(),
            misc = emptyList()
        )

        fun create(
            title: String?,
            notes: String?,
            features: List<String>,
            bugfixes: List<String>,
            misc: List<String>
        ): ValidatedNel<DomainError, AppVersionReleaseNotesDraft> =
            Validated.validNel(
                AppVersionReleaseNotesDraft(
                    titleField = title,
                    notesField = notes,
                    featuresField = features,
                    bugfixesField = bugfixes,
                    miscField = misc,
                )
            )
    }

    val title get() = titleField
    val notes get() = notesField
    val features get() = featuresField
    val bugfixes get() = bugfixesField
    val misc get() = miscField

    internal fun changeTitle(title: String) = create(title, notes, features, bugfixes, misc)
    internal fun changeNotes(notes: String) = create(title, notes, features, bugfixes, misc)
    internal fun changeFeatures(features: List<String>) = create(title, notes, features, bugfixes, misc)
    internal fun changeBugfixes(bugfixes: List<String>) = create(title, notes, features, bugfixes, misc)
    internal fun changeMisc(misc: List<String>) = create(title, notes, features, bugfixes, misc)
}

data class AppVersionReleaseNotes private constructor(
    private var titleField: String,
    private var notesField: String?,
    private var featuresField: List<String>,
    private var bugfixesField: List<String>,
    private var miscField: List<String>,
) {

    companion object {
        private val titleParser = notBlankParser(
            AppDomainErrorCodes.VERSION_RELEASE_TITLE_BLANK
        )

        private val featuresOrBugfixesParser = notEmptyListParser(
            AppDomainErrorCodes.VERSION_RELEASE_NOTE_FEATURES_OR_BUGFIXES
        )

        fun create(draft: AppVersionReleaseNotesDraft): ValidatedNel<DomainError, AppVersionReleaseNotes> = create(
            title = draft.title ?: "",
            notes = draft.notes,
            features = draft.features,
            bugfixes = draft.bugfixes,
            misc = draft.misc,
        )

        fun create(
            title: String,
            notes: String?,
            features: List<String>,
            bugfixes: List<String>,
            misc: List<String>
        ): ValidatedNel<DomainError, AppVersionReleaseNotes> =
            titleParser.parse(title).andThen { validTitle ->
                featuresOrBugfixesParser.parse(features.plus(bugfixes)).map {
                    AppVersionReleaseNotes(
                        titleField = validTitle,
                        notesField = notes,
                        featuresField = features,
                        bugfixesField = bugfixes,
                        miscField = misc,
                    )
                }
            }
    }

    val title get() = titleField
    val notes get() = notesField
    val features get() = featuresField
    val bugfixes get() = bugfixesField
    val misc get() = miscField

    val changeType: AppVersionChangeType get() = if (features.isNotEmpty()) AppVersionChangeType.FEATURE else AppVersionChangeType.BUGFIX

    internal fun changeTitle(title: String) = create(title, notes, features, bugfixes, misc)
    internal fun changeNotes(notes: String) = create(title, notes, features, bugfixes, misc)
    internal fun changeFeatures(features: List<String>) = create(title, notes, features, bugfixes, misc)
    internal fun changeBugfixes(bugfixes: List<String>) = create(title, notes, features, bugfixes, misc)
    internal fun changeMisc(misc: List<String>) = create(title, notes, features, bugfixes, misc)

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
        if (nextDatatype != null) {
            existingDatatype to nextDatatype
        } else {
            null
        }
    }.any { it.first.computeBreakingChanges(it.second).isNotEmpty() }
}

data class AppReport private constructor(
    private var nameField: String,
    private var sourceField: String,
    private var descriptionField: String?,
) {

    companion object {
        private val nameParser = notBlankParser(
            AppDomainErrorCodes.REPORT_NAME_BLANK
        )

        fun create(name: String, description: String?, source: String): ValidatedNel<DomainError, AppReport> =
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
