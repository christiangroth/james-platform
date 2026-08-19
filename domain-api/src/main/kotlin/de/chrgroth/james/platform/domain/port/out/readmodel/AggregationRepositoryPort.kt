package de.chrgroth.james.platform.domain.port.out.readmodel

import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValue
import de.chrgroth.james.platform.domain.model.readmodel.AggregationValueId

/** See docs/adr/0013-precomputed-read-models-per-ui-page.md and docs/adr/0020-aggregation-definitions.md. */
interface AggregationRepositoryPort {
  /** Single-value lookup for O(1) inline updates (see docs/adr/0020-aggregation-definitions.md, 2/3 - AppDataService's inline hook). */
  fun findByAggregationValueId(id: AggregationValueId): AggregationValue?
  fun findAllByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId): List<AggregationValue>
  fun save(value: AggregationValue)
  fun markStaleByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId)
  fun deleteAllByInstalledAppId(installedAppId: InstalledAppId)
  fun deleteAllByInstalledAppIdAndAggregationDefinitionId(installedAppId: InstalledAppId, aggregationDefinitionId: AggregationDefinitionId)
}
