package de.chrgroth.spotify.control.domain.port.`in`.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition

interface OutboxViewerPort {
  fun getPartitions(): List<OutboxViewerPartition>
}
