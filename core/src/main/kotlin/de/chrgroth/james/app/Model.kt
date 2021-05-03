package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.computeNext
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
        if (developmentVersion != null) {
            Maybe.Error(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
        } else {
            latestVersion?.let {
                Maybe.Result(AppVersionDraft(datatypes = it.datatypes.toSet(), reports = it.reports.toSet()))
            } ?: Maybe.Result(AppVersionDraft())
        }

    internal fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) =
        if (developmentVersion == null) {
            Maybe.Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
        } else {
            val nextVersion = releaseNotes.computeVersion(latestVersion, developmentVersion)
            Maybe.Result(AppVersion(
                version = nextVersion,
                releaseNotes = releaseNotes,
                datatypes = developmentVersion.datatypes.toSet(),
                reports = developmentVersion.reports.toSet(),
            ))
        }
}

data class AppVersion(
    val version: Semver,
    val releaseNotes: AppVersionReleaseNotes,
    val datatypes: Set<AppDatatype> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
)

data class AppVersionDraft(
    val datatypes: Set<AppDatatype> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
)

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
        val modelRenamedOrDeleted = latest.datatypes.any { oldModel ->
            next.datatypes.none { it.name == oldModel.name }
        }

        val schemaPairs = latest.datatypes
            .filter { oldModel -> next.datatypes.any { it.name == oldModel.name } }
            .map { oldModel -> oldModel to next.datatypes.first { it.name == oldModel.name } }
        //.map { it.first.toJsonSchema() to it.second.toJsonSchema() }

        // TODO need some more schema insights
        // val modelAttributeDeleted = schemaPairs.any { }
        // val modelAttributeTypeChanged = schemaPairs.any {  }

        return modelRenamedOrDeleted // || modelAttributeDeleted || modelAttributeTypeChanged
    }
}

data class AppDatatype(
    val name: String,
    val version: Long,
    val schema: String? = null,
    val description: String? = null,
) {
    internal fun toJsonSchema(): Schema =
        SchemaLoader.load(JSONObject(JSONTokener(schema)))
}

// TODO would be great to have some generic reports/graphs, configured based on models and their attributes
data class AppReport(
    val name: String,
    val description: String? = null,
    val source: String? = null,
)
