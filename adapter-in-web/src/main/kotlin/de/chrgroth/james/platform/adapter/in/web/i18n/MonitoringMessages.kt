package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle("monitoring")
interface MonitoringMessages {

  // health page
  @Message
  fun monitoringHealthTitle(): String

  @Message
  fun monitoringCronjobsStateHeading(): String

  @Message
  fun monitoringCronjobsSectionLabel(): String

  @Message
  fun monitoringColJob(): String

  @Message
  fun monitoringColCron(): String

  @Message
  fun monitoringColNext(): String

  @Message
  fun monitoringNowLabel(): String

  @Message
  fun monitoringMongoDbHeading(): String

  @Message
  fun monitoringCollectionsSectionLabel(): String

  @Message
  fun monitoringColCollection(): String

  @Message
  fun monitoringColCount(): String

  @Message
  fun monitoringColSize(): String

  @Message
  fun monitoringTotalLabel(): String

  @Message
  fun monitoringQueriesSectionLabel(): String

  @Message
  fun monitoringColQuery(): String

  @Message
  fun monitoringColExecutions(): String

  @Message
  fun monitoringScriptingHeading(): String

  @Message
  fun monitoringScriptExecutionsSectionLabel(): String

  @Message
  fun monitoringNoScriptStatsMessage(): String

  @Message
  fun monitoringColEntity(): String

  @Message
  fun monitoringColProperty(): String

  @Message
  fun monitoringColErrors(): String

  @Message
  fun monitoringColTotalMs(): String

  // logs page
  @Message
  fun monitoringLogsTitle(): String

  @Message
  fun monitoringLogsSubtitle(): String

  @Message
  fun monitoringLogsChronologicalTab(): String

  @Message
  fun monitoringLogsGroupedTab(): String

  @Message
  fun monitoringLogsEmptyState(): String

  @Message
  fun monitoringLogGroupEntriesLabel(count: Int): String

  @Message
  fun monitoringColTimestamp(): String

  @Message
  fun monitoringColMessage(): String

  @Message
  fun monitoringColStacktrace(): String

  @Message
  fun monitoringShowLabel(): String

  @Message
  fun monitoringColLevel(): String

  @Message
  fun monitoringColClass(): String

  // mongodb viewer page
  @Message
  fun monitoringMongoViewerTitle(): String

  @Message
  fun monitoringMongoCollectionLabel(): String

  @Message
  fun monitoringMongoSelectCollectionOption(): String

  @Message
  fun monitoringMongoNoDocumentsForSchemaMessage(): String

  @Message
  fun monitoringMongoFiltersHeading(): String

  @Message
  fun monitoringMongoContainsBadge(): String

  @Message
  fun monitoringMongoContainsPlaceholder(): String

  @Message
  fun monitoringMongoIdBadge(): String

  @Message
  fun monitoringMongoEqualsPlaceholder(): String

  @Message
  fun monitoringMongoInPlaceholder(): String

  @Message
  fun monitoringMongoNotInPlaceholder(): String

  @Message
  fun monitoringMongoSortHeading(): String

  @Message
  fun monitoringMongoNoSortOption(): String

  @Message
  fun monitoringMongoSortAscLabel(): String

  @Message
  fun monitoringMongoSortDescLabel(): String

  @Message
  fun monitoringMongoApplyFiltersButton(): String

  @Message
  fun monitoringMongoResultsHeading(): String

  @Message
  fun monitoringMongoDocumentsTotalLabel(count: Int): String

  @Message
  fun monitoringMongoPerPageLabel(): String

  @Message
  fun monitoringMongoNoDocumentsMatchMessage(): String

  @Message
  fun monitoringMongoPageLabel(page: Int, totalPages: Int): String

  @Message
  fun monitoringMongoPrevButton(): String

  @Message
  fun monitoringMongoNextButton(): String

  // config page
  @Message
  fun monitoringConfigTitle(): String

  @Message
  fun monitoringConfigEnvironmentSectionLabel(): String

  @Message
  fun monitoringConfigSectionLabel(): String

  @Message
  fun monitoringColKey(): String

  @Message
  fun monitoringColValue(): String
}
