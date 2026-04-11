package de.chrgroth.james.platform.domain.model.infra

data class HealthStats(
  val outboxPartitions: List<OutboxPartitionStats>,
  val mongoCollectionStats: List<MongoCollectionStats>,
  val mongoQueryStats: List<MongoQueryStats>,
  val cronjobStats: List<CronjobStats>,
  val configurationStats: ConfigurationStats,
) {
  val mongoCollectionDocumentTotal: Long get() = mongoCollectionStats.sumOf { it.documentCount }
  val mongoCollectionSizeTotalKb: Long get() = mongoCollectionStats.sumOf { it.sizeKb }
  val outboxAllActive: Boolean get() = outboxPartitions.all { it.status == "ACTIVE" }
}
