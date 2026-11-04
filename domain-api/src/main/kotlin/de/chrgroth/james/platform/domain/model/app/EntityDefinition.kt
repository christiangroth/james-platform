package de.chrgroth.james.platform.domain.model.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KClass

@JvmInline
value class EntityDefinitionId(val value: String)

@JvmInline
value class PropertyId(val value: String)

@JvmInline
value class ComputedPropertyId(val value: String)

enum class SortDirection { ASC, DESC }

data class SortCriteria(
  val propertyId: String,
  val direction: SortDirection,
)

data class EntityDefinition(
  val id: EntityDefinitionId,
  val name: String,
  val displayText: String? = null,
  val properties: List<Property> = emptyList(),
  val sortBy: List<SortCriteria> = emptyList(),
  val computedProperties: List<ComputedProperty> = emptyList(),
  val aggregations: List<AggregationDefinition> = emptyList(),
  // Declarative migration building blocks, executed in order on upgrade — see MigrationStep.
  val migrationSteps: List<MigrationStep> = emptyList(),
  // Marks the App Version's configuration: a singleton Entity with exactly one object per installation, created from the defaults (ADR 0027).
  val configuration: Boolean = false,
)

/**
 * A value derived from the object's other values by an [expression] of the platform's expression language (ADR 0029), evaluated whenever the
 * object is shown or read by a report, never stored. [unit] (LONG/DOUBLE only) is the unit the result is in, in its storage granularity like a
 * property's value. [legacyScript] is a Kotlin script stored before ADR 0029: it is no longer evaluated, only kept so the editor can show it as to
 * be replaced.
 */
data class ComputedProperty(
  val id: ComputedPropertyId,
  val name: String,
  val type: PropertyType,
  val expression: String? = null,
  val unit: PropertyUnit? = null,
  val legacyScript: String? = null,
) {
  init {
    require(unit == null || type.supportsUnit()) { "unit is not supported for computed property type $type" }
  }
}

data class Property(
  val id: PropertyId,
  val name: String,
  val type: PropertyType,
  val nullable: Boolean = true,
  val constraints: Set<PropertyConstraint> = emptySet(),
  val default: String? = null,
  // Expression (ADR 0029) evaluated when the create form opens, as a starting value the User may overwrite.
  val smartDefault: String? = null,
  // Kotlin smart default script stored before ADR 0029: no longer evaluated, only kept so the editor can show it as to be replaced.
  val legacySmartDefaultScript: String? = null,
  val valueProposals: List<String> = emptyList(),
  val targetEntityId: EntityDefinitionId? = null,
  // Type of the items contained in a LIST property, e.g. LIST<STRING>. Unused for non-LIST properties.
  val listItemType: PropertyType? = null,
  // Constraints applied to each item of a LIST property, using the item type's own constraints (UniqueKey excluded).
  val itemConstraints: Set<PropertyConstraint> = emptySet(),
  // Properties defining the structure of an OBJECT property, or of every item of a LIST of OBJECTs ([isObjectList]). Unused for other types.
  // An OBJECT may recursively contain further OBJECT properties; the items of a LIST of OBJECTs only simple fields (OBJECT_LIST_ITEM_FIELD_TYPES).
  val nestedProperties: List<Property> = emptyList(),
  // Unit assigned to a LONG/DOUBLE property's numeric value, e.g. distance stored in meters. Unused for other types.
  val unit: PropertyUnit? = null,
  // Lets a top-level REF property be entered as text of its target entity (with proposals, optionally auto-creating new targets). See ADR 0026.
  val referenceLookup: ReferenceLookup? = null,
) {
  init {
    require(unit == null || type.supportsUnit()) { "unit is not supported for property type $type" }
  }
}

/**
 * Entering a REF property by a STRING value of its target entity instead of picking from a select (ADR 0026): the form proposes the
 * existing values of [propertyId] (most referenced first) and resolves the typed text to the target object carrying it. With
 * [autoCreate], an unknown text creates a new target object on save, together with the referencing object.
 */
data class ReferenceLookup(
  val propertyId: PropertyId,
  val autoCreate: Boolean = false,
)

sealed interface PropertyConstraint {
  // Unique key constraint (scalar types only — not LIST or OBJECT)
  data object UniqueKey : PropertyConstraint

