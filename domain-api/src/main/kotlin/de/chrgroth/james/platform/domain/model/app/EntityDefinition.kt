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

sealed interface PropertyConstraint {
  // Generic constraints (all types)
  data object NotNull : PropertyConstraint
  data object UniqueKey : PropertyConstraint

  // Numeric constraints (LONG)
  data class MinLong(val min: Long) : PropertyConstraint
  data class MaxLong(val max: Long) : PropertyConstraint

  // Decimal constraints (DOUBLE)
  data class MinDouble(val min: Double) : PropertyConstraint
  data class MaxDouble(val max: Double) : PropertyConstraint

  // String constraints (STRING)
  data class MinLength(val min: Int) : PropertyConstraint
  data class MaxLength(val max: Int) : PropertyConstraint
  data class Pattern(val regex: String) : PropertyConstraint

  // List constraints (LIST)
  data class MinSize(val min: Int) : PropertyConstraint
  data class MaxSize(val max: Int) : PropertyConstraint
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
