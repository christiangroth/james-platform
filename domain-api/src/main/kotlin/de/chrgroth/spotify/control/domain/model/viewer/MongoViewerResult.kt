package de.chrgroth.spotify.control.domain.model.viewer

data class MongoViewerResult(
  val collections: List<String>,
  val selectedCollection: String,
  val fields: List<MongoViewerField>,
  val sortField: String,
  val sortDesc: Boolean,
  val documents: List<String>,
  val totalCount: Long,
  val page: Int,
  val pageSize: Int,
  val totalPages: Int,
  val hasPrev: Boolean,
  val hasNext: Boolean,
  val prevPage: Int,
  val nextPage: Int,
)
