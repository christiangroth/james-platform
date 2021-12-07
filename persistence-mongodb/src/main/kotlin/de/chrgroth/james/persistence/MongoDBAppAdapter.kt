package de.chrgroth.james.persistence

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.app.App
import de.chrgroth.james.app.AppCommandPersistencePort
import de.chrgroth.james.app.AppDatatype
import de.chrgroth.james.app.AppDatatypeDraft
import de.chrgroth.james.app.AppQueryPersistencePort
import de.chrgroth.james.app.AppReport
import de.chrgroth.james.app.AppVersion
import de.chrgroth.james.app.AppVersionChangeType
import de.chrgroth.james.app.AppVersionDraft
import de.chrgroth.james.app.AppVersionReleaseNotes
import kotlinx.datetime.Instant
import java.util.UUID

class MongoDBAppAdapter : AppQueryPersistencePort, AppCommandPersistencePort {

    override fun get(id: UUID): Maybe<App?> {
        TODO("#5 implement MongoDB adapter")
    }

    override fun find(): Maybe<Set<App>> {
        TODO("#5 implement MongoDB adapter")
    }

    override fun upsert(item: App): Maybe<App> {
        TODO("#5 implement MongoDB adapter")
    }

    override fun delete(id: UUID): Maybe<Unit> {
        TODO("#5 implement MongoDB adapter")
    }
}

// TODO #5 maybe store versions and development version separate because embedding increases document size
data class AppEntity(
    val id: UUID,
    val name: String,
    val developer: UUID,
    val description: String? = null,
    val versions: Set<AppVersionEntity> = emptySet(),
    val developmentVersion: AppVersionDraftEntity? = null,
    val discontinuationDate: Instant? = null,
) {
    internal fun toCoreRepresentation() = App(
        id = id,
        name = name,
        developer = developer,
        description = description,
        versions = versions.map { it.toCoreRepresentation() },
        developmentVersion = developmentVersion?.toCoreRepresentation(),
        discontinued = discontinuationDate != null,
    )
}

data class AppVersionDraftEntity(
    val datatypes: Set<AppDatatypeDraftEntity> = emptySet(),
    val reports: Set<AppReportEntity> = emptySet(),
) {
    internal fun toCoreRepresentation() = AppVersionDraft(
        datatypes = datatypes.map { it.toCoreRepresentation() }.toSet(),
        reports = reports.map { it.toCoreRepresentation() }.toSet(),
    )
}

data class AppVersionEntity(
    val id: Semver,
    val isBugfix: Boolean,
    val note: String,
    val datatypes: Set<AppDatatypeEntity> = emptySet(),
    val reports: Set<AppReportEntity> = emptySet(),
) {
    internal fun toCoreRepresentation() = AppVersion(
        version = id,
        releaseNotes = AppVersionReleaseNotes(
            changeType = if (isBugfix) AppVersionChangeType.BUGFIX else AppVersionChangeType.FEATURE,
            note = note
        ),
        datatypes = datatypes.map { it.toCoreRepresentation() }.toSet(),
        reports = reports.map { it.toCoreRepresentation() }.toSet(),
    )
}

data class AppDatatypeDraftEntity(
    val name: String,
    val schemaContent: String,
    val description: String? = null,
) {
    internal fun toCoreRepresentation() = AppDatatypeDraft(
        name = name,
        schemaContent = schemaContent,
        description = description,
    )
}

data class AppDatatypeEntity(
    val name: String,
    val version: Long,
    val schemaContent: String,
    val description: String? = null,
) {
    internal fun toCoreRepresentation() = AppDatatype(
        name = name,
        version = version,
        schemaContent = schemaContent,
        description = description,
    )
}

data class AppReportEntity(
    val name: String,
    val description: String? = null,
    val source: String? = null,
) {
    internal fun toCoreRepresentation() = AppReport(
        name = name,
        description = description,
        source = source,
    )
}
