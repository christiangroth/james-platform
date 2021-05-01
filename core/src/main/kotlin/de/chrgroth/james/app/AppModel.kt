package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.app.AppServicePort.NewVersionContent
import de.chrgroth.james.incMajor
import de.chrgroth.james.incMinor
import de.chrgroth.james.incPatch
import java.util.UUID

enum class AppStatus { ACTIVE, DISCONTINUED }

// TODO add reference to owner/developer later

data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val nextVersionDraft: AppVersionDraft? = null,
    val versions: Set<AppVersion> = emptySet(),
    val status: AppStatus = AppStatus.ACTIVE
) {

    internal fun getVersion(version: Semver) = versions.firstOrNull { it.version == version }
    internal fun getLatestVersion() = versions.maxByOrNull { it.version }

    internal fun prepareNewVersion(): Maybe<AppVersionDraft> {
        return if (nextVersionDraft != null) {
            Maybe.Error(AppErrorCodes.PREPARE_NEW_VERSION_DRAFT_EXISTS)
        } else {
            getLatestVersion()?.let {
                Maybe.Result(AppVersionDraft(models = it.models.toSet(), reports = it.reports.toSet()))
            } ?: Maybe.Result(AppVersionDraft())
        }
    }

    internal fun releaseNextVersion(newVersionContent: NewVersionContent, releaseNotes: AppVersionReleaseNotes?): Maybe<AppVersion> {
        return if (nextVersionDraft == null) {
            Maybe.Error(AppErrorCodes.RELEASE_NEW_VERSION_DRAFT_MISSING)
        } else {
            Maybe.Result(AppVersion(
                version = computeNextVersion(newVersionContent, nextVersionDraft, getLatestVersion()),
                releaseNotes = releaseNotes,
                models = nextVersionDraft.models.toSet(),
                reports = nextVersionDraft.reports.toSet(),
            ))
        }
    }

    private fun computeNextVersion(newVersionContent: NewVersionContent, draft: AppVersionDraft, latest: AppVersion?): Semver {
        val latestVersion = latest?.version ?: Semver(0, 0, 0)
        return if (isBreaking(draft, latest)) {
            latestVersion.incMajor()
        } else {
            when (newVersionContent) {
                NewVersionContent.FEATURE -> latestVersion.incMinor()
                NewVersionContent.BUGFIX -> latestVersion.incPatch()
            }
        }
    }

    private fun isBreaking(draft: AppVersionDraft, latest: AppVersion?): Boolean {
        if (latest == null) {
            return true
        }

        TODO()
    }
}

interface AppVersionArtifacts {
    val models: Set<AppModel>
    val reports: Set<AppReport>
}

data class AppVersionArtifactsHolder(
    override val models: Set<AppModel> = emptySet(),
    override val reports: Set<AppReport> = emptySet(),
) : AppVersionArtifacts

data class AppVersionDraft(
    val holder: AppVersionArtifactsHolder
) : AppVersionArtifacts by holder {

    constructor(models: Set<AppModel> = emptySet(), reports: Set<AppReport> = emptySet()) :
            this(AppVersionArtifactsHolder(models, reports))
}

// TODO add icon/image??

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes? = null,
    val holder: AppVersionArtifactsHolder,
) : AppVersionArtifacts by holder {

    constructor(
        version: Semver,
        releaseNotes: AppVersionReleaseNotes? = null,
        models: Set<AppModel> = emptySet(),
        reports: Set<AppReport> = emptySet(),
    ) : this(version, releaseNotes, AppVersionArtifactsHolder(models, reports))
}

data class AppVersionReleaseNotes(
    val bugfixes: Set<String> = emptySet(),
    val features: Set<String> = emptySet(),
    val header: String? = null,
)

// TODO use https://github.com/everit-org/json-schema for JSON schema validations on data objects, not sure if it can also validate the schema itself

data class AppModel(
    val name: String,
    val version: Long, // TODO need something more complex?
    val schema: String,
    val description: String? = null,
)

// TODO would be great to have some generic reports/graphs, configured based models and attributes

data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String,
)

// TODO move to User.kt or UserData.kt

data class AppDataObject(
    val id: Long,
    // TODO created, lastUpdated, deleted
    // TODO createdBy, lastUpdatedBy, deletedBy
    // TODO typeVersion?
)
