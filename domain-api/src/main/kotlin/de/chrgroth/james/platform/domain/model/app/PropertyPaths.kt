package de.chrgroth.james.platform.domain.model.app

/**
 * A property of an [EntityDefinition] together with the OBJECT properties enclosing it ([parents], outermost first) - empty [parents]
 * for a top-level property. Used wherever a property nested in OBJECT properties is addressed by its own id, e.g. an
 * [AggregationDefinition.sourceProperty].
 */
data class PropertyPath(
  val property: Property,
  val parents: List<Property> = emptyList(),
) {
  /** The property's name prefixed with the names of its enclosing OBJECT properties, e.g. `bewertung > zufriedenheit`. */
  val displayName: String get() = (parents.map { it.name } + property.name).joinToString(PATH_SEPARATOR)

  /** The ids of the enclosing OBJECT properties, outermost first - see [AggregationDefinition.sourcePath]. */
  val parentIds: List<PropertyId> get() = parents.map { it.id }

  companion object {
    const val PATH_SEPARATOR = " > "
  }
}

/** Every property of this entity in declaration order, OBJECT properties first followed by their nested properties (recursively). */
fun EntityDefinition.propertyPaths(): List<PropertyPath> = propertyPaths(properties, emptyList())

/** The [PropertyPath] of the (top-level or nested) property [id], or null if this entity has no such property. */
fun EntityDefinition.findPropertyPath(id: PropertyId): PropertyPath? = propertyPaths().find { it.property.id == id }

private fun propertyPaths(properties: List<Property>, parents: List<Property>): List<PropertyPath> = properties.flatMap { property ->
  listOf(PropertyPath(property, parents)) + if (property.type == PropertyType.OBJECT) propertyPaths(property.nestedProperties, parents + property) else emptyList()
}
