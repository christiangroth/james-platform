package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import java.util.UUID

// TODO add reference to owner/developer later

data class App(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val versions: List<AppVersion> = emptyList(),
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
