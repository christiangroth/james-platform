package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.incMajor
import de.chrgroth.james.incMinor
import de.chrgroth.james.incPatch
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID


enum class AppStatus {
    DEVELOPMENT, ACTIVE, DISCONTINUED
}

data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val discontinued: Boolean = false,
    val developmentVersion: AppVersionDraft? = null,
    val versions: Set<AppVersion> = emptySet(),
) {

    private val latestVersion
        get() = versions.maxByOrNull { it.version }

    val status
        get() = when {
            discontinued -> AppStatus.DISCONTINUED
            versions.isEmpty() -> AppStatus.DEVELOPMENT
            else -> AppStatus.ACTIVE
        }

    fun createDevelopmentVersion() =
        if (developmentVersion != null) {
            Maybe.Error(AppErrorCodes.PREPARE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
        } else {
            latestVersion?.let {
                Maybe.Result(AppVersionDraft(models = it.models.toSet(), reports = it.reports.toSet()))
            } ?: Maybe.Result(AppVersionDraft())
        }

    fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) =
        if (developmentVersion == null) {
            Maybe.Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
        } else {
            Maybe.Result(AppVersion(
                version = computeNextVersion(latestVersion, developmentVersion, releaseNotes),
                releaseNotes = releaseNotes,
                models = developmentVersion.models.toSet(),
                reports = developmentVersion.reports.toSet(),
            ))
        }

    private fun computeNextVersion(latest: AppVersion?, draft: AppVersionDraft, releaseNotes: AppVersionReleaseNotes): Semver {
        return if (latest == null) {
            Semver(major = 0, minor = 1, patch = 0)
        } else {
            val isBreaking = isBreaking(latest, draft)
            latest.version.computeNext(isBreaking, releaseNotes)
        }
    }

    private fun isBreaking(latest: AppVersion, draft: AppVersionDraft): Boolean {
        val modelRenamedOrDeleted = latest.models.any { oldModel ->
            draft.models.none { it.name == oldModel.name }
        }

        val schemaPairs = latest.models
            .filter { oldModel -> draft.models.any { it.name == oldModel.name } }
            .map { oldModel -> oldModel to draft.models.first { it.name == oldModel.name } }
            .map { it.first.toJsonSchema() to it.second.toJsonSchema()}

        // TODO need some more schema insights
        // val modelAttributeDeleted = schemaPairs.any { }
        // val modelAttributeTypeChanged = schemaPairs.any {  }

        return modelRenamedOrDeleted // || modelAttributeDeleted || modelAttributeTypeChanged
    }

    private fun Semver.computeNext(isBreaking: Boolean, releaseNotes: AppVersionReleaseNotes) =
        if (isBreaking) {
            incMajor()
        } else {
            when (releaseNotes.changeType) {
                AppVersionChangeType.BUGFIX -> incPatch()
                AppVersionChangeType.FEATURE -> incMinor()
            }
        }
}

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val models: Set<AppModel> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
)

data class AppVersionDraft(
    val models: Set<AppModel> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
)

enum class AppVersionChangeType {
    BUGFIX, FEATURE
}

data class AppVersionReleaseNotes(
    val changeType: AppVersionChangeType,
    val note: String,
)

data class AppModel(
    val name: String,
    val version: Long,
    val schema: String? = null,
    val description: String? = null,
){
    fun toJsonSchema(): Schema =
        SchemaLoader.load(JSONObject(JSONTokener(schema)))
}

// TODO would be great to have some generic reports/graphs, configured based on models and their attributes
data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String? = null,
)
