package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Generic data validation messages, split out of [UserMessages] into their own bundle - Quarkus Qute's generated message bundle resolver
 * fails bytecode verification at around 300 `@Message` methods on a single interface, see [UserAggregationMessages].
 */
@MessageBundle("userValidation")
interface UserValidationMessages {

  @Message
  fun userRequiredValueViolationError(): String
}
