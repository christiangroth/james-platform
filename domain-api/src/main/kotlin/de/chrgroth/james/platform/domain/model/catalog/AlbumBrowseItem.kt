package de.chrgroth.james.platform.domain.model.catalog

data class AlbumBrowseItem(
  val albumId: String,
  val title: String?,
  val imageLink: String?,
  val releaseDate: String?,
  val trackCount: Int,
  val durationMs: Long,
)
