package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Connection test URL texts for `import-connections.html`, in their own bundle because [UserMessages] is close to the
 * bytecode-verification wall Quarkus Qute's generated message bundle resolver hits at around 300 `@Message` methods.
 */
@MessageBundle("userImportConnection")
interface UserImportConnectionMessages {

  @Message
  fun userImportConnectionTestUrlLabel(): String

  @Message
  fun userImportConnectionTestUrlHint(): String

  @Message
  fun userImportConnectionTestUrlForeignOriginError(): String
}
