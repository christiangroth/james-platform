package de.chrgroth.james.persistence

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.app.App
import de.chrgroth.james.app.AppModel
import de.chrgroth.james.app.AppPersistencePort
import de.chrgroth.james.app.AppReport
import de.chrgroth.james.app.AppVersion
import de.chrgroth.james.app.AppVersionChangeType
import de.chrgroth.james.app.AppVersionDraft
import de.chrgroth.james.app.AppVersionReleaseNotes
import kotlinx.datetime.Instant
import java.util.UUID

class MongoDBAppAdapter : AppPersistencePort {

    override fun get(id: UUID): App? {
        TODO()
    }

    override fun find(): Set<App> {
        TODO()
    }

    override fun create(item: App): Maybe<App> {
        TODO()
    }

    override fun update(item: App): Maybe<App> {
        TODO()
    }

    override fun delete(id: UUID): Maybe<Unit> {
        TODO()
    }
}


// TODO maybe store versions and development version separate because embedding increases document size
data class AppEntity(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val versions: Set<AppVersionEntity> = emptySet(),
    val developmentVersion: AppVersionEntity? = null,
    val discontinuationDate: Instant? = null,
) {
    internal fun toCoreRepresentation() = App(
        id = id,
        name = name,
        description = description,
        versions = versions.map { it.toCoreRepresentation() }.toSet(),
        developmentVersion = if(developmentVersion != null)
            AppVersionDraft(
                models = developmentVersion.models.map { it.toCoreRepresentation() }.toSet(),
                reports = developmentVersion.reports.map { it.toCoreRepresentation() }.toSet(),
            )
        else null,
        discontinued = discontinuationDate != null,
    )
}

data class AppVersionEntity(
    val id: Semver,
    val isBugfix: Boolean,
    val note: String,
    val models: Set<AppModelEntity> = emptySet(),
    val reports: Set<AppReportEntity> = emptySet(),
) {
    internal fun toCoreRepresentation() = AppVersion(
        version = id,
        releaseNotes = AppVersionReleaseNotes(
            changeType = if(isBugfix) AppVersionChangeType.BUGFIX else AppVersionChangeType.FEATURE,
            note = note
        ),
        models = models.map { it.toCoreRepresentation() }.toSet(),
        reports = reports.map { it.toCoreRepresentation() }.toSet(),
    )
}

data class AppModelEntity(
    val name: String,
    val version: Long,
    val schema: String? = null,
    val description: String? = null,
) {
    fun toCoreRepresentation() = AppModel(
        name = name,
        version = version,
        schema = schema,
        description = description,

        )
}

data class AppReportEntity(
    val name: String,
    val description: String? = null,
    val source: String? = null,
) {
    fun toCoreRepresentation() = AppReport(
        name = name,
        description = description,
        source = source,
    )
}
