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

  @Message
  fun userOpenEntityAriaLabel(name: String): String

  @Message
  fun userEntityDataCountLabel(count: Int): String

  @Message
  fun userDeleteInstalledAppModalTitle(): String

  @Message
  fun userDeleteInstalledAppConfirm(): String

  @Message
  fun userImportButtonLabel(): String

  // app imports
  @Message
  fun userImportsTitle(): String

  @Message
  fun userNoImportsMessage(): String

  @Message
  fun userNewImportButtonLabel(): String

  @Message
  fun userImportUrlLabel(): String

  @Message
  fun userImportBearerTokenLabel(): String

  @Message
  fun userImportSubmitButton(): String

  @Message
  fun userImportColCreatedAt(): String

  @Message
  fun userImportColLastActivity(): String

  @Message
  fun userImportColStatus(): String

  @Message
  fun userImportColActions(): String

  @Message
  fun userImportStatusDownloaded(): String

  @Message
  fun userImportStatusDataIdentified(): String

  @Message
  fun userImportStatusReady(): String

  @Message
  fun userImportMappingLinkLabel(): String

  @Message
  fun userImportDataPathLabel(): String

  @Message
  fun userImportDataPathSizeLabel(count: Int): String

  @Message
  fun userImportDataPathSelectButton(): String

  @Message
  fun userImportDataPathManualLabel(): String

  @Message
  fun userImportDataPathManualHint(): String

  @Message
  fun userImportSelectedDataPathLabel(): String

  @Message
  fun userDeleteImportModalTitle(): String

  @Message
  fun userDeleteImportConfirm(): String

  @Message
  fun userImportCreatedMessage(): String

  @Message
  fun userImportDeletedMessage(): String

  @Message
  fun userImportDataPathSelectedMessage(): String

  @Message
  fun userImportUrlRequiredError(): String

  @Message
  fun userImportTokenRequiredError(): String

  @Message
  fun userImportInvalidUrlError(): String

  @Message
  fun userImportFetchFailedError(): String

  @Message
  fun userImportInvalidJsonError(): String

  @Message
  fun userImportNotJsonObjectError(): String

  @Message
  fun userImportResponseTooLargeError(): String

  @Message
  fun userImportDocumentNotFoundError(): String

  @Message
  fun userImportDocumentNotDownloadedError(): String

  @Message
  fun userImportBlankDataPathError(): String

  @Message
  fun userImportInvalidDataPathError(): String

  @Message
  fun userImportDocumentNotMappableError(): String

  @Message
  fun userImportBlankMappingNameError(): String

  @Message
  fun userImportEntityDefinitionNotFoundError(): String

  @Message
  fun userImportMappingPropertyNotFoundError(): String

  @Message
  fun userImportDocumentNotReadyError(): String

  // import mapping page
  @Message
  fun userImportMappingTitle(): String

  @Message
  fun userImportMappingBackToImportsLabel(): String

  @Message
  fun userImportMappingSelectEntityLabel(): String

  @Message
  fun userImportMappingSelectEntityPlaceholder(): String

  @Message
  fun userImportMappingSelectEntityHint(): String

  @Message
  fun userImportMappingNameLabel(): String

  @Message
  fun userImportMappingTypeLabel(): String

  @Message
  fun userImportMappingTypeFindLabel(): String

  @Message
  fun userImportMappingTypeFindOrCreateLabel(): String

  @Message
  fun userImportMappingColProperty(): String

  @Message
  fun userImportMappingColSourceField(): String

  @Message
  fun userImportMappingColConversion(): String

  @Message
  fun userImportMappingColFallbackValue(): String

  @Message
  fun userImportMappingNoSourceFieldOption(): String

  @Message
  fun userImportMappingFallbackValuePlaceholder(): String

  @Message
  fun userImportMappingMandatoryBadge(): String

  @Message
  fun userImportMappingPatternBadge(): String

  @Message
  fun userImportMappingSaveButton(): String

  @Message
  fun userImportMappingStatusReadyMessage(): String

  @Message
  fun userImportMappingStatusIncompleteMessage(): String

  @Message
  fun userImportMappingIssueMissingMandatory(): String

  @Message
  fun userImportMappingIssueIncompatibleType(sourceType: String, targetType: String): String

  @Message
  fun userImportMappingIssueNumericRange(observedMin: String, observedMax: String): String

  @Message
  fun userImportMappingIssueStringLength(observedMin: Int, observedMax: Int): String

  @Message
  fun userImportMappingIssueNotStaticallyValidated(regex: String): String

  @Message
  fun userImportMappingConversionNone(): String

  @Message
  fun userImportMappingConversionStringToLong(): String

  @Message
  fun userImportMappingConversionStringToDouble(): String

  @Message
  fun userImportMappingConversionStringToBoolean(): String

  @Message
  fun userImportMappingConversionLongToDouble(): String

  @Message
  fun userImportMappingConversionLongToString(): String

  @Message
  fun userImportMappingConversionDoubleToString(): String

  @Message
  fun userImportMappingConversionBooleanToString(): String

  @Message
  fun userImportMappingConversionStringToDate(): String

  @Message
  fun userImportMappingConversionStringToDatetime(): String

  @Message
  fun userImportSchemaTypeString(): String

  @Message
  fun userImportSchemaTypeDate(): String

  @Message
  fun userImportSchemaTypeDatetime(): String

  @Message
  fun userImportSchemaTypeLong(): String

  @Message
  fun userImportSchemaTypeDouble(): String

  @Message
  fun userImportSchemaTypeBoolean(): String

  @Message
  fun userImportSchemaTypeObject(): String

  @Message
  fun userImportSchemaTypeArray(): String

  @Message
  fun userImportSchemaTypeNull(): String

  // import dry-run page
  @Message
  fun userImportDryRunTitle(): String

  @Message
  fun userImportDryRunLinkLabel(): String

  @Message
  fun userImportDryRunBackToMappingLabel(): String

  @Message
  fun userImportDryRunTotalLabel(): String

  @Message
  fun userImportDryRunValidLabel(): String

  @Message
  fun userImportDryRunInvalidLabel(): String

  @Message
  fun userImportDryRunAllValidMessage(): String

  @Message
  fun userImportDryRunInvalidObjectsHeading(): String

  @Message
  fun userImportDryRunObjectLabel(index: Int): String

  @Message
  fun userImportDryRunColSourceData(): String

  @Message
  fun userImportDryRunColTargetObject(): String

  @Message
  fun userImportDryRunStaticallyCheckedBadge(): String

  @Message
  fun userImportDryRunNewCheckBadge(): String

  @Message
  fun userImportDryRunAcceptButton(): String

  @Message
  fun userImportDryRunAcceptModalTitle(): String

  @Message
  fun userImportDryRunAcceptConfirm(validCount: Int, invalidCount: Int): String

  @Message
  fun userImportDryRunAcceptedMessage(savedCount: Int, discardedCount: Int): String

  // app data new / edit (shared form fields)
  @Message
  fun userNewEntityTitle(name: String): String

  @Message
  fun userNewEntityBreadcrumbLabel(): String

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

  @Message
  fun userObjectNoPropertiesDefinedMessage(): String

  @Message
  fun userObjectDescendNoPropertiesLabel(): String

  @Message
  fun userObjectDescendPropertyCountLabel(): String

  @Message
  fun userMultiModeButton(): String

  @Message
  fun userSnapshotCreateButton(): String

  @Message
  fun userSnapshotReplaceButton(): String

  @Message
  fun userSnapshotDeleteButton(): String

  @Message
  fun userSnapshotFieldHint(): String

  @Message
  fun userFocusModeButton(): String

  // app data edit
  @Message
  fun userEditDataTitle(): String

  @Message
  fun userMetadataLabel(): String

  @Message
  fun userEntityTypeLabel(): String

  @Message
  fun userReferenceTextLabel(): String

  @Message
  fun userDisplayTextLabel(): String

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
  fun userAppUninstalledMessage(): String

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

  // app data new / edit (constraint hints, shown under the input)
  @Message
  fun userHintMinLabel(): String

  @Message
  fun userHintMaxLabel(): String

  @Message
  fun userHintStepLabel(): String

  @Message
  fun userHintMinLengthLabel(): String

  @Message
  fun userHintMaxLengthLabel(): String

  @Message
  fun userHintPatternLabel(): String

  @Message
  fun userHintMinSizeLabel(): String

  @Message
  fun userHintMaxSizeLabel(): String

  @Message
  fun userHintMinDateLabel(): String

  @Message
  fun userHintMaxDateLabel(): String

  @Message
  fun userHintMinTimeLabel(): String

  @Message
  fun userHintMaxTimeLabel(): String

  @Message
  fun userHintMinDatetimeLabel(): String

  @Message
  fun userHintMaxDatetimeLabel(): String

  @Message
  fun userHintMinDurationLabel(): String

  @Message
  fun userHintMaxDurationLabel(): String

  @Message
  fun userHintUniqueKeyLabel(): String
}
