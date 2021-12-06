package de.chrgroth.james.data

import java.time.Instant
import java.util.UUID

data class DataObject(
    val id: Long,

    val createdAt: Instant,
    val createdBy: UUID,

    val lastUpdatedAt: Instant,
    val lastUpdatedBy: UUID,

    val markedDeletedAt: Instant,
    val markedDeletedBy: UUID,

    // TODO #5 typeVersion?
)
