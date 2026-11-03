package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of the advanced report transformations (delta, as-of, join, top-N, goals: validation errors, stat goal), in their own bundle because
 * [ReportMessages] and the other large bundles grow towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("reportAdvanced")
interface ReportAdvancedMessages {

  // region validation errors

  @Message
  fun reportErrorTransformNeedsTime(step: String, query: String): String

  @Message
  fun reportErrorTransformQueryCycle(query: String): String

  @Message
  fun reportErrorGoalTargetInvalid(value: String): String

  // endregion

  // region report page

  @Message
  fun reportStatGoal(): String

  // endregion
}
