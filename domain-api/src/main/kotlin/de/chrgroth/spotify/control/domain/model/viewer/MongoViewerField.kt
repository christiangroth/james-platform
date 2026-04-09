package de.chrgroth.spotify.control.domain.model.viewer

data class MongoViewerField(
  val name: String,
  val fieldType: MongoViewerFieldType,
  val containsValue: String = "",
  val equalsValue: String = "",
  val inValue: String = "",
  val notInValue: String = "",
)
