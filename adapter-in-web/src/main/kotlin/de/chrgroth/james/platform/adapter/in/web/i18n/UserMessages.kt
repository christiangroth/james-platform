package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle("user")
interface UserMessages {

  // shared across ui/user pages
  @Message
  fun userBreadcrumbDashboardLabel(): String

  // user dashboard
  @Message
  fun userMyAppsHeading(): String

  @Message
  fun userDeveloperDashboardAriaLabel(): String

  @Message
  fun userDevelopmentLinkLabel(): String

  @Message
  fun userOpenInstalledAppAriaLabel(name: String): String

  @Message
  fun userUpgradeToVersionButton(version: String): String

  // app store
  @Message
  fun userAppStoreTitle(): String

  @Message
  fun userNoAppsAvailableMessage(): String

  @Message
  fun userViewAppDetailsAriaLabel(name: String): String

  @Message
  fun userInstalledBadge(): String

  // app store detail
  @Message
  fun userAppInfoHeading(): String

  @Message
  fun userDeveloperLabelWithName(name: String): String

  @Message
  fun userInstallButton(): String

  @Message
  fun userInstalledVersionLabel(version: String): String

  @Message
  fun userUpToDateBadge(): String

  @Message
  fun userLatestVersionLabel(): String

  @Message
  fun userReleaseNotesLabel(): String

  @Message
  fun userEntitiesLabel(): String

  @Message
  fun userNoEntitiesMessage(): String

  @Message
  fun userReportsLabel(): String

  @Message
  fun userNoReportsMessage(): String

  @Message
  fun userVersionHistoryLabel(): String

  @Message
  fun userAppUpdateModalTitle(): String

  @Message
  fun userUpgradeButton(): String

  // app detail
  @Message
  fun userNoEntitiesDefinedMessage(): String

  @Message
  fun userNoDataYetMessage(): String

  @Message
  fun userAddMoreDataAriaLabel(): String

  @Message
  fun userPageLabel(): String

  @Message
  fun userPreviousLabel(): String

  @Message
  fun userNextLabel(): String

  // app data new / edit (shared form fields)
  @Message
  fun userNewEntityTitle(name: String): String

  @Message
  fun userNoPropertiesDefinedMessage(): String

  @Message
  fun userRequiredAriaLabel(): String

  @Message
  fun userRemoveValueAriaLabel(): String

  @Message
  fun userAddValueButton(): String

  @Message
  fun userDurationPlaceholder(): String

  @Message
  fun userDecreaseValueAriaLabel(): String

  @Message
  fun userIncreaseValueAriaLabel(): String

  // app data edit
  @Message
  fun userEditDataTitle(): String

  @Message
  fun userMetadataLabel(): String

  @Message
  fun userIdLabel(): String

  @Message
  fun userEntityTypeLabel(): String

  @Message
  fun userReferenceTextLabel(): String

  @Message
  fun userDisplayTextLabel(): String

  @Message
  fun userVersionLabel(): String

  @Message
  fun userCreatedLabel(): String

  @Message
  fun userLastModifiedLabel(): String

  @Message
  fun userComputedPropertiesLabel(): String

  @Message
  fun userDeleteDataModalTitle(): String

  @Message
  fun userDeleteDataConfirm(): String

  // UserAppStoreResource messages/errors
  @Message
  fun userAppInstalledMessage(): String

  @Message
  fun userAppUpgradedMessage(): String

  @Message
  fun userEntityTypeRequiredError(): String

  @Message
  fun userDataCreatedMessage(): String

  @Message
  fun userDataUpdatedMessage(): String

  @Message
  fun userDataDeletedWithReferencesMessage(count: Int): String

  @Message
  fun userDataDeletedMessage(): String

  @Message
  fun userAppNotFoundError(): String

  @Message
  fun userNoPublishedVersionError(): String

  @Message
  fun userAlreadyInstalledError(): String

  @Message
  fun userNotInstalledError(): String

  @Message
  fun userInstalledAppNotFoundError(): String

  @Message
  fun userAlreadyUpToDateError(): String

  @Message
  fun userEntityNotFoundError(): String

  @Message
  fun userConstraintViolationError(): String

  @Message
  fun userAppDataNotFoundError(): String

  @Message
  fun userReferencedByNonNullablePropertyError(): String

  // constraint violation messages
  @Message
  fun userUniqueKeyViolationError(): String

  @Message
  fun userMinValueViolationError(min: String): String

  @Message
  fun userMaxValueViolationError(max: String): String

  @Message
  fun userMinLengthViolationError(min: Int): String

  @Message
  fun userMaxLengthViolationError(max: Int): String

  @Message
  fun userPatternViolationError(regex: String): String

  @Message
  fun userMinSizeViolationError(min: Int): String

  @Message
  fun userMaxSizeViolationError(max: Int): String

  @Message
  fun userInvalidReferenceViolationError(): String

  @Message
  fun userMinDateViolationError(min: String): String

  @Message
  fun userMaxDateViolationError(max: String): String

  @Message
  fun userMinTimeViolationError(min: String): String

  @Message
  fun userMaxTimeViolationError(max: String): String

  @Message
  fun userMinDatetimeViolationError(min: String): String

  @Message
  fun userMaxDatetimeViolationError(max: String): String

  @Message
  fun userMinDurationViolationError(min: String): String

  @Message
  fun userMaxDurationViolationError(max: String): String

  @Message
  fun userStepViolationError(step: String): String

  @Message
  fun userInvalidDurationFormatViolationError(format: String): String
}
