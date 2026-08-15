package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Filter mode/operator labels for `import-filter.html`, split out of [UserMessages] into their own bundle - Quarkus
 * Qute's generated message bundle resolver hits a hard bytecode-verification wall somewhere around 300 `@Message`
 * methods on a single interface, and [UserMessages] (covering every user-facing page) was already close to that
 * limit before these operators were added.
 */
@MessageBundle("userImportFilter")
interface UserImportFilterMessages {

  @Message
  fun userImportFilterModeInclude(): String

  @Message
  fun userImportFilterModeExclude(): String

  @Message
  fun userImportFilterOperatorIsNull(): String

  @Message
  fun userImportFilterOperatorIsNotNull(): String

  @Message
  fun userImportFilterOperatorEquals(): String

  @Message
  fun userImportFilterOperatorNotEquals(): String

  @Message
  fun userImportFilterOperatorContains(): String

  @Message
  fun userImportFilterOperatorMatches(): String

  @Message
  fun userImportFilterOperatorNotMatches(): String

  @Message
  fun userImportFilterOperatorGreaterThan(): String

  @Message
  fun userImportFilterOperatorGreaterThanOrEqual(): String

  @Message
  fun userImportFilterOperatorLessThan(): String

  @Message
  fun userImportFilterOperatorLessThanOrEqual(): String
}
