package de.chrgroth.spotify.control.domain.port.out.infra

interface OutboxPartitionObserver {
  fun onPartitionPaused(partitionKey: String, reason: String)
  fun onPartitionActivated(partitionKey: String)
}
