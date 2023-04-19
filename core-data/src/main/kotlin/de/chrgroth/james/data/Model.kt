package de.chrgroth.james.data

import arrow.core.Validated
import arrow.core.ValidatedNel
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import java.util.*

// TODO #1 maybe have multiple buckets per AppInstallation in case of shared data?
// TODO #2 create reference from AppInstallation
// TODO #2 think about further detail attributes, e.g. retention options, statistics / meta data like number of objects etc
// TODO #2 add reference from DataObject?
data class DataBucket(
    val id: UUID,
)

// TODO #2 think about data object versioning
// TODO #2 think about derived fields/data
data class DataObject private constructor(
    val id: UUID,
    val appId: UUID,
    val appVersion: Semver,
    val datatypeName: String,
    val datatypeVersion: Long,
    val ownerId: UUID,
    val data: Map<String, Any>,
) {

    companion object {

        @Suppress("LongParameterList")
        fun create(
            id: UUID,
            appId: UUID,
            appVersion: Semver,
            datatypeName: String,
            datatypeVersion: Long,
            ownerId: UUID,
            data: Map<String, Any>,
        ): ValidatedNel<DomainError, DataObject> =
            Validated.validNel(
                DataObject(
                    id = id,
                    appId = appId,
                    appVersion = appVersion,
                    datatypeName = datatypeName,
                    datatypeVersion = datatypeVersion,
                    ownerId = ownerId,
                    data = data,
                )
            )
    }
}
