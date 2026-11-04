package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.buildAggregationDependencyIndex
import de.chrgroth.james.platform.domain.model.app.computedSourceIn
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueStatus
import de.chrgroth.james.platform.domain.model.readmodel.contributionOf
import de.chrgroth.james.platform.domain.model.readmodel.valueIdFor
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant

/**
 * Inline, O(1) delta updates to `AggregationValue`s, called synchronously by `AppDataService` in the same method
 * call as the `AppData` write it reacts to (see docs/adr/0020-aggregation-definitions.md - no CDI event, per ADR
 * 0013: every write, own or referenced, goes through `AppDataService`, and the dependency index from #640 yields a
 * fixed, small set of affected aggregations).
 *
 * SUM/COUNT/AVG (including time-bucketed) always stay O(1): a running delta on the affected group's value. MIN/MAX
 * is O(1) whenever the new/edited value does not evict the current extreme; when an edit or delete *might* evict it
 * (the changed item's old value equals the stored extreme - a duplicate could still be the true extreme, so this is
 * conservative "in doubt" per the issue), the value is marked STALE and a full recompute is enqueued on the
 * [de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition.AggregationRecompute] outbox partition instead.
 *
 * An aggregation over a computed property is always recomputed that way (its values are evaluated from the expression, which may read the
 * configuration or today), and so are all of them when the installation's configuration object changes.
 */
@ApplicationScoped
@Suppress("Unused")
class AggregationInlineUpdateService(
  private val aggregationRepository: AggregationRepositoryPort,
  private val outbox: OutboxPort,
) {

  fun onAppDataCreated(installedAppId: InstalledAppId, entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition, appData: AppData) {
    recomputeComputedSources(installedAppId, entityDefinitions, entityDef)
    inlineAggregations(entityDefinitions, entityDef).forEach { applyCreate(installedAppId, it, appData) }
  }

  fun onAppDataUpdated(installedAppId: InstalledAppId, entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition, old: AppData, new: AppData) {
    recomputeComputedSources(installedAppId, entityDefinitions, entityDef)
    inlineAggregations(entityDefinitions, entityDef).forEach { aggregation ->
      applyDelete(installedAppId, aggregation, old)
      applyCreate(installedAppId, aggregation, new)
    }
  }

  fun onAppDataDeleted(installedAppId: InstalledAppId, entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition, appData: AppData) {
    recomputeComputedSources(installedAppId, entityDefinitions, entityDef)
    inlineAggregations(entityDefinitions, entityDef).forEach { applyDelete(installedAppId, it, appData) }
  }

  /** The affected aggregations updated by delta: those over a stored property. */
  private fun inlineAggregations(entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition): List<AggregationDefinition> =
    affectedAggregations(entityDefinitions, entityDef).filter { it.computedSourceIn(entityDef) == null }

  /**
   * Marks STALE and enqueues a full recompute for the aggregations over computed properties a write to [entityDef] affects: its own ones, or
   * all of the version's when [entityDef] is the configuration (computed properties may read `config.<field>`).
   */
  private fun recomputeComputedSources(installedAppId: InstalledAppId, entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition) {
    val owners = if (entityDef.configuration) entityDefinitions else listOf(entityDef)
    owners.flatMap { owner -> owner.aggregations.filter { it.computedSourceIn(owner) != null } }.forEach { markStaleAndEnqueueRecompute(installedAppId, it.id) }
  }

  /** The [AggregationDefinition]s owned by [entityDef], resolved via the #640 dependency index over [entityDefinitions]. */
  private fun affectedAggregations(entityDefinitions: List<EntityDefinition>, entityDef: EntityDefinition): List<AggregationDefinition> {
    val dependencyIndex = buildAggregationDependencyIndex(entityDefinitions)
    val affectedIds = dependencyIndex.byOwningEntity[entityDef.id].orEmpty().map { it.aggregationId }.toSet()
    return entityDef.aggregations.filter { it.id in affectedIds }
  }

  private fun applyCreate(installedAppId: InstalledAppId, aggregation: AggregationDefinition, item: AppData) {
    val contribution = aggregation.contributionOf(item) ?: return
    val id = aggregation.valueIdFor(installedAppId, item)
    val current = aggregationRepository.findByAggregationValueId(id)
    when (aggregation.function) {
      AggregationFunction.SUM, AggregationFunction.COUNT -> save(id, (current?.value ?: 0.0) + contribution, current?.sampleCount ?: 0)
      AggregationFunction.AVG -> {
        val newCount = (current?.sampleCount ?: 0) + 1
        val newSum = (current?.value ?: 0.0) * (current?.sampleCount ?: 0) + contribution
        save(id, newSum / newCount, newCount)
      }
      AggregationFunction.MIN -> if (current == null || contribution < current.value) save(id, contribution, 1)
      AggregationFunction.MAX -> if (current == null || contribution > current.value) save(id, contribution, 1)
    }
  }

  private fun applyDelete(installedAppId: InstalledAppId, aggregation: AggregationDefinition, item: AppData) {
    val contribution = aggregation.contributionOf(item) ?: return
    val id = aggregation.valueIdFor(installedAppId, item)
    val current = aggregationRepository.findByAggregationValueId(id) ?: return
    when (aggregation.function) {
      AggregationFunction.SUM, AggregationFunction.COUNT -> save(id, current.value - contribution, current.sampleCount)
      AggregationFunction.AVG -> {
        val newCount = current.sampleCount - 1
        val newValue = if (newCount <= 0) 0.0 else (current.value * current.sampleCount - contribution) / newCount
        save(id, newValue, newCount.coerceAtLeast(0))
      }
      AggregationFunction.MIN, AggregationFunction.MAX -> if (contribution == current.value) {
        markStaleAndEnqueueRecompute(installedAppId, aggregation.id)
      }
    }
  }

  private fun save(id: AggregationValueId, value: Double, sampleCount: Long) {
    aggregationRepository.save(AggregationValue(id = id, value = value, status = AggregationValueStatus.UP_TO_DATE, updatedAt = Instant.now(), sampleCount = sampleCount))
  }

  private fun markStaleAndEnqueueRecompute(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId) {
    aggregationRepository.markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)
    outbox.enqueue(DomainOutboxEvent.RecomputeAggregation(installedAppId = installedAppId.value, aggregationDefinitionId = aggregationDefinitionId.value))
    logger.info { "MIN/MAX possibly evicted, recompute enqueued: installedAppId=${installedAppId.value} aggregationDefinitionId=${aggregationDefinitionId.value}" }
  }

  companion object : KLogging()
}
