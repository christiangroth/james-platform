package de.chrgroth.james.platform.domain.model.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import java.time.Instant

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

private val objectMapper = jacksonObjectMapper()

/**
 * Encodes the (recursively structured) values of an OBJECT property's nested properties into the single string stored in [AppData.data].
 * Values of nested OBJECT properties are nested maps rather than already-encoded strings, so a single JSON document represents the whole tree.
 */
fun encodeObjectValue(values: Map<String, Any?>): String = objectMapper.writeValueAsString(values)

/** Decodes an OBJECT property's stored string back into the raw values of its nested properties. */
fun decodeObjectValue(raw: String?): Map<String, Any?> = if (raw.isNullOrEmpty()) emptyMap() else objectMapper.readValue(raw)
