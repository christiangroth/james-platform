package de.chrgroth.james.platform.domain.model.app

import kotlin.reflect.KClass

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
  // Unique key constraint (scalar types only — not LIST or OBJECT)
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
  LONG {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
    )
  },
  DOUBLE {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinDouble::class,
      PropertyConstraint.MaxDouble::class,
    )
  },
  BOOLEAN {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )
  },
  STRING {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
    )
  },
  DATE {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )
  },
  TIME {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )
  },
  DATETIME {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )
  },
  REF {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )
  },
  LIST {
    override fun availableConstraints() = listOf(
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )
  },
  OBJECT {
    override fun availableConstraints(): List<KClass<out PropertyConstraint>> = emptyList()
  },
  ;

  abstract fun availableConstraints(): List<KClass<out PropertyConstraint>>
}
