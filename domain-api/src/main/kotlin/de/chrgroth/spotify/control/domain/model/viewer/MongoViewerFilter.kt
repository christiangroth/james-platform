package de.chrgroth.spotify.control.domain.model.viewer

data class MongoViewerFilter(
  val field: String,
  val operator: MongoViewerFilterOperator,
  val value: String,
)
