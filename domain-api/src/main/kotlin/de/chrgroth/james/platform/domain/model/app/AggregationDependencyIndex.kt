package de.chrgroth.james.platform.domain.model.app

/** One [AggregationDefinition], identified together with the [EntityDefinition] it is declared on. */
data class AggregationDependency(
  val entityId: EntityDefinitionId,
  val aggregationId: AggregationDefinitionId,
)

/**
 * Derived from an [AppVersion]'s [EntityDefinition]s (see docs/adr/0020-aggregation-definitions.md), so a write can
 * be mapped to exactly the [AggregationDefinition]s it affects instead of recomputing every aggregation in the
 * `AppVersion` on every write.
 */
data class AggregationDependencyIndex(
  /** Aggregations declared directly on an Entity — affected by any write to that Entity's own instances. */
  val byOwningEntity: Map<EntityDefinitionId, List<AggregationDependency>>,
  /** Aggregations that group by a `Ref` to this Entity — affected by writes/deletes to that Entity's instances. */
  val byReferencedEntity: Map<EntityDefinitionId, List<AggregationDependency>>,
) {
  /** All aggregations affected by a write to instances of [entityId], whether owning or referenced. */
  fun affectedBy(entityId: EntityDefinitionId): List<AggregationDependency> =
    (byOwningEntity[entityId].orEmpty() + byReferencedEntity[entityId].orEmpty()).distinct()
}

fun buildAggregationDependencyIndex(entityDefinitions: List<EntityDefinition>): AggregationDependencyIndex {
  val byOwningEntity = mutableMapOf<EntityDefinitionId, MutableList<AggregationDependency>>()
  val byReferencedEntity = mutableMapOf<EntityDefinitionId, MutableList<AggregationDependency>>()
  entityDefinitions.forEach { entity ->
    entity.aggregations.forEach { aggregation ->
      val dependency = AggregationDependency(entity.id, aggregation.id)
      byOwningEntity.getOrPut(entity.id) { mutableListOf() }.add(dependency)
      val referencedEntityId = aggregation.refPath
        ?.let { refPropertyId -> entity.properties.find { it.id == refPropertyId } }
        ?.targetEntityId
      if (referencedEntityId != null) {
        byReferencedEntity.getOrPut(referencedEntityId) { mutableListOf() }.add(dependency)
      }
    }
  }
  return AggregationDependencyIndex(byOwningEntity, byReferencedEntity)
}
