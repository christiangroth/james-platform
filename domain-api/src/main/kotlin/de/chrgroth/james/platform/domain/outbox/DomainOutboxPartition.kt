package de.chrgroth.james.platform.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  data object Domain : DomainOutboxPartition {
    override val key = "domain"
  }

  /** Dedicated partition for [DomainOutboxEvent.GenerateTestData], kept separate from [Domain] per ADR 0019's mandatory per-operation partition separation. */
  data object TestDataGeneration : DomainOutboxPartition {
    override val key = "test-data-generation"
  }

  /** Dedicated partition for [DomainOutboxEvent.RecomputeAggregation] (see docs/adr/0020-aggregation-definitions.md), kept separate from [Domain] per ADR 0019's mandatory per-operation partition separation. */
  data object AggregationRecompute : DomainOutboxPartition {
    override val key = "aggregation-recompute"
  }

  companion object {
    val all: List<DomainOutboxPartition> = listOf(Domain, TestDataGeneration, AggregationRecompute)
  }
}
