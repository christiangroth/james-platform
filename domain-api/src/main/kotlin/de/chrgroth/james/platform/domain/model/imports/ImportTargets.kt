package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType

/**
 * A property an import [FieldMapping] can target: a top-level property of the target entity, or a property nested in
 * one or more OBJECT properties ([parents], outermost first). OBJECT properties themselves are never targets - their
 * value is assembled from their nested targets.
 */
data class ImportTarget(
  val property: Property,
  val parents: List<Property> = emptyList(),
) {
  /** The property's name prefixed with the names of its enclosing OBJECT properties, e.g. `bewertung > zufriedenheit`. */
  val displayName: String get() = (parents.map { it.name } + property.name).joinToString(PATH_SEPARATOR)

  /** Whether every imported object must carry a value: the property and all of its enclosing OBJECT properties are non-nullable. */
  val effectivelyMandatory: Boolean get() = !property.nullable && parents.none { it.nullable }

  /** The top-level property of the target entity this target belongs to (the property itself for a top-level target). */
  val topLevelProperty: Property get() = parents.firstOrNull() ?: property

  companion object {
    const val PATH_SEPARATOR = " > "
  }
}

/** All import targets of this entity in declaration order, descending into OBJECT properties (see [ImportTarget]). */
fun EntityDefinition.importTargets(): List<ImportTarget> = importTargets(properties, emptyList())

private fun importTargets(properties: List<Property>, parents: List<Property>): List<ImportTarget> = properties.flatMap { property ->
  if (property.type == PropertyType.OBJECT) importTargets(property.nestedProperties, parents + property) else listOf(ImportTarget(property, parents))
}
