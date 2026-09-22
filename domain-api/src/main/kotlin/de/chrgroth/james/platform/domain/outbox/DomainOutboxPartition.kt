package de.chrgroth.james.platform.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  /**
   * Shared by [DomainOutboxEvent.AcceptDryRun], [DomainOutboxEvent.UninstallApp], [DomainOutboxEvent.DeleteApp],
   * [DomainOutboxEvent.DeleteUser] and [DomainOutboxEvent.AutoUpgradeInstallation]. [workerCount] lets multiple of
   * these tasks dispatch concurrently, while each event's `groupId` (the affected entity's id) still serializes
   * operations against the *same* entity in enqueue order - see ADR 0019's "groupId adoption" section. This
   * supersedes the static hash-sharded partition pool (`UserDeletion-0..N`) the ADR previously floated as the only
   * workaround under the old library's one-worker-per-partition model.
   */
  data object Domain : DomainOutboxPartition {
    override val key = "domain"
    override val workerCount = 4
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
