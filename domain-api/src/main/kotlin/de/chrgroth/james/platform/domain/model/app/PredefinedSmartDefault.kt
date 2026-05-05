package de.chrgroth.james.platform.domain.model.app

/** Predefined smart default options that can be selected in the developer UI instead of writing a Kotlin script by hand. */
enum class PredefinedSmartDefault(
  val label: String,
  val script: String,
  val types: Set<PropertyType>,
) {
  DATE_TODAY(
    label = "Today",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString()",
    types = setOf(PropertyType.DATE),
  ),
  TIME_NOW(
    label = "Now",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalTime().toString()",
    types = setOf(PropertyType.TIME),
  ),
  TIME_NOW_CURRENT_SECOND(
    label = "Now (Current Second)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()",
    types = setOf(PropertyType.TIME),
  ),
  TIME_NOW_CURRENT_MINUTE(
    label = "Now (Current Minute)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()",
    types = setOf(PropertyType.TIME),
  ),
  TIME_NOW_CURRENT_HOUR(
    label = "Now (Current Hour)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString()",
    types = setOf(PropertyType.TIME),
  ),
  DATETIME_NOW(
    label = "Now",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().toString()",
    types = setOf(PropertyType.DATETIME),
  ),
  DATETIME_NOW_CURRENT_SECOND(
    label = "Now (Current Second)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()",
    types = setOf(PropertyType.DATETIME),
  ),
  DATETIME_NOW_CURRENT_MINUTE(
    label = "Now (Current Minute)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()",
    types = setOf(PropertyType.DATETIME),
  ),
  DATETIME_NOW_CURRENT_HOUR(
    label = "Now (Current Hour)",
    script = "java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString()",
    types = setOf(PropertyType.DATETIME),
  ),
  ;

  companion object {
    /** Returns all predefined smart defaults applicable to the given [type]. */
    fun forType(type: PropertyType): List<PredefinedSmartDefault> = entries.filter { type in it.types }

    /** Predefined smart defaults grouped by their applicable [PropertyType] name, supporting multi-type entries. */
    val byTypeName: Map<String, List<PredefinedSmartDefault>> =
      entries.flatMap { pd -> pd.types.map { type -> type.name to pd } }
        .groupBy({ it.first }, { it.second })

    /** JSON array representation of [byTypeName] for safe server-side embedding in HTML templates. */
    val byTypeNameJson: String = byTypeName.entries.joinToString(",", "{", "}") { (typeName, defaults) ->
      "\"${typeName}\":[${defaults.joinToString(",") { pd -> "{\"label\":${jsonEncode(pd.label)},\"script\":${jsonEncode(pd.script)}}" }}]"
    }

    private fun jsonEncode(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
  }
}
