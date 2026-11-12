package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.error.InvalidExpressionError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.isObjectList
import de.chrgroth.james.platform.domain.model.infra.AppTimeZone
import de.chrgroth.james.platform.domain.report.ReportExpressions
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * How computed properties and smart defaults (ADR 0029) use the expression language ([Expression]) on one object of an entity: fields are the
 * entity's properties by name (properties nested in OBJECT properties as `object.field`), read in their raw storage form typed by the property
 * (references as the referenced object's id, lists and objects as text); `config.<field>` reads the installation's configuration the same way.
 * List functions ([Expression.LIST_FUNCTIONS]) read a top-level LIST property's items (of the object, or of the configuration as `config.<field>`):
 * the fields of a LIST of OBJECTs' items by name, the item value of a list of simple values as [Expression.ITEM].
 */
internal object EntityExpressions {

  private const val SEPARATOR = '.'
  private const val MAX_CACHED = 1000
  private val parsed = ConcurrentHashMap<String, Expression>()

  /** Parses [source] (cached, definitions are evaluated for every object shown); null if it is invalid - validation on save reports why. */
  fun parseOrNull(source: String): Expression? {
    parsed[source]?.let { return it }
    val expression = runCatching { Expression.parse(source) }.getOrNull() ?: return null
    if (parsed.size >= MAX_CACHED) parsed.clear()
    parsed[source] = expression
    return expression
  }

  /**
   * The property [name] refers to among [properties], as path outermost first: a property by name (also one whose name contains a dot) or one
   * nested in OBJECT properties (`adresse.ort`); null if there is none.
   */
  fun resolve(properties: List<Property>, name: String): List<Property>? {
    properties.find { it.name == name }?.let { return listOf(it) }
    val segments = name.split(SEPARATOR)
    if (segments.size < 2) return null
    val path = mutableListOf<Property>()
    var current = properties
    segments.forEachIndexed { index, segment ->
      val property = current.find { it.name == segment } ?: return null
      path += property
      if (index < segments.lastIndex) {
        if (property.type != PropertyType.OBJECT) return null
        current = property.nestedProperties
      }
    }
    return path
  }

  /** The raw value of the property at [path] in [data] (raw values by top-level property id; OBJECT values are JSON maps keyed by property id). */
  fun raw(data: Map<String, String?>, path: List<Property>): String? {
    val top = data[path.first().id.value]
    if (path.size == 1) return top
    var current: Map<*, *> = runCatching { decodeObjectValue(top) }.getOrNull() ?: return null
    for (parent in path.drop(1).dropLast(1)) {
      current = current[parent.id.value] as? Map<*, *> ?: return null
    }
    return current[path.last().id.value]?.toString()
  }

  /** The expression type a value of [property] is read as. */
  fun typeOf(property: Property): ExpressionType? = ReportExpressions.typeOf(property.type)

  fun value(data: Map<String, String?>, path: List<Property>): ExpressionValue? = ExpressionValue.of(raw(data, path), typeOf(path.last()))

  /** The top-level LIST property [name] among [properties], null if there is none. */
  fun listProperty(properties: List<Property>, name: String): Property? = properties.find { it.name == name && it.type == PropertyType.LIST }

  /** The expression type of the item field [name] of [list]: a field of a LIST of OBJECTs' items, or [Expression.ITEM] of a list of simple values. */
  fun itemTypeOf(list: Property, name: String): Known? = if (list.isObjectList) {
    list.nestedProperties.find { it.name == name }?.let { Known(typeOf(it)) }
  } else {
    list.listItemType?.takeIf { name == Expression.ITEM }?.let { Known(ReportExpressions.typeOf(it)) }
  }

  /** Reads the item field [name] of one stored item of [list] (an item's JSON object for a LIST of OBJECTs, else the item value itself). */
  fun itemValue(list: Property, item: String, name: String): ExpressionValue? = if (list.isObjectList) {
    list.nestedProperties.find { it.name == name }?.let { field ->
      val values = runCatching { decodeObjectValue(item) }.getOrNull() ?: return null
      ExpressionValue.of(values[field.id.value]?.toString(), typeOf(field))
    }
  } else {
    list.listItemType?.takeIf { name == Expression.ITEM }?.let { ExpressionValue.of(item, ReportExpressions.typeOf(it)) }
  }

  /** The stored items ([raw]) of [list] as contexts reading each item's fields, everything else from [parent]. */
  fun itemContexts(parent: ExpressionContext, list: Property, raw: String?): List<ExpressionContext> =
    decodeListValue(raw).map { item -> ItemExpressionContext(parent) { field -> itemValue(list, item, field) } }

  /** [now] as wall-clock date-time in the application's time zone. */
  fun localNow(now: Instant): LocalDateTime = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(AppTimeZone.zone).toLocalDateTime()

  /** [value] stored as a value of [type]: references as text (the referenced object's id), everything else as [ReportExpressions.raw]. */
  fun store(value: ExpressionValue?, type: PropertyType): String? = when (type) {
    PropertyType.REF -> ExpressionValue.toText(value)?.takeIf { it.isNotBlank() }
    else -> ReportExpressions.raw(value, type)
  }

  // region latest entry

  /**
   * The entity's latest existing object in [entries] (objects of other entities are ignored): by its time field - the first DATE/DATETIME
   * property it is sorted by, else its first DATE/DATETIME property - and by creation time where that is missing or equal.
   */
  fun latest(entity: EntityDefinition, entries: List<AppData>): AppData? {
    val own = entries.filter { it.entityType == entity.id }
    if (own.isEmpty()) return null
    val temporal = { property: Property -> property.type == PropertyType.DATE || property.type == PropertyType.DATETIME }
    val timeProperty = entity.sortBy.firstNotNullOfOrNull { criteria -> entity.properties.find { it.id.value == criteria.propertyId }?.takeIf(temporal) }
      ?: entity.properties.firstOrNull(temporal)
    val time = { item: AppData -> timeProperty?.let { property -> item.data[property.id.value]?.let { ExpressionValue.toDateTime(ExpressionValue.Text(it)) } } }
    return own.maxWithOrNull(compareBy<AppData, LocalDateTime?>(nullsFirst()) { time(it) }.thenBy { it.createdAt })
  }

  // endregion

  // region validation

  /** Where an expression is used: computed properties may not read the entity's existing objects ([Expression.ENTRY_FUNCTIONS]). */
  enum class Usage(val functions: Set<String>) {
    COMPUTED_PROPERTY(Expression.FUNCTIONS.keys - Expression.ENTRY_FUNCTIONS),
    SMART_DEFAULT(Expression.FUNCTIONS.keys),
    DISPLAY_TEXT(Expression.FUNCTIONS.keys - Expression.ENTRY_FUNCTIONS),
  }

  /** Validates the expression of [computed] on [entity]: it may read the entity's properties and the computed properties defined before it. */
  fun validateComputed(entity: EntityDefinition, computed: ComputedProperty, source: String, configEntity: EntityDefinition?): InvalidExpressionError? {
    val before = entity.computedProperties.takeWhile { it.id != computed.id }
    return validate(source, Usage.COMPUTED_PROPERTY, computed.type, configEntity, entity.properties) { name ->
      resolve(entity.properties, name)?.let { Known(typeOf(it.last())) } ?: before.find { it.name == name }?.let { Known(ReportExpressions.typeOf(it.type)) }
    }
  }

  /** Validates the smart default of [property] (a top-level property of [entity]): it may read the entity's properties. */
  fun validateSmartDefault(entity: EntityDefinition, property: Property, source: String, configEntity: EntityDefinition?): InvalidExpressionError? =
    validate(source, Usage.SMART_DEFAULT, property.type, configEntity, entity.properties) { name -> resolve(entity.properties, name)?.let { Known(typeOf(it.last())) } }

  /** Validates a `${ expression }` placeholder of the display text of [entity]: it may read all its properties (nullable ones too) and computed properties, no configuration. */
  fun validateDisplayText(entity: EntityDefinition, source: String): InvalidExpressionError? =
    validate(source, Usage.DISPLAY_TEXT, PropertyType.STRING, null, entity.properties) { name ->
      resolve(entity.properties, name)?.let { Known(typeOf(it.last())) } ?: entity.computedProperties.find { it.name == name }?.let { Known(ReportExpressions.typeOf(it.type)) }
    }

  /** A known field and its type (null: unknown type). */
  class Known(val type: ExpressionType?)

  private fun validate(
    source: String,
    usage: Usage,
    resultType: PropertyType,
    configEntity: EntityDefinition?,
    properties: List<Property>,
    field: (String) -> Known?,
  ): InvalidExpressionError? {
    val expression = try {
      Expression.parse(source)
    } catch (e: Expression.ParseException) {
      return when {
        e.unknownFunction != null -> InvalidExpressionError(ExpressionErrorCode.UNKNOWN_FUNCTION, listOf(e.unknownFunction, usage.functions.joinToString(", ")))
        e.fieldArgumentOf != null -> InvalidExpressionError(ExpressionErrorCode.FIELD_ARGUMENT_REQUIRED, listOf(e.fieldArgumentOf))
        else -> InvalidExpressionError(ExpressionErrorCode.SYNTAX, listOf(e.position.toString(), e.detail))
      }
    }
    expression.functions.firstOrNull { it !in usage.functions }?.let {
      return InvalidExpressionError(ExpressionErrorCode.FUNCTION_NOT_AVAILABLE, listOf(it, usage.functions.joinToString(", ")))
    }
    val types = HashMap<String, ExpressionType?>()
    for (identifier in expression.identifiers) {
      val configPath = Expression.configPath(identifier)
      if (configPath != null) {
        val path = configEntity?.let { resolve(it.properties, configPath) } ?: return InvalidExpressionError(ExpressionErrorCode.UNKNOWN_CONFIG_FIELD, listOf(configPath))
        types[identifier] = typeOf(path.last())
      } else {
        types[identifier] = (field(identifier) ?: return InvalidExpressionError(ExpressionErrorCode.UNKNOWN_FIELD, listOf(identifier))).type
      }
    }
    val itemTypes = HashMap<Pair<String, String>, ExpressionType?>()
    for ((listName, itemNames) in expression.itemIdentifiers) {
      val list = Expression.configPath(listName).let { path -> if (path != null) configEntity?.let { listProperty(it.properties, path) } else listProperty(properties, listName) }
        ?: return InvalidExpressionError(ExpressionErrorCode.LIST_FIELD_REQUIRED, listOf(listName))
      for (itemName in itemNames) {
        val known = itemTypeOf(list, itemName) ?: return InvalidExpressionError(ExpressionErrorCode.UNKNOWN_ITEM_FIELD, listOf(itemName, listName))
        itemTypes[listName to itemName] = known.type
      }
    }
    val check = expression.check({ types[it] }, { list, name -> itemTypes[list to name] })
    check.problems.firstOrNull()?.let { problem ->
      val code = when (problem.kind) {
        ExpressionProblem.Kind.OPERAND_TYPE -> ExpressionErrorCode.OPERAND_TYPE
        ExpressionProblem.Kind.ARGUMENT_TYPE -> ExpressionErrorCode.ARGUMENT_TYPE
        ExpressionProblem.Kind.INCOMPARABLE -> ExpressionErrorCode.INCOMPARABLE
        ExpressionProblem.Kind.MIXED_TYPES -> ExpressionErrorCode.MIXED_TYPES
      }
      return InvalidExpressionError(code, problem.args)
    }
    val inferred = check.type ?: return null
    val fits = if (resultType == PropertyType.REF) inferred == ExpressionType.TEXT else ReportExpressions.converts(inferred, resultType)
    return if (fits) null else InvalidExpressionError(ExpressionErrorCode.RESULT_TYPE, listOf(resultType.name, inferred.name))
  }

  // endregion
}

/** The evaluation context of computed properties and smart defaults on one object. */
internal class EntityExpressionContext(
  private val properties: List<Property>,
  private val data: Map<String, String?>,
  private val config: Map<String, String?>,
  private val configEntity: EntityDefinition?,
  override val now: LocalDateTime,
  private val extraField: (String) -> ExpressionValue? = { null },
  private val lastValue: (String) -> ExpressionValue? = { null },
) : ExpressionContext {

  override val today: LocalDate get() = now.toLocalDate()

  override fun field(name: String): ExpressionValue? {
    val path = EntityExpressions.resolve(properties, name) ?: return extraField(name)
    return EntityExpressions.value(data, path)
  }

  override fun config(path: String): ExpressionValue? {
    val resolved = configEntity?.let { EntityExpressions.resolve(it.properties, path) } ?: return null
    return EntityExpressions.value(config, resolved)
  }

  override fun last(name: String): ExpressionValue? = lastValue(name)

  override fun items(name: String): List<ExpressionContext>? {
    val list = EntityExpressions.listProperty(properties, name) ?: return null
    return EntityExpressions.itemContexts(this, list, data[list.id.value])
  }

  override fun configItems(path: String): List<ExpressionContext>? {
    val list = configEntity?.let { EntityExpressions.listProperty(it.properties, path) } ?: return null
    return EntityExpressions.itemContexts(this, list, config[list.id.value])
  }
}

/** One list item inside a list function: its own fields, everything else (configuration, today, now) from the enclosing [parent]. */
internal class ItemExpressionContext(private val parent: ExpressionContext, private val itemField: (String) -> ExpressionValue?) : ExpressionContext {
  override fun field(name: String): ExpressionValue? = itemField(name)

  override fun config(path: String): ExpressionValue? = parent.config(path)

  override fun configItems(path: String): List<ExpressionContext>? = parent.configItems(path)

  override val today: LocalDate get() = parent.today

  override val now: LocalDateTime get() = parent.now
}
