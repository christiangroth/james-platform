package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle
interface AppMessages {

  @Message
  fun loginHeroTagline(): String

  @Message
  fun loginHeroFeatureDataModels(): String

  @Message
  fun loginHeroFeatureInstallApps(): String

  @Message
  fun loginHeroFeatureDocsHealth(): String

  @Message
  fun loginTitle(): String

  @Message
  fun loginUsernameLabel(): String

  @Message
  fun loginPasswordLabel(): String

  @Message
  fun loginSubmitButton(): String

  @Message
  fun loginErrorInvalidCredentials(): String

  @Message
  fun loginErrorUnexpected(): String
}
