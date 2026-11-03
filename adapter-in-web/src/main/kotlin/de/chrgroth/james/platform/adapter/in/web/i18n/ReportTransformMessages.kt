package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of report variables and transformations (validation errors, variable controls), in their own bundle because [ReportMessages] and the
 * other large bundles grow towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("reportTransform")
@Suppress("TooManyFunctions")
interface ReportTransformMessages {

  // region validation errors

  @Message
  fun reportErrorVariableNotFound(variable: String): String

  @Message
  fun reportErrorVariableTypeMismatch(variable: String, use: String): String

  @Message
  fun reportErrorVariableOptionsQueryInvalid(variable: String, query: String): String

  @Message
  fun reportErrorVariableNameInvalid(name: String): String

  @Message
  fun reportErrorTransformStepMissing(allowed: String): String

  @Message
  fun reportErrorTransformStepAmbiguous(steps: String): String

  @Message
  fun reportErrorExpressionInvalid(expression: String, position: String, detail: String): String

  @Message
  fun reportErrorExpressionUnknownFunction(function: String, allowed: String): String

  @Message
  fun reportErrorTransformNeedsBucket(step: String, query: String): String

  @Message
  fun reportErrorForecastNeedsPeriod(query: String): String

  @Message
  fun reportErrorResampleNeedsTime(query: String): String

  // endregion

  // region report page

  @Message
  fun reportVariablesAriaLabel(): String

  // endregion
}
