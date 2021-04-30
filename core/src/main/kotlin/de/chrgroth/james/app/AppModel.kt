package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Either
import de.chrgroth.james.app.AppServicePort.NewVersionContent
import de.chrgroth.james.incMajor
import de.chrgroth.james.incMinor
import de.chrgroth.james.incPatch
import java.util.UUID

enum class AppStatus { ACTIVE, DISCONTINUED }

// TODO Set vs List
// TODO add reference to owner/developer later

data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val nextVersionDraft: AppVersionDraft? = null,
    val versions: List<AppVersion> = emptyList(),
    val status: AppStatus = AppStatus.ACTIVE
) {

    internal fun getVersion(version: Semver) = versions.firstOrNull { it.version == version }
    internal fun getLatestVersion() = versions.maxByOrNull { it.version }

    internal fun prepareNewVersion(): Either<AppVersionDraft, Exception> {
        return if (nextVersionDraft != null) {
            Either.Right(IllegalStateException("Can't prepare new version as a draft already exists!"))
        } else {
            getLatestVersion()?.let {
                Either.Left(AppVersionDraft(models = it.models.toList(), reports = it.reports.toList()))
            } ?: Either.Left(AppVersionDraft())
        }
    }

    internal fun releaseNextVersion(newVersionContent: NewVersionContent, releaseNotes: AppVersionReleaseNotes?): Either<AppVersion, Exception> {
        return if (nextVersionDraft == null) {
            Either.Right(IllegalStateException("Can't release new version without a draft!"))
        } else {
            Either.Left(AppVersion(
                version = computeNextVersion(newVersionContent, nextVersionDraft, getLatestVersion()),
                releaseNotes = releaseNotes,
                models = nextVersionDraft.models.toList(),
                reports = nextVersionDraft.reports.toList(),
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

data class AppVersionDraft(
    val models: List<AppModel> = emptyList(),
    val reports: List<AppReport> = emptyList(),
)

// TODO add icon/image??

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes? = null,
    val models: List<AppModel> = emptyList(),
    val reports: List<AppReport> = emptyList(),
)

data class AppVersionReleaseNotes(
    val bugfixes: List<String> = emptyList(),
    val features: List<String> = emptyList(),
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
