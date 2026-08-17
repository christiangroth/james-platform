package de.chrgroth.james.platform.domain.model.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant

@JvmInline
value class AppDataId(val value: String)

data class AppData(
  val id: AppDataId,
  val userId: String,
  val installedAppId: InstalledAppId,
  val appVersion: VersionNumber,
  val entityType: EntityDefinitionId,
  val objectVersion: Int,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val data: Map<String, String?>,
)

// Unit separator control character (Unicode U+001F), used to encode a LIST property's item values into the single string stored in AppData.data.
private const val LIST_VALUE_SEPARATOR = ""

/** Encodes the item values of a LIST property into the single string stored in [AppData.data]. */
fun encodeListValue(items: List<String>): String = items.joinToString(LIST_VALUE_SEPARATOR)

/** Decodes a LIST property's stored string back into its individual item values. */
fun decodeListValue(raw: String?): List<String> = if (raw.isNullOrEmpty()) emptyList() else raw.split(LIST_VALUE_SEPARATOR)

private val objectMapper = jacksonObjectMapper()

/**
 * Encodes the (recursively structured) values of an OBJECT property's nested properties into the single string stored in [AppData.data].
 * Values of nested OBJECT properties are nested maps rather than already-encoded strings, so a single JSON document represents the whole tree.
 */
fun encodeObjectValue(values: Map<String, Any?>): String = objectMapper.writeValueAsString(values)

/** Decodes an OBJECT property's stored string back into the raw values of its nested properties. */
fun decodeObjectValue(raw: String?): Map<String, Any?> = if (raw.isNullOrEmpty()) emptyMap() else objectMapper.readValue(raw)
