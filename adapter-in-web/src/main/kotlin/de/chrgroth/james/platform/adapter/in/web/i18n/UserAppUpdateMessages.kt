package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * App update status texts (dashboard, app page, app store page), in their own bundle because [UserMessages] is close to the
 * bytecode-verification wall Quarkus Qute's generated message bundle resolver hits at around 300 `@Message` methods.
 */
@MessageBundle("userAppUpdate")
interface UserAppUpdateMessages {

  @Message
  fun userAppUpdatePendingHint(version: String): String

  @Message
  fun userAppUpdateManualHint(version: String): String
}
