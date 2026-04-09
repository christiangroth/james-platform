package de.chrgroth.spotify.control.domain.catalog

data class CatalogSyncRequest(
  val trackId: String,
  val artistIds: List<String>,
)
