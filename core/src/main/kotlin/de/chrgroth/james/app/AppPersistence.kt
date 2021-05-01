package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.CrudRepository
import kotlinx.datetime.Instant
import java.util.UUID

interface AppPersistencePort: CrudRepository<AppEntity, UUID>

// TODO maybe store versions and development version separate because embedding increases document size
data class AppEntity(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val versions: Set<AppVersionEntity> = emptySet(),
    val developmentVersion: AppVersionEntity? = null,
    val discontinuationDate: Instant? = null,
) {
    internal fun toDescriptor() = AppDescription(
        id = id,
        name = name,
        description = description,
        versions = versions.map { it.id }.sortedDescending(),
        hasDevelopmentVersion = developmentVersion != null,
        isDiscontinued = discontinuationDate != null,
    )
}

data class AppVersionEntity(
    val id: Semver,
    val isBugfix: Boolean,
    val note: String,
    val models: Set<AppModelEntity> = emptySet(),
    val reports: Set<AppReportEntity> = emptySet(),
) {
    internal fun toDescriptor() = AppVersionDescription(
        version = id,
        releaseNotes = AppVersionReleaseNotes(
            changeType = if(isBugfix) AppVersionChangeType.BUGFIX else AppVersionChangeType.FEATURE,
            note = note
        ),
    )
}

data class AppModelEntity(
    val name: String,
    val version: Long,
    val schema: String? = null,
    val description: String? = null,
)

data class AppReportEntity(
    val name: String,
    val description: String? = null,
    val source: String? = null,
)
