package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of the expression language's validation errors (types of operands, arguments and results) and the type names they mention, in their
 * own bundle because the report bundles grow towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("reportExpression")
interface ReportExpressionMessages {

  // region validation errors

  @Message
  fun reportErrorExpressionOperandType(operator: String, type: String): String

  @Message
  fun reportErrorExpressionArgumentType(function: String, argument: String, type: String): String

  @Message
  fun reportErrorExpressionIncomparable(operator: String, left: String, right: String): String

  @Message
  fun reportErrorExpressionMixedTypes(function: String, types: String): String

  @Message
  fun reportErrorExpressionResultType(expected: String, actual: String): String

  // endregion

  // region type names

  @Message
  fun expressionTypeNumber(): String

  @Message
  fun expressionTypeInteger(): String

  @Message
  fun expressionTypeText(): String

  @Message
  fun expressionTypeBoolean(): String

  @Message
  fun expressionTypeDate(): String

  @Message
  fun expressionTypeDateTime(): String

  @Message
  fun expressionTypeTime(): String

  // endregion
}
