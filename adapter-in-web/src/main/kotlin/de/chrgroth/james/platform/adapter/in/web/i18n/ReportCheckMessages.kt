package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of report checks and warnings panels (validation errors, panel and tab texts), in their own bundle because [ReportMessages] and the
 * other large bundles grow towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("reportCheck")
interface ReportCheckMessages {

  // region validation errors

  @Message
  fun reportErrorCheckTypeMissing(allowed: String): String

  @Message
  fun reportErrorCheckTypeAmbiguous(types: String): String

  @Message
  fun reportErrorCheckNotFound(check: String): String

  @Message
  fun reportErrorCalendarInvalid(calendar: String): String

  @Message
  fun reportErrorMessagePlaceholderUnknown(placeholder: String, allowed: String): String

  // endregion

  // region report page

  @Message
  fun reportWarningsEmpty(): String

  @Message
  fun reportWarningsTruncated(): String

  @Message
  fun reportWarningsCountLabel(): String

  // endregion
}
