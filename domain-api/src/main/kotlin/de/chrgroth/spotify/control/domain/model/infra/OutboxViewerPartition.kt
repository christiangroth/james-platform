package de.chrgroth.spotify.control.domain.model.infra

data class OutboxViewerPartition(
  val key: String,
  val tasks: List<OutboxTask>,
)
