package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of reference lookup fields in the data create/edit forms (ADR 0026), split out of [UserMessages] into their own bundle - Quarkus Qute's
 * generated message bundle resolver fails bytecode verification at around 300 `@Message` methods on a single interface, see
 * [UserAggregationMessages].
 */
@MessageBundle("userReferenceLookup")
interface UserReferenceLookupMessages {

  @Message
  fun userReferenceLookupNotFoundViolationError(): String

  @Message
  fun userReferenceLookupNewHint(): String

  @Message
  fun userReferenceLookupUnknownHint(): String

  @Message
  fun userReferenceLookupNewFieldsTitle(): String
}
