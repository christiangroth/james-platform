package de.chrgroth.james.platform.domain.model.app

@JvmInline
value class EntityDefinitionId(val value: String)

@JvmInline
value class PropertyId(val value: String)

data class EntityDefinition(
  val id: EntityDefinitionId,
  val name: String,
  val properties: List<Property> = emptyList(),
)

data class Property(
  val id: PropertyId,
  val name: String,
  val type: PropertyType,
  val nullable: Boolean = true,
  val constraints: Set<PropertyConstraint> = emptySet(),
)

enum class PropertyConstraint {
  NOT_NULL,
  UNIQUE_KEY,
}

enum class PropertyType {
  LONG,
  DOUBLE,
  BOOLEAN,
  STRING,
  DATE,
  TIME,
  DATETIME,
  REF,
  LIST,
  OBJECT,
}
