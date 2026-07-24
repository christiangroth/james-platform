package de.chrgroth.james.platform.domain.model.infra

data class HealthStats(
  val mongoCollectionStats: List<MongoCollectionStats>,
  val mongoQueryStats: List<MongoQueryStats>,
  val cronjobStats: List<CronjobStats>,
  val configurationStats: ConfigurationStats,
  val scriptStats: List<ScriptExecutionStats>,
  val importCleanupStats: ImportCleanupStats,
) {
  val mongoCollectionDocumentTotal: Long get() = mongoCollectionStats.sumOf { it.documentCount }
  val mongoCollectionSizeTotalKb: Long get() = mongoCollectionStats.sumOf { it.sizeKb }
}
