package de.chrgroth.james.platform.domain.catalog

data class CatalogSyncRequest(
  val trackId: String,
  val artistIds: List<String>,
)
