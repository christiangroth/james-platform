package de.chrgroth.james.platform.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  data object Domain : DomainOutboxPartition {
    override val key = "domain"
  }

  companion object {
    val all: List<DomainOutboxPartition> = listOf(Domain)
  }
}
