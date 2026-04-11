package de.chrgroth.james.platform.domain.model.infra

data class OutboxViewerPartition(
  val key: String,
  val tasks: List<OutboxTask>,
)
