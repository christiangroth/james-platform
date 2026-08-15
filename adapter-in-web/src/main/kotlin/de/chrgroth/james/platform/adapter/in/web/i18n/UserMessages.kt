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
  fun userImportPageHeading(connectionName: String, installedAppName: String, targetEntityName: String): String

  @Message
  fun userNoImportsMessage(): String

  @Message
  fun userNewImportButtonLabel(): String

  @Message
  fun userImportUrlLabel(): String

  @Message
  fun userImportUrlPostfixLabel(): String

  @Message
  fun userImportUrlPostfixHint(): String

  @Message
  fun userImportSourceUrlLabel(): String

  @Message
  fun userImportBearerTokenLabel(): String

  @Message
  fun userImportSubmitButton(): String

  @Message
  fun userImportConnectionLabel(): String

  @Message
  fun userImportNoConnectionsHint(): String

  @Message
  fun userImportManageConnectionsLinkLabel(): String

  @Message
  fun userImportColCreatedAt(): String

  @Message
  fun userImportColLastAction(): String

  @Message
  fun userImportColStatus(): String

  @Message
  fun userImportColTargetEntity(): String

  @Message
  fun userImportColTargetAppInstallation(): String

  @Message
  fun userImportColActions(): String

  @Message
  fun userImportTableColApp(): String

  @Message
  fun userImportTableColEntity(): String

  @Message
  fun userImportTableColCreated(): String

  @Message
  fun userImportTableColUpdated(): String

  @Message
  fun userImportStepDataPathLabel(): String

  @Message
  fun userImportStepsAriaLabel(): String

  @Message
  fun userImportStatusDownloaded(): String

  @Message
  fun userImportStatusDataIdentified(): String

  @Message
  fun userImportStatusReady(): String

  @Message
  fun userImportFilterLinkLabel(): String

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
  fun userImportDataPathStructureHeading(): String

  @Message
  fun userImportDataPathStructureSelectedBadge(): String

  @Message
  fun userImportDataPathSchemaColField(): String

  @Message
  fun userImportDataPathSchemaColType(): String

  @Message
  fun userImportDataPathSchemaColMandatory(): String

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
  fun userImportConnectionRequiredError(): String

  @Message
  fun userImportEntityRequiredError(): String

  @Message
  fun userImportConnectionNotFoundError(): String

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
  fun userImportJobNotFoundError(): String

  @Message
  fun userImportJobNotDownloadedError(): String

  @Message
  fun userImportBlankDataPathError(): String

  @Message
  fun userImportInvalidDataPathError(): String

  @Message
  fun userImportJobNotMappableError(): String

  @Message
  fun userImportEntityDefinitionNotFoundError(): String

  @Message
  fun userImportMappingPropertyNotFoundError(): String

  @Message
  fun userImportJobNotReadyError(): String

  @Message
  fun userImportJobNotFilterableError(): String

  // import filter page
  @Message
  fun userImportFilterTitle(): String

  @Message
  fun userImportFilterColMode(): String

  @Message
  fun userImportFilterColSourceField(): String

  @Message
  fun userImportFilterColOperator(): String

  @Message
  fun userImportFilterColValue(): String

  @Message
  fun userImportFilterValuePlaceholder(): String

  @Message
  fun userImportFilterAddRuleButton(): String

  @Message
  fun userImportFilterRemoveRuleButton(): String

  @Message
  fun userImportFilterSaveButton(): String

  @Message
  fun userImportFilterNoRulesHint(): String

  @Message
  fun userImportFilterPreviewLabel(matchingRecordCount: Int, totalRecordCount: Int): String

  @Message
  fun userImportFilterSavedMessage(matchingRecordCount: Int, totalRecordCount: Int): String

  @Message
  fun userImportFilterModeInclude(): String

  @Message
  fun userImportFilterModeExclude(): String

  @Message
  fun userImportFilterOperatorIsNull(): String

  @Message
  fun userImportFilterOperatorIsNotNull(): String

  @Message
  fun userImportFilterOperatorEquals(): String

  @Message
  fun userImportFilterOperatorNotEquals(): String

  @Message
  fun userImportFilterOperatorContains(): String

  // import connections page
  @Message
  fun userImportConnectionsNavLabel(): String

  @Message
  fun userImportConnectionsTitle(): String

  @Message
  fun userNoConnectionsMessage(): String

  @Message
  fun userNewConnectionButtonLabel(): String

  @Message
  fun userImportConnectionNameLabel(): String

  @Message
  fun userImportConnectionTokenHint(): String

  @Message
  fun userImportConnectionClearTokenLabel(): String

  @Message
  fun userImportConnectionColName(): String

  @Message
  fun userImportConnectionColUrl(): String

  @Message
  fun userImportConnectionColToken(): String

  @Message
  fun userImportConnectionColActions(): String

  @Message
  fun userImportConnectionTokenSetLabel(): String

  @Message
  fun userImportConnectionTokenNotSetLabel(): String

  @Message
  fun userImportConnectionTestButton(): String

  @Message
  fun userImportConnectionEditModalTitle(): String

  @Message
  fun userDeleteConnectionModalTitle(): String

  @Message
  fun userDeleteConnectionConfirm(): String

  @Message
  fun userImportConnectionNameRequiredError(): String

  @Message
  fun userImportConnectionCreatedMessage(): String

  @Message
  fun userImportConnectionUpdatedMessage(): String

  @Message
  fun userImportConnectionDeletedMessage(): String

  @Message
  fun userImportConnectionTestSucceededMessage(): String

  // import mapping page
  @Message
  fun userImportMappingTitle(): String

  @Message
  fun userImportMappingTargetEntityLabel(): String

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
  fun userImportMappingIssueReferenceLookupMissingCriteria(): String

  @Message
  fun userImportMappingIssueReferenceLookupInvalidCriterion(): String

  @Message
  fun userImportMappingIssueFallbackValueViolatesConstraint(violation: String): String

  @Message
  fun userImportMappingUseLookupLabel(): String

  @Message
  fun userImportMappingLookupHint(): String

  @Message
  fun userImportMappingAddCriterionButton(): String

  @Message
  fun userImportMappingRemoveCriterionButton(): String

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
  fun userImportMappingConversionDatetimeToDate(): String

  @Message
  fun userImportMappingConversionLongToDuration(): String

  @Message
  fun userImportMappingConversionUnitSeconds(): String

  @Message
  fun userImportMappingConversionUnitMinutes(): String

  @Message
  fun userImportMappingConversionUnitHours(): String

  @Message
  fun userImportMappingConversionUnitDays(): String

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
  fun userImportDryRunTotalLabel(): String

  @Message
  fun userImportDryRunValidLabel(): String

  @Message
  fun userImportDryRunInvalidLabel(): String

  @Message
  fun userImportDryRunSkippedLabel(): String

  @Message
  fun userImportDryRunAllValidMessage(): String

  @Message
  fun userImportDryRunNoObjectsMessage(): String

  @Message
  fun userImportDryRunDetailsHeading(): String

  @Message
  fun userImportDryRunSkippedReasonsColProperty(): String

  @Message
  fun userImportDryRunSkippedReasonsColReason(): String

  @Message
  fun userImportDryRunSkippedReasonsColCount(): String

  @Message
  fun userImportDryRunSkippedBadge(): String

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
  fun userImportDryRunAddButton(): String

  @Message
  fun userImportDryRunReplaceButton(): String

  @Message
  fun userImportDryRunAddModalTitle(): String

  @Message
  fun userImportDryRunReplaceModalTitle(): String

  @Message
  fun userImportDryRunAddConfirm(validCount: Int, skippedCount: Int, invalidCount: Int): String

  @Message
  fun userImportDryRunReplaceConfirm(targetEntityName: String, validCount: Int, skippedCount: Int, invalidCount: Int): String

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
