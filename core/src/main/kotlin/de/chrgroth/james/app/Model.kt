package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.computeNext
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
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

    // TODO update tests with discontinued
    internal fun createDevelopmentVersion() =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion != null -> Maybe.Error(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
            else -> latestVersion?.let {
                Maybe.Result(copy(developmentVersion = AppVersionDraft(datatypes = it.datatypes.toSet(), reports = it.reports.toSet())))
            } ?: Maybe.Result(copy(developmentVersion = AppVersionDraft()))
        }

    // TODO update tests with discontinued
    internal fun updateDevelopmentVersion(datatype: AppDatatype) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            else -> {
                val upsertResult = developmentVersion?.upsert(datatype)
                    ?: Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)

                when(upsertResult) {
                    is Maybe.Error -> upsertResult.convert()
                    is Maybe.Result -> Maybe.Result(copy(developmentVersion = upsertResult.value))
                }
            }
        }

    // TODO update tests with discontinued
    internal fun updateDevelopmentVersion(report: AppReport) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            else -> {
                val upsertResult = developmentVersion?.upsert(report)
                    ?: Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)

                when(upsertResult) {
                    is Maybe.Error -> upsertResult.convert()
                    is Maybe.Result -> Maybe.Result(copy(developmentVersion = upsertResult.value))
                }
            }
        }

    // TODO update tests with discontinued
    internal fun releaseDevelopmentVersion(releaseNotes: AppVersionReleaseNotes) =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            developmentVersion == null -> Maybe.Error(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
            // TODO validate release notes
            else -> {
                val nextVersion = AppVersion(
                    version = releaseNotes.computeVersion(latestVersion, developmentVersion),
                    releaseNotes = releaseNotes,
                    datatypes = developmentVersion.datatypes.toSet(),
                    reports = developmentVersion.reports.toSet(),
                )
                Maybe.Result(copy(developmentVersion = null, versions = versions.plus(nextVersion)))
            }
        }

    // TODO add tests
    internal fun discontinue() =
        when {
            !status.allowsChanges -> Maybe.Error(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
            else -> Maybe.Result(copy(discontinued = true))
        }

    // TODO add tests
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
    val datatypes: Set<AppDatatype> = emptySet(),
    val reports: Set<AppReport> = emptySet(),
) {

    // TODO validate datatype and write tests
    internal fun upsert(datatype: AppDatatype): Maybe<AppVersionDraft> {
        return Maybe.Result(copy(datatypes = datatypes.upsert(datatype)))
    }

    private fun Set<AppDatatype>.upsert(datatype: AppDatatype) =
        filterNot { it.name == datatype.name }.plus(datatype).toSet()


    // TODO validate report and write tests
    internal fun upsert(report: AppReport): Maybe<AppVersionDraft> {
        return Maybe.Result(copy(reports = reports.upsert(report)))
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
