package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.incMajor
import de.chrgroth.james.incMinor
import de.chrgroth.james.incPatch
import java.util.UUID

enum class AppStatus { DEVELOPMENT, ACTIVE, DISCONTINUED }

interface AppDescriptor {
    val id: UUID
    val name: String
    val description: String?
    val versions: List<Semver>
    val hasActiveDraft: Boolean
    val isDiscontinued: Boolean

    val latestVersion: Semver?
        get() = versions.firstOrNull()

    val status: AppStatus
        get() = when {
            isDiscontinued -> AppStatus.DISCONTINUED
            versions.isEmpty() -> AppStatus.DEVELOPMENT
            else -> AppStatus.ACTIVE
        }
}

internal data class AppDescription(
    override val id: UUID,
    override val name: String,
    override val description: String? = null,
    override val versions: List<Semver> = emptyList(),
    override val hasActiveDraft: Boolean,
    override val isDiscontinued: Boolean,
) : AppDescriptor

internal data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val discontinued: Boolean,
    val nextVersionDraft: AppVersionDraft? = null,
    val versionModels: Set<AppVersion> = emptySet(),
) {

    fun createDescriptor() = AppDescription(
        id = id,
        name = name,
        description = description,
        versions = versionModels.map { it.version }.sortedDescending(),
        hasActiveDraft = nextVersionDraft != null,
        isDiscontinued = discontinued
    )

    internal fun prepareNewVersion(): Maybe<AppVersionDraft> {
        return if (nextVersionDraft != null) {
            Maybe.Error(AppErrorCodes.PREPARE_NEW_VERSION_DRAFT_EXISTS)
        } else {
            getLatestVersionModel()?.let {
                Maybe.Result(AppVersionDraft(models = it.models.toSet(), reports = it.reports.toSet()))
            } ?: Maybe.Result(AppVersionDraft())
        }
    }

    internal fun releaseNextVersion(releaseNotes: AppVersionReleaseNotes): Maybe<AppVersion> {
        return if (nextVersionDraft == null) {
            Maybe.Error(AppErrorCodes.RELEASE_NEW_VERSION_DRAFT_MISSING)
        } else {
            Maybe.Result(AppVersion(
                version = computeNextVersion(releaseNotes, nextVersionDraft, getLatestVersionModel()),
                releaseNotes = releaseNotes,
                models = nextVersionDraft.models.toSet(),
                reports = nextVersionDraft.reports.toSet(),
            ))
        }
    }

    private fun getLatestVersionModel() = versionModels.maxByOrNull { it.version }

    private fun computeNextVersion(releaseNotes: AppVersionReleaseNotes, draft: AppVersionDraft, latest: AppVersion?): Semver {
        val latestVersion = latest?.version ?: Semver(0, 0, 0)
        return if (isBreaking(draft, latest)) {
            latestVersion.incMajor()
        } else {
            when (releaseNotes.changeType) {
                AppVersionChangeType.BUGFIX -> latestVersion.incPatch()
                AppVersionChangeType.FEATURE -> latestVersion.incMinor()
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

interface AppVersionDescriptor {
    val version: Semver
    val releaseNotes: AppVersionReleaseNotes
}

internal data class AppVersionDescription(
    override val version: Semver,
    override val releaseNotes: AppVersionReleaseNotes,
) : AppVersionDescriptor

interface AppVersionArtifacts {
    val models: Set<AppModel>
    val reports: Set<AppReport>
}

internal data class AppVersionArtifactsHolder(
    override val models: Set<AppModel> = emptySet(),
    override val reports: Set<AppReport> = emptySet(),
) : AppVersionArtifacts

// TODO add icon/image??
internal data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val holder: AppVersionArtifacts,
) : AppVersionArtifacts by holder {

    fun createDescriptor() = AppVersionDescription(
        version = version,
        releaseNotes = releaseNotes,
    )

    constructor(
        version: Semver,
        releaseNotes: AppVersionReleaseNotes,
        models: Set<AppModel> = emptySet(),
        reports: Set<AppReport> = emptySet(),
    ) : this(version, releaseNotes, AppVersionArtifactsHolder(models, reports))
}

data class AppVersionDraft(
    val holder: AppVersionArtifacts,
) : AppVersionArtifacts by holder {

    constructor(models: Set<AppModel> = emptySet(), reports: Set<AppReport> = emptySet()) :
            this(AppVersionArtifactsHolder(models, reports))
}

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

data class AppVersionReleaseNotes(
    val changeType: AppVersionChangeType,
    val note: String,
)

// TODO use https://github.com/everit-org/json-schema for JSON schema validations on data objects, not sure if it can also validate the schema itself
data class AppModel(
    val name: String,
    val version: Long, // TODO need something more complex like Semver here too?
    val schema: String,
    val description: String? = null,
)

// TODO would be great to have some generic reports/graphs, configured based models and attributes
data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String,
)
