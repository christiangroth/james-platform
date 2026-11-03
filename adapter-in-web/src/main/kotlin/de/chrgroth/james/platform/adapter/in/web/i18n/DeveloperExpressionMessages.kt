package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts around the expressions of computed properties and smart defaults (ADR 0029): validation errors, units and the hint for Kotlin scripts
 * stored before, in their own bundle because [DeveloperMessages] grows towards the ~300 method limit of Quarkus Qute's generated message bundle
 * resolver.
 */
@MessageBundle("developerExpression")
interface DeveloperExpressionMessages {

  // region validation errors

  @Message
  fun developerExpressionSyntaxError(position: String, detail: String): String

  @Message
  fun developerExpressionUnknownFunctionError(function: String, functions: String): String

  @Message
  fun developerExpressionFunctionNotAvailableError(function: String, functions: String): String

  @Message
  fun developerExpressionFieldArgumentRequiredError(function: String): String

  @Message
  fun developerExpressionUnknownFieldError(field: String): String

  @Message
  fun developerExpressionUnknownConfigFieldError(field: String): String

  @Message
  fun developerExpressionResultTypeError(expected: String, actual: String): String

  // endregion

  // region editor

  @Message
  fun developerComputedPropertyUnitLabel(): String

  @Message
  fun developerComputedPropertyUnitNone(): String

  @Message
  fun developerLegacyScriptBadge(): String

  @Message
  fun developerLegacyScriptHint(): String

  // endregion
}