  // Numeric constraints (LONG)
  data class MinLong(val min: Long) : PropertyConstraint
  data class MaxLong(val max: Long) : PropertyConstraint
  data class StepLong(val step: Long) : PropertyConstraint

  // Decimal constraints (DOUBLE)
  data class MinDouble(val min: Double) : PropertyConstraint
  data class MaxDouble(val max: Double) : PropertyConstraint
  data class StepDouble(val step: Double) : PropertyConstraint

  // String constraints (STRING)
  data class MinLength(val min: Int) : PropertyConstraint
  data class MaxLength(val max: Int) : PropertyConstraint
  data class Pattern(val regex: String) : PropertyConstraint

  // List constraints (LIST)
  data class MinSize(val min: Int) : PropertyConstraint
  data class MaxSize(val max: Int) : PropertyConstraint

  // Date constraints (DATE)
  data class MinDate(val min: LocalDate) : PropertyConstraint
  data class MaxDate(val max: LocalDate) : PropertyConstraint

  // Time constraints (TIME)
  data class MinTime(val min: LocalTime) : PropertyConstraint
  data class MaxTime(val max: LocalTime) : PropertyConstraint

  // Datetime constraints (DATETIME)
  data class MinDatetime(val min: LocalDateTime) : PropertyConstraint
  data class MaxDatetime(val max: LocalDateTime) : PropertyConstraint
}

enum class PropertyType {
  LONG {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
      PropertyConstraint.StepLong::class,
    )

    override fun supportsUnit(): Boolean = true
  },
  DOUBLE {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinDouble::class,
      PropertyConstraint.MaxDouble::class,
      PropertyConstraint.StepDouble::class,
    )

    override fun supportsUnit(): Boolean = true
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
      PropertyConstraint.MinDate::class,
      PropertyConstraint.MaxDate::class,
    )
  },
  TIME {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinTime::class,
      PropertyConstraint.MaxTime::class,
    )
  },
  DATETIME {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
      PropertyConstraint.MinDatetime::class,
      PropertyConstraint.MaxDatetime::class,
    )
  },
  REF {
    override fun availableConstraints() = listOf(
      PropertyConstraint.UniqueKey::class,
    )

    override fun supportsDefault(): Boolean = false

    // e.g. `last(schuhe)`: the reference used most recently (ADR 0029)
    override fun supportsSmartDefault(): Boolean = true
  },
  LIST {
    override fun availableConstraints() = listOf(
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )

    override fun supportsDefault(): Boolean = false
  },
  OBJECT {
    override fun availableConstraints(): List<KClass<out PropertyConstraint>> = emptyList()

    override fun supportsDefault(): Boolean = false
  },
  ;

  abstract fun availableConstraints(): List<KClass<out PropertyConstraint>>

  open fun supportsDefault(): Boolean = true

  open fun supportsSmartDefault(): Boolean = supportsDefault()

  open fun supportsComputedProperty(): Boolean = supportsDefault() && this != REF

  /** Whether this type may carry a [Property.unit] (see also ADR 0016). */
  open fun supportsUnit(): Boolean = false

  /** Constraints applicable to LIST items of this type, i.e. its own constraints minus UniqueKey (not meaningful per list element). */
  fun availableItemConstraints(): List<KClass<out PropertyConstraint>> = availableConstraints().filter { it != PropertyConstraint.UniqueKey::class }

  companion object {
    /** Types that may be used as the item type of a LIST property. Lists of lists are not supported. */
    val LIST_ITEM_TYPES: List<PropertyType> = entries.filter { it != LIST }

    /** Types the fields of a LIST of OBJECTs' items may have: simple values only (no REF, LIST or OBJECT). */
    val OBJECT_LIST_ITEM_FIELD_TYPES: Set<PropertyType> = setOf(LONG, DOUBLE, BOOLEAN, STRING, DATE, TIME, DATETIME)
  }
}

/** Whether this is a LIST of OBJECTs: every item is an object with the fields in [Property.nestedProperties]. */
val Property.isObjectList: Boolean get() = type == PropertyType.LIST && listItemType == PropertyType.OBJECT

/** Whether this property's [Property.nestedProperties] are in use: an OBJECT property or a LIST of OBJECTs. */
val Property.hasNestedProperties: Boolean get() = type == PropertyType.OBJECT || isObjectList
