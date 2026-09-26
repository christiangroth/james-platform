package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Labels for the grouped/time-bucketed aggregation tables on `app-entity-detail.html` (issue #713), split out of
 * [UserMessages] into their own bundle - Quarkus Qute's generated message bundle resolver hits a hard
 * bytecode-verification wall somewhere around 300 `@Message` methods on a single interface, and [UserMessages]
 * (covering every user-facing page) was already close to that limit before these labels were added - see
 * [UserImportFilterMessages]'s KDoc for the same issue.
 */
@MessageBundle("userAggregation")
interface UserAggregationMessages {

  @Message
  fun userAggregationTablePeriodHeading(): String

  @Message
  fun userAggregationTableGroupHeading(): String

  @Message
  fun userAggregationTableValueHeading(): String

  @Message
  fun userAggregationTableNoGroupLabel(): String

  @Message
  fun userAggregationTableNoPeriodLabel(): String

  @Message
  fun userAggregationTableTruncatedHint(visibleCount: Int, totalCount: Int): String
}
