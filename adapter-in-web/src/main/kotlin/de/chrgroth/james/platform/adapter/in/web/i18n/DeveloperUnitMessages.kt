package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts around the display of property and computed property units (the largest display granularity), in their own bundle because
 * [DeveloperMessages] grows towards the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("developerUnit")
interface DeveloperUnitMessages {

  @Message
  fun developerUnitMaxDisplayGranularityLabel(): String

  @Message
  fun developerUnitMaxDisplayGranularityAll(): String

  @Message
  fun developerUnitMaxDisplayGranularityHint(): String

  @Message
  fun developerUnitMaxDisplayGranularityInvalidError(): String
}
