package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of the heatmap and pie panels and the error for the removed `kotlin` transformation step, in their own bundle because [ReportMessages] and
 * the other large bundles grow towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("reportExtension")
interface ReportExtensionMessages {

  // region validation errors

  @Message
  fun reportErrorHeatmapNeedsDay(query: String): String

  @Message
  fun reportErrorPieCategoryMissing(query: String): String

  @Message
  fun reportErrorKotlinStepRemoved(): String

  // endregion
}
