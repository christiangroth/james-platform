package de.chrgroth.spotify.control.domain.model.infra

data class HealthStats(
  val outgoingRequestStats: List<OutgoingRequestStats>,
  val outboxPartitions: List<OutboxPartitionStats>,
  val mongoCollectionStats: List<MongoCollectionStats>,
  val mongoQueryStats: List<MongoQueryStats>,
  val cronjobStats: List<CronjobStats>,
  val predicateStats: List<PredicateStats>,
  val configurationStats: ConfigurationStats,
) {
  val mongoCollectionDocumentTotal: Long get() = mongoCollectionStats.sumOf { it.documentCount }
  val mongoCollectionSizeTotalKb: Long get() = mongoCollectionStats.sumOf { it.sizeKb }
  val outboxAllActive: Boolean get() = outboxPartitions.all { it.status == "ACTIVE" }
  val playbackActive: Boolean? get() = predicateStats.firstOrNull { it.name == "playbackActive" }?.active
}
