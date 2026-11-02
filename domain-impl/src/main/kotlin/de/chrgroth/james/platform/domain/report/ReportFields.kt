package de.chrgroth.james.platform.domain.report

import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode

/**
 * A query field resolved against the version's entities: either a (computed) property of the queried entity, or - via one `ref` hop
 * ([refProperty], e.g. `schuhe.name`) - a (computed) property of the referenced entity ([owner]). Exactly one of [property]/[computed] is set.
 */
internal data class ResolvedField(
  val path: String,
  val owner: EntityDefinition,
  val refProperty: Property?,
  val property: Property?,
  val computed: ComputedProperty?,
) {
  val type: PropertyType get() = property?.type ?: computed!!.type
  val unit: PropertyUnit? get() = property?.unit
  val listItemType: PropertyType? get() = property?.listItemType
  val isComputed: Boolean get() = computed != null

  /** Key of the value in AppData.data (property id) or in the computed values (computed property id). */
  val valueKey: String get() = property?.id?.value ?: computed!!.id.value
}

internal sealed interface FieldResolution {
  data class Resolved(val field: ResolvedField) : FieldResolution
  data class Failed(val code: ReportDefinitionErrorCode, val args: List<String>) : FieldResolution
}

internal object ReportFields {

  private const val SEPARATOR = '.'

  /** Resolves [path] (`name` or `ref.name`, one hop at most) on [entity]. */
  fun resolve(entity: EntityDefinition, path: String, entities: List<EntityDefinition>): FieldResolution {
    val segments = path.split(SEPARATOR)
    // A property name containing the separator itself wins over a ref hop.
    if (segments.size > 1) (resolveOn(entity, path, path, null) as? FieldResolution.Resolved)?.let { return it }
    return when (segments.size) {
      1 -> resolveOn(entity, path, path, null)
      2 -> {
        val refProperty = entity.properties.find { it.name == segments[0] }
          ?: return FieldResolution.Failed(ReportDefinitionErrorCode.FIELD_NOT_FOUND, listOf(segments[0], entity.name))
        val target = refProperty.targetEntityId?.takeIf { refProperty.type == PropertyType.REF }?.let { id -> entities.find { it.id == id } }
          ?: return FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
        resolveOn(target, segments[1], path, refProperty)
      }
      else -> FieldResolution.Failed(ReportDefinitionErrorCode.REF_HOP_INVALID, listOf(path))
    }
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
}
