package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of the declarative reports (report editor, validation errors, report page), in their own bundle because [DeveloperMessages] and
 * [UserMessages] are close to the bytecode-verification wall Quarkus Qute's generated message bundle resolver hits at around 300 methods.
 */
@MessageBundle("report")
@Suppress("TooManyFunctions")
interface ReportMessages {

  // region editor

  @Message
  fun reportDefinitionLabel(): String

  @Message
  fun reportDefinitionHelp(): String

  @Message
  fun reportPreviewHeading(): String

  @Message
  fun reportPreviewButton(): String

  @Message
  fun reportPreviewInstallationLabel(): String

  @Message
  fun reportPreviewNoInstallationHint(): String

  @Message
  fun reportPreviewEmpty(): String

  @Message
  fun reportValidationErrorsHeading(): String

  @Message
  fun reportDefinitionValid(): String

  @Message
  fun reportInvalidReportsError(names: String): String

  // endregion

  // region report page

  @Message
  fun reportsHeading(): String

  @Message
  fun reportOpenAriaLabel(name: String): String

  @Message
  fun reportNoContent(): String

  @Message
  fun reportNotRenderable(): String

  @Message
  fun reportTableEmpty(): String

  @Message
  fun reportTableTruncated(): String

  @Message
  fun reportLoadingFailed(): String

  // endregion

  // region validation errors

  @Message
  fun reportErrorLocation(line: String, path: String): String

  @Message
  fun reportErrorYamlSyntax(detail: String): String

  @Message
  fun reportErrorDuplicateKey(key: String): String

  @Message
  fun reportErrorWrongType(expected: String): String

  @Message
  fun reportErrorUnknownKey(key: String, allowed: String): String

  @Message
  fun reportErrorNotSupportedYet(key: String): String

  @Message
  fun reportErrorRequiredKeyMissing(key: String): String

  @Message
  fun reportErrorInvalidValue(value: String, allowed: String): String

  @Message
  fun reportErrorEntityNotFound(entity: String): String

  @Message
  fun reportErrorFieldNotFound(field: String, entity: String): String

  @Message
  fun reportErrorRefHopInvalid(field: String): String

  @Message
  fun reportErrorTimeFieldInvalid(field: String): String

  @Message
  fun reportErrorTimeFieldMissing(): String

  @Message
  fun reportErrorQueryNotFound(query: String): String

  @Message
  fun reportErrorColumnNotInQuery(field: String, query: String): String

  @Message
  fun reportErrorPanelTypeMissing(allowed: String): String

  @Message
  fun reportErrorPanelTypeAmbiguous(types: String): String

  @Message
  fun reportErrorFilterValueMissing(operator: String): String

  @Message
  fun reportErrorInvalidRegex(pattern: String): String

  @Message
  fun reportErrorInvalidDate(value: String): String

  @Message
  fun reportErrorInvalidRange(): String

  @Message
  fun reportErrorNumberOutOfRange(value: String, min: String, max: String): String

  // endregion
}
