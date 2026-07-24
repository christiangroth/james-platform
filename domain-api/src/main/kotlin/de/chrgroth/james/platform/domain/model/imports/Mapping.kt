package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.PropertyId

enum class MappingType {
  FIND,
  FIND_OR_CREATE,
}

/** Simple, lossless-intent conversions that may be applied to a mapped field to resolve small type mismatches between source and target. */
enum class FieldMappingConversion {
  NONE,
  STRING_TO_LONG,
  STRING_TO_DOUBLE,
  STRING_TO_BOOLEAN,
  LONG_TO_DOUBLE,
  LONG_TO_STRING,
  DOUBLE_TO_STRING,
  BOOLEAN_TO_STRING,
  STRING_TO_DATE,
  STRING_TO_DATETIME,
}

/**
 * Maps a single target property of an [de.chrgroth.james.platform.domain.model.app.EntityDefinition] to a source
 * schema field. Either [sourcePath] (a path into the detected schema), [fallbackValue] (a static value used for
 * every record), or both may be set; a property with neither is considered unmapped.
 */
data class FieldMapping(
  val targetPropertyId: PropertyId,
  val sourcePath: String? = null,
  val conversion: FieldMappingConversion = FieldMappingConversion.NONE,
  val fallbackValue: String? = null,
)

data class Mapping(
  val name: String,
  val type: MappingType,
  val targetEntityDefinitionId: EntityDefinitionId,
  val fieldMappings: List<FieldMapping> = emptyList(),
)
