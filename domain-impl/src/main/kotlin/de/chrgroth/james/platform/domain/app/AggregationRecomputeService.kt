package de.chrgroth.james.platform.domain.app

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.AggregationError
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.readmodel.computeAggregationValues
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.`in`.app.AggregationPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.readmodel.AggregationRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant

/**
 * Outbox-driven, full-scan recomputation of a single `AggregationDefinition`'s values (see
 * docs/adr/0020-aggregation-definitions.md) - the O(n) counterpart to `AggregationInlineUpdateService`'s O(1)
 * inline updates, run on [de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition.AggregationRecompute].
 */
@ApplicationScoped
@Suppress("Unused")
class AggregationRecomputeService(
  private val installedAppRepository: InstalledAppRepositoryPort,
  private val appVersionRepository: AppVersionRepositoryPort,
  private val appDataRepository: AppDataRepositoryPort,
  private val aggregationRepository: AggregationRepositoryPort,
) : AggregationPort {

  override fun handle(event: DomainOutboxEvent.RecomputeAggregation): Either<DomainError, Unit> {
    val installedAppId = InstalledAppId(event.installedAppId)
    val aggregationDefinitionId = AggregationDefinitionId(event.aggregationDefinitionId)
    val installedApp = installedAppRepository.findById(installedAppId) ?: run {
      logger.info { "Recompute aggregation handler: installation already gone, treating as already processed: installedAppId=${event.installedAppId}" }
      return Unit.right()
    }
    val appVersion = resolveInstalledAppVersion(installedApp, appVersionRepository) ?: run {
      logger.warn { "Recompute aggregation handler failed: app version not found for installedAppId=${event.installedAppId}" }
      return AggregationError.INSTALLED_APP_NOT_FOUND.left()
    }

    val owningEntity = appVersion.entityDefinitions.find { entity -> entity.aggregations.any { it.id == aggregationDefinitionId } }
    val aggregation = owningEntity?.aggregations?.find { it.id == aggregationDefinitionId }

    // Always clear existing values first: correct both for a full recompute (about to re-save fresh ones below) and
    // for an AggregationDefinition that was since removed/renamed (nothing to re-save, so this alone resolves it).
    aggregationRepository.deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId, aggregationDefinitionId)
    if (owningEntity == null || aggregation == null) {
      logger.info {
        "Recompute aggregation handler: aggregation definition no longer exists, cleared stale values: " +
          "installedAppId=${event.installedAppId} aggregationDefinitionId=${event.aggregationDefinitionId}"
      }
      return Unit.right()
    }

    val items = appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, owningEntity.id)
    val computed = aggregation.computeAggregationValues(installedAppId, items, Instant.now())
    computed.forEach { aggregationRepository.save(it) }
    logger.info {
      "Aggregation recomputed: installedAppId=${event.installedAppId} aggregationDefinitionId=${event.aggregationDefinitionId} " +
        "scanned=${items.size} groups=${computed.size}"
    }
    return Unit.right()
  }

  companion object : KLogging()
}
