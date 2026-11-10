package de.chrgroth.james.platform.domain.model.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@JvmInline
value class AppDataId(val value: String)

data class AppData(
  val id: AppDataId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val appVersion: VersionNumber,
  val lastValidatedWithVersion: VersionNumber,
  val entityType: EntityDefinitionId,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val data: Map<String, String?>,
  val importProvenance: ImportProvenance? = null,
)

/**
 * Snapshot of the [de.chrgroth.james.platform.domain.model.imports.ImportConnection] an [AppData] object was created
 * from, taken at accept time since the underlying [de.chrgroth.james.platform.domain.model.imports.ImportJob] (and its
 * `connectionId`) is deleted right after. [connectionName] and [sourceUrl] are snapshots rather than live lookups
 * because the connection can be renamed or deleted afterwards; [connectionId] stays nullable so it can still be
 * cleared if the connection is deleted later on. `createdAt` on [AppData] already doubles as the import timestamp.
 */
data class ImportProvenance(
  val connectionId: ImportConnectionId?,
  val connectionName: String,
  val sourceUrl: String,
)

// Unit separator control character (Unicode U+001F), used to encode a LIST property's item values into the single string stored in AppData.data.
private const val LIST_VALUE_SEPARATOR = ""

/** Encodes the item values of a LIST property into the single string stored in [AppData.data]. */
fun encodeListValue(items: List<String>): String = items.joinToString(LIST_VALUE_SEPARATOR)

/** Decodes a LIST property's stored string back into its individual item values. */
fun decodeListValue(raw: String?): List<String> = if (raw.isNullOrEmpty()) emptyList() else raw.split(LIST_VALUE_SEPARATOR)

/**
 * The item values of a LIST of [itemType] in their natural order: dates, date-times, times and numbers ascending (items that do not parse last,
 * in their given order); every other item type (texts, references, yes/no, objects) keeps the order the items were entered in.
 */
fun sortListItems(itemType: PropertyType?, items: List<String>): List<String> = when (itemType) {
  PropertyType.DATE -> items.sortedByParsed { runCatching { LocalDate.parse(it) }.getOrNull() }
  PropertyType.DATETIME -> items.sortedByParsed { runCatching { LocalDateTime.parse(it) }.getOrNull() }
  PropertyType.TIME -> items.sortedByParsed { runCatching { LocalTime.parse(it) }.getOrNull() }
  PropertyType.LONG, PropertyType.DOUBLE -> items.sortedByParsed { it.trim().toBigDecimalOrNull() }
  else -> items
}

private fun <T : Comparable<T>> List<String>.sortedByParsed(parse: (String) -> T?): List<String> = sortedWith(compareBy(nullsLast<T>()) { parse(it) })

private val objectMapper = jacksonObjectMapper()

/**
 * Encodes the (recursively structured) values of an OBJECT property's nested properties into the single string stored in [AppData.data].
 * Values of nested OBJECT properties are nested maps rather than already-encoded strings, so a single JSON document represents the whole tree.
 */
fun encodeObjectValue(values: Map<String, Any?>): String = objectMapper.writeValueAsString(values)

/** Decodes an OBJECT property's stored string back into the raw values of its nested properties. */
fun decodeObjectValue(raw: String?): Map<String, Any?> = if (raw.isNullOrEmpty()) emptyMap() else objectMapper.readValue(raw)
