package de.chrgroth.james.platform.domain.port.`in`.infra

import de.chrgroth.james.platform.domain.model.infra.OutboxViewerPartition

interface OutboxViewerPort {
  fun getPartitions(): List<OutboxViewerPartition>
}
