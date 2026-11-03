package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EnergyGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.MassGranularity
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.VolumeGranularity

/**
 * A query field resolved against the version's entities: a (computed) property of the queried entity or - via one `ref` hop ([refProperty],
 * e.g. `schuhe.name`) - of the referenced entity ([owner]); a property nested in OBJECT properties is reached through them ([objectPath],
 * outermost first, e.g. `bewertung.zufriedenheit`, also behind a `ref` hop), mirroring `AggregationDefinition.sourcePath`. Exactly one of
 * [property]/[computed] is set; nested fields are always plain properties.
 */
internal data class ResolvedField(
  val path: String,
  val owner: EntityDefinition,
  val refProperty: Property?,
  val property: Property?,
  val computed: ComputedProperty?,
  val objectPath: List<Property> = emptyList(),
) {
  val type: PropertyType get() = property?.type ?: computed!!.type
  val unit: PropertyUnit? get() = property?.unit
  val listItemType: PropertyType? get() = property?.listItemType
  val isComputed: Boolean get() = computed != null

  /** Key of the value in AppData.data (property id) or in the computed values (computed property id). */
  val valueKey: String get() = property?.id?.value ?: computed!!.id.value

  /** Path segments for the nested JSON view filter rules are evaluated on; a property whose own name contains a dot stays one segment. */
  val segments: List<String> get() = if (refProperty == null && objectPath.isEmpty()) listOf(path) else path.split('.')
}

internal sealed interface FieldResolution {
  data class Resolved(val field: ResolvedField) : FieldResolution
  data class Failed(val code: ReportDefinitionErrorCode, val args: List<String>) : FieldResolution
}

internal object ReportFields {

  private const val SEPARATOR = '.'

  /** Prefix of a reference to the App configuration (ADR 0027) in a value position, e.g. `config.zielKm`. */
  const val CONFIG_PREFIX = "config."

  /** Names of all unit granularities (e.g. `KILOMETERS`), as a calculated field's `unit` takes them. */
  val GRANULARITY_NAMES: List<String> = listOf(
    TimeGranularity.entries,
    DistanceGranularity.entries,
    CurrencyGranularity.entries,
    MassGranularity.entries,
    EnergyGranularity.entries,
    VolumeGranularity.entries,
  ).flatten().map { it.name }

  /** The configuration field path of a `config.<field>` value, null for any other value. */
  fun configPath(raw: String?): String? = raw?.trim()?.takeIf { it.startsWith(CONFIG_PREFIX) }?.removePrefix(CONFIG_PREFIX)?.takeIf { it.isNotBlank() }

  /** Converts a raw number stored in [from]'s storage granularity into [to]'s; unchanged unless both are units of the same family. */
  fun convertUnit(value: Double, from: PropertyUnit?, to: PropertyUnit?): Double {
    if (from == null || to == null || from.family != to.family) return value
    return value * from.storageGranularity.factorToSmallestUnit / to.storageGranularity.factorToSmallestUnit
  }

  /**
   * Resolves [path] on [entity]: `name`, `ref.name` (one `ref` hop at most), `object.nested[.deeper]` or `ref.object.nested`. A property whose
   * own name contains the separator wins over a hop.
   */
  fun resolve(entity: EntityDefinition, path: String, entities: List<EntityDefinition>): FieldResolution {
    val segments = path.split(SEPARATOR)
    if (segments.size > 1) (resolveOn(entity, path, path, null) as? FieldResolution.Resolved)?.let { return it }
    if (segments.size == 1) return resolveOn(entity, path, path, null)
    val first = entity.properties.find { it.name == segments[0] }
      ?: return FieldResolution.Failed(ReportDefinitionErrorCode.FIELD_NOT_FOUND, listOf(segments[0], entity.name))
    return when (first.type) {
      PropertyType.OBJECT -> resolveNested(entity, path, null, first, segments.drop(1))
      PropertyType.REF -> {
        val target = first.targetEntityId?.let { id -> entities.find { it.id == id } }
          ?: return FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
        val rest = segments.drop(1)
        if (rest.size == 1) return resolveOn(target, rest[0], path, first)
        val objectProperty = target.properties.find { it.name == rest[0] }
          ?: return FieldResolution.Failed(ReportDefinitionErrorCode.FIELD_NOT_FOUND, listOf(rest[0], target.name))
        if (objectProperty.type != PropertyType.OBJECT) return FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
        resolveNested(target, path, first, objectProperty, rest.drop(1))
      }
      else -> FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
    }
  }

  private fun resolveNested(owner: EntityDefinition, path: String, refProperty: Property?, objectProperty: Property, rest: List<String>): FieldResolution {
    val enclosing = mutableListOf(objectProperty)
    var current = objectProperty
    rest.forEachIndexed { index, name ->
      val nested = current.nestedProperties.find { it.name == name }
        ?: return FieldResolution.Failed(ReportDefinitionErrorCode.FIELD_NOT_FOUND, listOf(name, owner.name))
      if (index == rest.lastIndex) return FieldResolution.Resolved(ResolvedField(path, owner, refProperty, nested, null, enclosing.toList()))
      if (nested.type != PropertyType.OBJECT) return FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
      enclosing += nested
      current = nested
    }
    return FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
  }

  private fun resolveOn(entity: EntityDefinition, name: String, path: String, refProperty: Property?): FieldResolution {
    entity.properties.find { it.name == name }?.let { return FieldResolution.Resolved(ResolvedField(path, entity, refProperty, it, null)) }
    entity.computedProperties.find { it.name == name }?.let { return FieldResolution.Resolved(ResolvedField(path, entity, refProperty, null, it)) }
    return FieldResolution.Failed(ReportDefinitionErrorCode.FIELD_NOT_FOUND, listOf(name, entity.name))
  }

  /** The fields of a query without an explicit field list: all top-level properties (except OBJECT) and computed properties. */
  fun defaultFields(entity: EntityDefinition): List<String> =
    entity.properties.filter { it.type != PropertyType.OBJECT }.map { it.name } + entity.computedProperties.map { it.name }

  fun isTimeType(type: PropertyType): Boolean = type == PropertyType.DATE || type == PropertyType.DATETIME

  fun isNumericType(type: PropertyType): Boolean = type == PropertyType.LONG || type == PropertyType.DOUBLE
}
