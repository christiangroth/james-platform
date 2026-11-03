package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.adapter.`in`.web.i18n.DeveloperExpressionMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportExpressionMessages
import de.chrgroth.james.platform.domain.error.ExpressionErrorCode
import de.chrgroth.james.platform.domain.error.InvalidExpressionError
import de.chrgroth.james.platform.domain.model.app.PropertyType
import jakarta.enterprise.context.ApplicationScoped

/** German texts for invalid expressions of computed properties and smart defaults (ADR 0029). */
@ApplicationScoped
class ExpressionTexts(
  private val expressionMsg: DeveloperExpressionMessages,
  private val reportExpressionMsg: ReportExpressionMessages,
  private val reportTexts: ReportTexts,
) {

  fun message(error: InvalidExpressionError): String {
    val a = { index: Int -> error.args.getOrElse(index) { "" } }
    return when (error.reason) {
      ExpressionErrorCode.SYNTAX -> expressionMsg.developerExpressionSyntaxError(a(0), a(1))
      ExpressionErrorCode.UNKNOWN_FUNCTION -> expressionMsg.developerExpressionUnknownFunctionError(a(0), a(1))
      ExpressionErrorCode.FUNCTION_NOT_AVAILABLE -> expressionMsg.developerExpressionFunctionNotAvailableError(a(0), a(1))
      ExpressionErrorCode.FIELD_ARGUMENT_REQUIRED -> expressionMsg.developerExpressionFieldArgumentRequiredError(a(0))
      ExpressionErrorCode.UNKNOWN_FIELD -> expressionMsg.developerExpressionUnknownFieldError(a(0))
      ExpressionErrorCode.UNKNOWN_CONFIG_FIELD -> expressionMsg.developerExpressionUnknownConfigFieldError(a(0))
      ExpressionErrorCode.OPERAND_TYPE -> reportExpressionMsg.reportErrorExpressionOperandType(a(0), typeName(a(1)))
      ExpressionErrorCode.ARGUMENT_TYPE -> reportExpressionMsg.reportErrorExpressionArgumentType(a(0), a(1), typeName(a(2)))
      ExpressionErrorCode.INCOMPARABLE -> reportExpressionMsg.reportErrorExpressionIncomparable(a(0), typeName(a(1)), typeName(a(2)))
      ExpressionErrorCode.MIXED_TYPES -> reportExpressionMsg.reportErrorExpressionMixedTypes(a(0), a(1).split(",").joinToString(", ") { typeName(it.trim()) })
      ExpressionErrorCode.RESULT_TYPE -> expressionMsg.developerExpressionResultTypeError(typeName(a(0)), typeName(a(1)))
    }
  }

  private fun typeName(type: String): String =
    if (type == PropertyType.REF.name) PropertyLabelTemplateExtensions.propertyTypeLabel(PropertyType.REF) else reportTexts.typeName(type)

  companion object {
    /** English, machine-oriented detail line for the token API, e.g. `UNKNOWN_FIELD [gewicht]`. */
    fun detail(error: InvalidExpressionError): String = if (error.args.isEmpty()) error.reason.name else "${error.reason.name} ${error.args}"
  }
}
