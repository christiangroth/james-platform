package de.chrgroth.james.platform.domain.model.viewer

data class MongoViewerFilter(
  val field: String,
  val operator: MongoViewerFilterOperator,
  val value: String,
)
