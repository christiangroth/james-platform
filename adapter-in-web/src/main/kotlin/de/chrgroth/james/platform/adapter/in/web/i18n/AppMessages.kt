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

  // common
  @Message
  fun commonCancel(): String

  @Message
  fun commonSave(): String

  @Message
  fun commonDelete(): String

  @Message
  fun commonRemove(): String

  @Message
  fun commonAdd(): String

  @Message
  fun commonEdit(): String

  @Message
  fun commonClose(): String

  @Message
  fun commonName(): String

  @Message
  fun commonType(): String

  @Message
  fun commonSelectPlaceholder(): String

  @Message
  fun commonNotAvailable(): String

  @Message
  fun commonNetworkError(): String

  @Message
  fun commonUnexpectedError(): String

  @Message
  fun commonNameRequired(): String

  // property types (shared across type/list-item-type selects)
  @Message
  fun propertyTypeString(): String

  @Message
  fun propertyTypeLong(): String

  @Message
  fun propertyTypeDouble(): String

  @Message
  fun propertyTypeBoolean(): String

  @Message
  fun propertyTypeDate(): String

  @Message
  fun propertyTypeTime(): String

  @Message
  fun propertyTypeDatetime(): String

  @Message
  fun propertyTypeDuration(): String

  @Message
  fun propertyTypeReference(): String

  @Message
  fun propertyTypeList(): String

  @Message
  fun propertyTypeObject(): String

  // profile
  @Message
  fun profileHeading(): String

  @Message
  fun profileAccountInfoHeading(): String

  @Message
  fun profileCreatedLabel(): String

  @Message
  fun profileLastLoginLabel(): String

  @Message
  fun profileChangeUsernameHeading(): String

  @Message
  fun profileNewUsernameLabel(): String

  @Message
  fun profileChangePasswordHeading(): String

  @Message
  fun profileCurrentPasswordLabel(): String

  @Message
  fun profileNewPasswordLabel(): String

  @Message
  fun profileConfirmPasswordLabel(): String

  @Message
  fun profileUsernameChangedMessage(): String

  @Message
  fun profilePasswordChangedMessage(): String

  @Message
  fun profileOperationCompletedMessage(): String

  @Message
  fun profileUserNotFoundError(): String

  @Message
  fun profileUsernameExistsError(): String

  @Message
  fun profileInvalidCurrentPasswordError(): String

  @Message
  fun profileAllFieldsRequiredError(): String

  @Message
  fun profilePasswordsDoNotMatchError(): String
}
