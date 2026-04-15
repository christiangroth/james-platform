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

  companion object {
    val GENERAL_CONSTRAINTS: List<KClass<out PropertyConstraint>> = listOf(
      NotNull::class,
      UniqueKey::class,
    )
  }
}

enum class PropertyType {
  LONG {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS + listOf(
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
    )
  },
  DOUBLE {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS + listOf(
      PropertyConstraint.MinDouble::class,
      PropertyConstraint.MaxDouble::class,
    )
  },
  BOOLEAN {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  STRING {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS + listOf(
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
    )
  },
  DATE {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  TIME {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  DATETIME {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  REF {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  LIST {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS + listOf(
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )
  },
  OBJECT {
    override fun availableConstraints() = PropertyConstraint.GENERAL_CONSTRAINTS
  },
  ;

  abstract fun availableConstraints(): List<KClass<out PropertyConstraint>>
}
