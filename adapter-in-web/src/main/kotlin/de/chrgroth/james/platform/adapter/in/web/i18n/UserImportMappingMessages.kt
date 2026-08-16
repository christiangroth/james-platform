package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Mapping-step live preview labels for `import-mapping.html`, split out of [UserMessages] into their own bundle for
 * the same reason as [UserImportFilterMessages] - [UserMessages] is close to the bytecode-verification wall Quarkus
 * Qute's generated message bundle resolver hits at around 300 `@Message` methods on a single interface.
 */
@MessageBundle("userImportMapping")
interface UserImportMappingMessages {

  @Message
  fun userImportMappingSampleTriggerLabel(): String

  /** [position]/[total] are substituted client-side after an AJAX fetch, not by Qute - see the script block in import-mapping.html for why this takes strings instead of ints. */
  @Message
  fun userImportMappingSamplePositionLabel(position: String, total: String): String

  @Message
  fun userImportMappingSampleEmptyMessage(): String
}
