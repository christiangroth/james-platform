package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Texts of the App configuration (ADR 0027): the developer's "Konfiguration" toggle and the user's "Einstellungen", in their own bundle because
 * [DeveloperMessages] and [UserMessages] are close to the ~300 method limit of Quarkus Qute's generated message bundle resolver.
 */
@MessageBundle("appConfig")
@Suppress("TooManyFunctions")
interface AppConfigMessages {

  // region developer

  @Message
  fun appConfigEntityToggleLabel(): String

  @Message
  fun appConfigEntityToggleHelp(): String

  @Message
  fun appConfigEntityBadge(): String

  @Message
  fun appConfigEntitySavedMessage(): String

  @Message
  fun appConfigAlreadyDefinedError(): String

  @Message
  fun appConfigReferencedError(): String

  @Message
  fun appConfigInvalidError(names: String): String

  // endregion

  // region user

  @Message
  fun appConfigSettingsButtonLabel(): String

  @Message
  fun appConfigSettingsHeading(): String

  @Message
  fun appConfigNotDefinedError(): String

  @Message
  fun appConfigAlreadyExistsError(): String

  @Message
  fun appConfigNotDeletableError(): String

  @Message
  fun appConfigSavedMessage(): String

  // endregion
}
